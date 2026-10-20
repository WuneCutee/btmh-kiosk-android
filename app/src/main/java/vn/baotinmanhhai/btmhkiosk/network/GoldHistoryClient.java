package vn.baotinmanhhai.btmhkiosk.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Lay "bien dong gia vang theo ngay" tu website cong khai cua BTMH (baotinmanhhai.vn) — cung nguon voi muc
 * "Dien bien gia vang" o trang Bang gia vang cua website: GraphQL {@code goldChartData(code, max_days)} tra
 * {@code data_points[{date,buy,sell}]} (moi ngay mot dong, don vi VND/chi), {@code price_changes} (Hom nay / 30 ngay / ...)
 * va {@code product_options} (danh sach san pham chon duoc).
 *
 * <p>Day KHONG phai API Kiosk cua BTMH (khong nam trong tai lieu Phase 1): khong gui Bearer/X-Device-Code sang host
 * nay (dinh danh may chi gui cho backend cua minh), moi loi (mang, dinh dang doi) deu chi lam mat danh sach nay, khong
 * anh huong phan con lai cua App — noi goi giu du lieu cu trong cache khi that bai.
 */
public class GoldHistoryClient {

    public static final String ENDPOINT = "https://baotinmanhhai.vn/api/graphql";
    /** San pham mac dinh cua website (Kim Gia Bao 24K). */
    public static final String DEFAULT_CODE = "KGB";
    public static final int DEFAULT_DAYS = 30;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String QUERY = "query GetGoldChartData($code: String!, $max_days: Int) { "
            + "goldChartData(code: $code, max_days: $max_days) { "
            + "data_points { date buy sell } product_options { value label } "
            + "price_changes { label value percentage trend } default_product } }";

    // ---- Mo hinh du lieu (Gson) ----

    public static class Point {
        public String date;   // "dd/MM"
        public long buy;      // VND/chi
        public long sell;
    }

    public static class Option {
        public String value;  // ma san pham, vd KGB
        public String label;  // ten hien thi
    }

    public static class Change {
        public String label;       // "Hom nay", "30 ngay", ...
        public String value;       // "-70.000"
        public String percentage;  // "-0.48%"
        public String trend;       // "up" | "down" | ...
    }

    public static class History {
        public String code;
        @SerializedName("default_product") public String defaultProduct;
        @SerializedName("data_points") public List<Point> dataPoints = new ArrayList<>();
        @SerializedName("product_options") public List<Option> productOptions = new ArrayList<>();
        @SerializedName("price_changes") public List<Change> priceChanges = new ArrayList<>();
        public long fetchedAt;
    }

    /** Mot dong hien thi: ngay moi nhat truoc; {@code delta} = gia ban hom do tru gia ban ngay lien truoc (null neu la ngay dau). */
    public static class Row {
        public final String date;
        public final long buy;
        public final long sell;
        public final Long delta;

        Row(String date, long buy, long sell, Long delta) {
            this.date = date;
            this.buy = buy;
            this.sell = sell;
            this.delta = delta;
        }
    }

    private final OkHttpClient http;
    private final Gson gson = new Gson();

    public GoldHistoryClient() {
        http = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    /** Goi dong bo (chay tren luong nen). Nem {@link IOException} neu mang loi / phan hoi khong dung dinh dang. */
    public History fetch(String code, int maxDays) throws IOException {
        JsonObject vars = new JsonObject();
        vars.addProperty("code", code);
        vars.addProperty("max_days", maxDays);
        JsonObject body = new JsonObject();
        body.addProperty("query", QUERY);
        body.add("variables", vars);

        Request req = new Request.Builder()
                .url(ENDPOINT)
                .header("Accept", "application/json")
                .header("User-Agent", "BTMH-Kiosk")
                .post(RequestBody.create(gson.toJson(body), JSON))
                .build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IOException("HTTP " + resp.code());
            }
            JsonObject root = gson.fromJson(resp.body().charStream(), JsonObject.class);
            if (root == null || !root.has("data") || root.get("data").isJsonNull()
                    || !root.getAsJsonObject("data").has("goldChartData")
                    || root.getAsJsonObject("data").get("goldChartData").isJsonNull()) {
                throw new IOException("Phan hoi khong co goldChartData");
            }
            History h = gson.fromJson(root.getAsJsonObject("data").get("goldChartData"), History.class);
            if (h == null || h.dataPoints == null || h.dataPoints.isEmpty()) {
                throw new IOException("goldChartData rong");
            }
            h.code = code;
            h.fetchedAt = System.currentTimeMillis();
            return h;
        } catch (RuntimeException e) { // JsonSyntaxException... -> coi nhu loi dinh dang
            throw new IOException("Dinh dang phan hoi khong dung: " + e.getMessage(), e);
        }
    }

    /**
     * Website tra sell = 1 (khong phai gia that) cho san pham BTMH chi MUA VAO, khong ban ra - vd vang nguyen lieu
     * NL9999/NL999 (kiem that 16/10/2026). Coi gia ban <= 1 la "khong co gia ban".
     */
    public static boolean hasSellPrice(long sell) {
        return sell > 1;
    }

    /** San pham khong co gia ban o moi ngay (chi mua vao) - khi do tom tat "Hom nay/30 ngay..." cua website (tinh
     *  tren gia ban) luon bang 0, khong co y nghia. */
    public static boolean buyOnly(History h) {
        if (h == null || h.dataPoints == null || h.dataPoints.isEmpty()) return false;
        for (Point p : h.dataPoints) {
            if (hasSellPrice(p.sell)) return false;
        }
        return true;
    }

    /** Danh sach hien thi: moi nhat truoc, kem chenh lech gia ban so voi ngay lien truoc (khong co gia ban thi
     *  tinh tren gia mua vao). */
    public static List<Row> rows(History h) {
        List<Row> out = new ArrayList<>();
        if (h == null || h.dataPoints == null) return out;
        for (int i = 0; i < h.dataPoints.size(); i++) {
            Point p = h.dataPoints.get(i);
            Long delta = null;
            if (i > 0) {
                Point prev = h.dataPoints.get(i - 1);
                delta = hasSellPrice(p.sell) && hasSellPrice(prev.sell) ? p.sell - prev.sell : p.buy - prev.buy;
            }
            out.add(0, new Row(p.date, p.buy, p.sell, delta));
        }
        return out;
    }
}
