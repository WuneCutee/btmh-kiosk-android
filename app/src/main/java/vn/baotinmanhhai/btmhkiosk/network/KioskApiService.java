package vn.baotinmanhhai.btmhkiosk.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import vn.baotinmanhhai.btmhkiosk.data.KioskConfig;
import vn.baotinmanhhai.btmhkiosk.data.Models;

/**
 * Cac API du lieu (Bearer accessToken) — moi phuong thuc DONG BO (goi tu luong nen). Quy tac (II.2 / II.4):
 * - Moi request: X-Trace-Id (UUID moi), X-Device-Code, X-App-Version, Authorization.
 * - 401 -> lay token moi (II.2.1) roi gui lai DUNG MOT LAN.
 * - 403 code 121 -> DeviceRevokedException; 403 code 201 -> ChannelPausedException (giu du lieu cu).
 * - 429 -> ApiException kem Retry-After; 5xx -> ApiException (isServerError); mang loi -> IOException.
 * - ETag/If-None-Match (II.2.2, II.2.12): 304 -> Result.notModified.
 * Bearer duoc gan THU CONG o day, khong dung Interceptor toan cuc, de khong ro ri sang enroll/device-sessions.
 */
public class KioskApiService {

    /** Ket qua mot request co ETag: du lieu moi, hoac notModified (304) khi App giu ban cu. */
    public static class Result<T> {
        public final T data;
        public final String etag;
        public final boolean notModified;

        Result(T data, String etag, boolean notModified) {
            this.data = data;
            this.etag = etag;
            this.notModified = notModified;
        }
    }

    public interface Callback<T> {
        void onSuccess(T result);

        void onError(Exception error);
    }

    /** Lay token moi khi gap 401 (goi DeviceAuthService.fetchSession) — tra ve accessToken moi. */
    public interface TokenRefresher {
        String refresh() throws Exception;
    }

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final KioskConfig config;
    private final Gson gson = new Gson();
    private final ExecutorService asyncExecutor = Executors.newSingleThreadExecutor();

    private volatile String accessToken;
    private volatile TokenRefresher tokenRefresher;
    private volatile ErrorReporter reporter;

    public KioskApiService(OkHttpClient http, KioskConfig config) {
        this.http = http;
        this.config = config;
    }

    public void setToken(String token) {
        this.accessToken = token;
    }

    public void setTokenRefresher(TokenRefresher refresher) {
        this.tokenRefresher = refresher;
    }

    public void setErrorReporter(ErrorReporter reporter) {
        this.reporter = reporter;
    }

    // ==================== CAC API (DONG BO) ====================

    public List<Models.BannerDto> banners() throws Exception {
        List<Models.BannerDto> r = data("GET", "/api/v1/miniapp/banners", null, listOf(Models.BannerDto.class));
        return r != null ? r : new ArrayList<>();
    }

    public List<Models.CategoryDto> categories() throws Exception {
        List<Models.CategoryDto> r = data("GET", "/api/v1/miniapp/categories", null, listOf(Models.CategoryDto.class));
        return r != null ? r : new ArrayList<>();
    }

    public Models.ProductPageDto products(String categoryId, int page, int pageSize) throws Exception {
        String path = "/api/v1/miniapp/products?categoryId=" + enc(categoryId) + "&pageNumber=" + page + "&pageSize=" + pageSize;
        Models.ProductPageDto r = data("GET", path, null, Models.ProductPageDto.class);
        return r != null ? r : new Models.ProductPageDto();
    }

    public Models.ProductPageDto search(Models.SearchProductRequest request) throws Exception {
        Models.ProductPageDto r = data("POST", "/api/v1/miniapp/products/search", gson.toJson(request), Models.ProductPageDto.class);
        return r != null ? r : new Models.ProductPageDto();
    }

    /** II.2.8 — 404 code 202 nem ApiException (san pham khong ton tai hoac da tat cho kenh). */
    public Models.ProductDto productDetail(String productId) throws Exception {
        return data("GET", "/api/v1/miniapp/products/" + enc(productId), null, Models.ProductDto.class);
    }

    public Result<Models.BootstrapConfigDto> bootstrap(String etag) throws Exception {
        return result("/api/v1/kiosk/bootstrap", Models.BootstrapConfigDto.class, etag);
    }

    public Result<List<Models.AdDto>> adsPlaylist(String etag) throws Exception {
        return result("/api/v1/kiosk/ads/playlist", listOf(Models.AdDto.class), etag);
    }

    public List<Models.ProvinceDto> provinces() throws Exception {
        List<Models.ProvinceDto> r = data("GET", "/api/v1/miniapp/provinces/with-stores", null, listOf(Models.ProvinceDto.class));
        return r != null ? r : new ArrayList<>();
    }

    public List<Models.StoreDto> storesByProvince(long provinceId) throws Exception {
        List<Models.StoreDto> r = data("GET", "/api/v1/miniapp/stores/by-province?provinceId=" + provinceId, null,
                listOf(Models.StoreDto.class));
        return r != null ? r : new ArrayList<>();
    }

