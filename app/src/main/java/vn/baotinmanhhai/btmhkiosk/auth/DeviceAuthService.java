package vn.baotinmanhhai.btmhkiosk.auth;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import vn.baotinmanhhai.btmhkiosk.data.KioskClock;
import vn.baotinmanhhai.btmhkiosk.data.KioskConfig;
import vn.baotinmanhhai.btmhkiosk.data.Models;

/**
 * Xac thuc thiet bi kiosk (spec II.2.1, II.2.14, II.2.15, II.5). Khoa RSA-2048 nam trong Android Keystore,
 * ky RSASSA-PSS-SHA256. CAC API NAY KHONG dung Bearer (xac thuc bang chu ky) nhung van gui du header
 * X-Device-Code / X-App-Version / X-Trace-Id. Moi phuong thuc *Blocking chay tren luong goi — cac ham
 * async ben duoi chay chung tren 1 luong rieng.
 */
public class DeviceAuthService {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    /** Chi 1 lan lay token/xoay khoa tai 1 thoi diem (tranh 2 luong cung ghi nonce/khoa). */
    private static final Object LOCK = new Object();

    private final OkHttpClient http;
    private final KioskConfig config;
    private final DeviceKeyStore keyStore;
    private final Gson gson = new Gson();
    private final SecureRandom random = new SecureRandom();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface Callback<T> {
        void onSuccess(T result);

        void onError(Exception error);
    }

    public DeviceAuthService(OkHttpClient http, KioskConfig config) {
        this.http = http;
        this.config = config;
        this.keyStore = new DeviceKeyStore(config.getAppContext());
    }

    /** serverTime - gio may luc enroll thanh cong (ms); VM dung de canh bao ky thuat vien khi lech > 5 phut. */
    private volatile long lastEnrollSkewMs = 0L;

    public long getLastEnrollSkewMs() {
        return lastEnrollSkewMs;
    }

    public boolean isEnrolled() {
        return !config.getDeviceCode().isEmpty() && keyStore.hasKey();
    }

    /** Co deviceCode nhung khong con khoa (xoa du lieu/hong Keystore) -> "Máy cần kích hoạt lại". */
    public boolean hasLostKey() {
        return !config.getDeviceCode().isEmpty() && !keyStore.hasKey();
    }

    // ==================== ASYNC ====================