    /** II.2.11 — chap nhan ca dang boc BaseResponse (spec) lan tra thang {limitQuantity} (backend hien tai). */
    public Models.LimitDto limit() throws Exception {
        String body = send("GET", "/api/v1/miniapp/limits/active", null, null).body;
        JsonElement root = JsonParser.parseString(body);
        JsonObject obj = root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
        JsonObject payload = obj.has("data") && obj.get("data").isJsonObject() ? obj.getAsJsonObject("data") : obj;
        Models.LimitDto dto = gson.fromJson(payload, Models.LimitDto.class);
        return dto != null ? dto : new Models.LimitDto();
    }

    /** II.2.13 — gui lo loi (toi da 50); "000" = da ghi nhan. Goi tu ErrorReporter (async, khong chan). */
    public void postErrors(Models.ErrorReportRequest request, Callback<Void> cb) {
        asyncExecutor.execute(() -> {
            try {
                send("POST", "/api/v1/kiosk/errors", gson.toJson(request), null);
                cb.onSuccess(null);
            } catch (Exception e) {
                cb.onError(e);
            }
        });
    }

    // ==================== HA TANG ====================

    private static Type listOf(Class<?> element) {
        return TypeToken.getParameterized(List.class, element).getType();
    }

    private <T> T data(String method, String path, String body, Type dataType) throws Exception {
        return parseData(send(method, path, body, null).body, dataType);
    }

    private <T> Result<T> result(String path, Type dataType, String etag) throws Exception {
        Raw raw = send("GET", path, null, etag);
        if (raw.notModified) return new Result<>(null, raw.etag != null ? raw.etag : etag, true);
        return new Result<>(parseData(raw.body, dataType), raw.etag, false);
    }

    private <T> T parseData(String body, Type dataType) {
        Type envelope = TypeToken.getParameterized(Models.KioskResponse.class, dataType).getType();
        Models.KioskResponse<T> parsed = gson.fromJson(body, envelope);
        return parsed != null ? parsed.data : null;
    }

    private static class Raw {
        final String body;
        final String etag;
        final boolean notModified;

        Raw(String body, String etag, boolean notModified) {
            this.body = body;
            this.etag = etag;
            this.notModified = notModified;
        }
    }

    /** Gui request (co Bearer); 401 -> lam moi token + gui lai dung 1 lan. */
    private Raw send(String method, String path, String jsonBody, String etag) throws Exception {
        try {
            return sendOnce(method, path, jsonBody, etag, accessToken);
        } catch (Models.ApiException e) {
            if (e.httpStatus == 401 && tokenRefresher != null) {
                String fresh = tokenRefresher.refresh();
                accessToken = fresh;
                return sendOnce(method, path, jsonBody, etag, fresh);
            }
            throw e;
        }
    }

    private Raw sendOnce(String method, String path, String jsonBody, String etag, String token) throws IOException {
        final String traceId = UUID.randomUUID().toString();
        Request.Builder builder = new Request.Builder()
                .url(config.getBaseUrl() + path)
                .header("X-Trace-Id", traceId)
                .header("X-App-Version", config.getAppVersion());
        if (!config.getDeviceCode().isEmpty()) builder.header("X-Device-Code", config.getDeviceCode());
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (etag != null) builder.header("If-None-Match", etag);
        if ("POST".equals(method)) builder.post(RequestBody.create(jsonBody != null ? jsonBody : "{}", JSON));
        else builder.get();

        try (Response response = http.newCall(builder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            ErrorReporter r = reporter;
            if (r != null && !path.startsWith("/api/v1/kiosk/errors")) r.onHttpResult(path, response.code(), traceId);
            if (response.code() == 304) return new Raw(null, response.header("ETag"), true);

            String code = null;
            String message = null;
            try {
                JsonElement parsed = JsonParser.parseString(body);
                if (parsed.isJsonObject()) {
                    JsonObject o = parsed.getAsJsonObject();
                    if (o.has("code") && !o.get("code").isJsonNull()) code = o.get("code").getAsString();
                    if (o.has("message") && !o.get("message").isJsonNull()) message = o.get("message").getAsString();
                }
            } catch (Exception ignored) {
            }

            if (response.code() == 403 && "121".equals(code)) throw new Models.DeviceRevokedException();
            if (response.code() == 403 && "201".equals(code)) throw new Models.ChannelPausedException();
            if (!response.isSuccessful() || (code != null && !"000".equals(code))) {
                long retryAfter = 0;
                try {
                    String h = response.header("Retry-After");
                    if (h != null) retryAfter = Long.parseLong(h.trim());
                } catch (NumberFormatException ignored) {
                }
                throw new Models.ApiException(response.code(), code, message, retryAfter, 0L);
            }
            return new Raw(body, response.header("ETag"), false);
        }
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }
}