    public void enroll(String deviceCode, String activationCode, Callback<Void> callback) {
        executor.execute(() -> {
            try {
                enrollBlocking(deviceCode.trim(), activationCode.trim());
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void getAccessToken(Callback<Models.DeviceSessionData> callback) {
        executor.execute(() -> {
            try {
                callback.onSuccess(fetchSession());
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void rotateKey(Callback<Void> callback) {
        executor.execute(() -> {
            try {
                rotateKeyBlocking();
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    // ==================== II.2.14 KICH HOAT ====================

    public void enrollBlocking(String deviceCode, String activationCode) throws Exception {
        synchronized (LOCK) {
            PublicKey publicKey;
            try {
                publicKey = keyStore.createActive(); // sinh khoa moi, ghi de khoa cu (neu con)
            } catch (GeneralSecurityException e) {
                throw new Models.KeyUnavailableException("Không sinh được khoá trên máy: " + e.getMessage());
            }
            String publicKeyB64 = DeviceKeyStore.exportPublicKeyBase64(publicKey);

            boolean clockRetried = false;
            boolean nonceRetried = false;
            int networkAttempts = 0;
            while (true) {
                try {
                    // Moi lan (ke ca goi lai) dung nonce + timestamp moi va ky lai — vẫn cung 1 khoa.
                    long timestamp = KioskClock.now();
                    String nonce = newNonce();
                    String base = deviceCode + "\n" + timestamp + "\n" + nonce + "\n" + activationCode + "\n" + publicKeyB64;

                    Models.EnrollRequest body = new Models.EnrollRequest();
                    body.deviceCode = deviceCode;
                    body.timestamp = timestamp;
                    body.nonce = nonce;
                    body.activationCode = activationCode;
                    body.publicKey = publicKeyB64;
                    body.signature = keyStore.sign(false, base);
                    body.appVersion = config.getAppVersion();
                    body.osVersion = config.getOsVersion();
                    body.deviceModel = config.getDeviceModel();

                    JsonObject data = post("/api/v1/kiosk/devices/enroll", gson.toJson(body), deviceCode);
                    if (data != null && data.has("serverTime")) {
                        long serverTime = data.get("serverTime").getAsLong();
                        KioskClock.sync(serverTime);
                        // Dong ho may lech so voi server: giu lai de VM canh bao ky thuat vien neu > 5 phut.
                        lastEnrollSkewMs = serverTime > 0 ? serverTime - System.currentTimeMillis() : 0L;
                    }
                    config.setDeviceCode(deviceCode); // ghi cau hinh roi moi bao thanh cong
                    return;
                } catch (IOException network) {
                    // Loi mang/het gio: GIU khoa da sinh va goi lai (server xu ly lap lai, II.2.14).
                    if (++networkAttempts >= 3) throw network;
                    Thread.sleep(2000L * networkAttempts);
                } catch (Models.ApiException e) {
                    String code = e.errorCode;
                    if ("102".equals(code) && !clockRetried) {
                        clockRetried = true;
                        if (e.serverTime > 0) KioskClock.sync(e.serverTime);
                    } else if ("102".equals(code)) {
                        keyStore.deleteAll();
                        throw new Models.ClockSkewException();
                    } else if ("103".equals(code) && !nonceRetried) {
                        nonceRetried = true;
                    } else if (isActivationRejection(code)) {
                        keyStore.deleteAll(); // ma sai/het han/het hieu luc/trang thai may: xoa khoa vua sinh
                        throw new Models.EnrollmentFailedException(code, e.getMessage());
                    } else if (e.isServerError() || "191".equals(code)) {
                        if (++networkAttempts >= 3) throw new Models.EnrollmentFailedException(code, e.getMessage());
                        Thread.sleep(2000L * networkAttempts);
                    } else {
                        throw new Models.EnrollmentFailedException(code, e.getMessage());
                    }
                }
            }
        }
    }

    private static boolean isActivationRejection(String code) {
        return "111".equals(code) || "112".equals(code) || "113".equals(code)
                || "122".equals(code) || "123".equals(code);
    }

    // ==================== II.2.1 LAY TOKEN ====================

    /** Lay accessToken. Neu dang xoay khoa thi ky bang khoa moi truoc; 104 -> quay ve khoa cu. */
    public Models.DeviceSessionData fetchSession() throws Exception {
        synchronized (LOCK) {
            if (hasLostKey()) {
                throw new Models.KeyUnavailableException("Máy cần kích hoạt lại, liên hệ quản trị viên");
            }
            if (!isEnrolled()) throw new Models.DeviceNotEnrolledException();

            if (keyStore.hasPendingKey()) {
                try {
                    Models.DeviceSessionData data = requestSession(true);
                    keyStore.promotePending(); // khoa moi duoc cong nhan -> dung tu day, xoa khoa cu
                    return data;
                } catch (Models.KeyNotRecognizedException e) {
                    keyStore.discardPending(); // xoay khoa dang do -> quay ve khoa cu
                } catch (Models.ApiException e) {
                    // Server chi co 1 khoa nen chu ky bang khoa moi CHUA duoc cong nhan co the tra 101 (khong chi 104).
                    // Khong quay ve khoa cu thi may ket vinh vien voi khoa cho — thu lai bang khoa dang dung.
                    if (!"101".equals(e.errorCode)) throw e;
                    keyStore.discardPending();
                }
            }
            return requestSession(false);
        }
    }

    private Models.DeviceSessionData requestSession(boolean pending) throws Exception {
        String deviceCode = config.getDeviceCode();
        boolean clockRetried = false;
        boolean nonceRetried = false;
        while (true) {
            try {
                long timestamp = KioskClock.now();
                String nonce = newNonce();
                Models.DeviceSessionRequest body = new Models.DeviceSessionRequest();
                body.deviceCode = deviceCode;
                body.timestamp = timestamp;
                body.nonce = nonce;
                body.signature = keyStore.sign(pending, deviceCode + "\n" + timestamp + "\n" + nonce);
                body.appVersion = config.getAppVersion();
                body.osVersion = config.getOsVersion();

                JsonObject data = post("/api/v1/kiosk/device-sessions", gson.toJson(body), deviceCode);
                Models.DeviceSessionData session = gson.fromJson(data, Models.DeviceSessionData.class);
                if (session == null || session.accessToken == null || session.accessToken.isEmpty()) {
                    throw new IOException("device-sessions tra response rong");
                }
                KioskClock.sync(session.serverTime);
                return session;
            } catch (GeneralSecurityException e) {
                throw new Models.KeyUnavailableException("Máy cần kích hoạt lại, liên hệ quản trị viên");
            } catch (Models.ApiException e) {
                String code = e.errorCode;
                if ("102".equals(code) && !clockRetried) {
                    clockRetried = true;
                    if (e.serverTime > 0) KioskClock.sync(e.serverTime); // bu lech roi ky lai dung 1 lan
                } else if ("102".equals(code)) {
                    throw new Models.ClockSkewException();
                } else if ("103".equals(code) && !nonceRetried) {
                    nonceRetried = true; // nonce da dung -> sinh nonce moi gui lai
                } else if ("104".equals(code)) {
                    throw new Models.KeyNotRecognizedException(); // KHONG tu xoa khoa
                } else if (e.httpStatus == 403 && "121".equals(code)) {
                    throw new Models.DeviceRevokedException();
                } else if (e.httpStatus == 403 && "201".equals(code)) {
                    throw new Models.ChannelPausedException();
                } else {
                    throw e;
                }
            }
        }
    }

    // ==================== II.2.15 XOAY KHOA ====================

    /**
     * Sinh khoa moi (giu khoa cu) -> POST rotate-key (2 chu ky) -> lay token bang khoa moi. Goi khi II.2.1
     * tra keyRotationRequired = true luc may ranh. Khoi dong lai giua chung: khoa moi dang cho duoc dung lai.
     */
    public void rotateKeyBlocking() throws Exception {
        synchronized (LOCK) {
            if (!isEnrolled()) return;
            String deviceCode = config.getDeviceCode();
            PublicKey newKey = keyStore.hasPendingKey() ? keyStore.publicKeyOf(true) : keyStore.createPending();
            String newKeyB64 = DeviceKeyStore.exportPublicKeyBase64(newKey);

            boolean clockRetried = false;
            boolean nonceRetried = false;
            while (true) {
                try {
                    long timestamp = KioskClock.now();
                    String nonce = newNonce();
                    String base = deviceCode + "\n" + timestamp + "\n" + nonce + "\n" + newKeyB64;
                    Models.RotateKeyRequest body = new Models.RotateKeyRequest();
                    body.deviceCode = deviceCode;
                    body.timestamp = timestamp;
                    body.nonce = nonce;
                    body.newPublicKey = newKeyB64;
                    body.signature = keyStore.sign(false, base);        // khoa CU chung minh may duoc tin
                    body.newKeySignature = keyStore.sign(true, base);   // khoa MOI chung minh giu khoa moi
                    post("/api/v1/kiosk/devices/rotate-key", gson.toJson(body), deviceCode);
                    break;
                } catch (Models.ApiException e) {
                    String code = e.errorCode;
                    if ("102".equals(code) && !clockRetried) {
                        clockRetried = true;
                        if (e.serverTime > 0) KioskClock.sync(e.serverTime);
                    } else if ("103".equals(code) && !nonceRetried) {
                        nonceRetried = true;
                    } else if ("104".equals(code)) {
                        // Co the server DA nhan khoa moi nhung phan hoi bi mat (mat khoa cu hieu luc). Thu lay token bang
                        // khoa moi: duoc thi coi nhu xoay khoa thanh cong, khong thi bo khoa moi va giu khoa cu.
                        try {
                            requestSession(true);
                            keyStore.promotePending();
                            return;
                        } catch (Models.KeyNotRecognizedException | Models.ApiException notAccepted) {
                            keyStore.discardPending();
                            throw e;
                        }
                    } else if ("131".equals(code)) {
                        keyStore.discardPending(); // giu khoa cu, xoa khoa moi vua sinh, thu lai sau
                        throw e;
                    } else if ("132".equals(code) || "124".equals(code)) {
                        throw e; // dang chong lan voi khoa khac / may khong ACTIVE: dung xoay
                    } else {
                        throw e;
                    }
                }
            }
            fetchSession(); // dung khoa moi lay token; thanh cong -> promotePending()
        }
    }

    // ==================== HTTP ====================

    /**
     * POST JSON khong Bearer. Tra ve "data" (co the null) khi code "000"; nem ApiException (kem serverTime
     * neu co trong data) khi loi. Mang loi -> IOException.
     */
    private JsonObject post(String path, String json, String deviceCode) throws IOException {
        Request request = new Request.Builder()
                .url(config.getBaseUrl() + path)
                .header("X-Trace-Id", UUID.randomUUID().toString())
                .header("X-Device-Code", deviceCode)
                .header("X-App-Version", config.getAppVersion())
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = http.newCall(request).execute()) {
            String bodyStr = response.body() != null ? response.body().string() : "";
            JsonObject root = null;
            try {
                JsonElement parsed = JsonParser.parseString(bodyStr);
                if (parsed.isJsonObject()) root = parsed.getAsJsonObject();
            } catch (Exception ignored) {
            }
            String code = root != null && root.has("code") && !root.get("code").isJsonNull()
                    ? root.get("code").getAsString() : null;
            JsonObject data = root != null && root.has("data") && root.get("data").isJsonObject()
                    ? root.getAsJsonObject("data") : null;

            if (!response.isSuccessful() || (code != null && !"000".equals(code))) {
                String message = root != null && root.has("message") && !root.get("message").isJsonNull()
                        ? root.get("message").getAsString() : null;
                long serverTime = data != null && data.has("serverTime") ? data.get("serverTime").getAsLong() : 0L;
                long retryAfter = parseRetryAfter(response.header("Retry-After"));
                throw new Models.ApiException(response.code(), code, message, retryAfter, serverTime);
            }
            return data;
        }
    }

    private static long parseRetryAfter(String header) {
        try {
            return header == null ? 0 : Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 16 ky tu hex lowercase (spec II.2.1) — 8 byte ngau nhien tu SecureRandom. */
    private String newNonce() {
        byte[] bytes = new byte[8];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(16);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** May bi thu hoi tren WebAdmin -> xoa cau hinh + khoa cuc bo, quay ve trang thai chua kich hoat. */
    public void clearEnrollment() {
        keyStore.deleteAll();
        config.clearDeviceCode();
    }

}
