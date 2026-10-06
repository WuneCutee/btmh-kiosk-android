package vn.baotinmanhhai.btmhkiosk.network;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import vn.baotinmanhhai.btmhkiosk.data.KioskConfig;
import vn.baotinmanhhai.btmhkiosk.data.Models;

/**
 * Client SSE cho GET /api/v1/kiosk/prices/stream (spec II.3). Quy tac da ap dung:
 * - "snapshot"/"price" cung cau truc {version, items[]}; ViewModel xu ly version (snapshot luon ap dung,
 *   price co version <= hien tai thi bo qua).
 * - ping ": ping" 15s/lan; readTimeout 45s = watchdog (khong nhan dong nao kem ping -> mat ket noi).
 * - Ket noi lai: cho 3s, moi lan that bai lien tiep nhan doi, toi da 30s, cong jitter +-50%
 *   (mo SSE bi gioi han 6 lan/phut/may nen KHONG duoc thu lai day).
 * - "replaced": dung han, KHONG tu ket noi lai. "device-revoked": bao ViewModel roi dung han.
 * - Token lay qua Supplier moi lan ket noi (server chi kiem token luc mo ket noi).
 */
public class KioskPriceStreamClient {

    private static final String TAG = "KioskSSE";
    private static final long BACKOFF_MIN_MS = 3000;
    private static final long BACKOFF_MAX_MS = 30000;

    public interface Listener {
        /** true = da nhan du lieu tu server; false = mat ket noi / dang thu lai. */
        void onConnectionChanged(boolean connected);

        void onSnapshot(Models.SsePriceSnapshotDto snapshot);

        void onPrice(Models.SsePriceSnapshotDto update);

        void onConfigChanged();

        void onPlaylistChanged();

        void onDeviceRevoked();

        void onReplaced();

        /** Mo ket noi that bai lien tiep (>= 3 lan thi nen bao loi SSE_CONNECT, spec II.2.13). */
        default void onConnectFailure(int consecutive, String message) {
        }

        /** Mo ket noi bi 401 — ViewModel lay token moi, ket noi tiep theo se dung token do. */
        void onUnauthorized();
    }

    private final KioskConfig config;
    private final OkHttpClient sseHttp;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Response currentResponse;
    private volatile Listener listener;
    /** Retry-After (ms) cua lan mo SSE vua bi 429; runLoop dung thay cho backoff roi dat lai 0. */
    private volatile long retryAfterMs = 0L;

    public KioskPriceStreamClient(KioskConfig config) {
        this.config = config;
        this.sseHttp = new OkHttpClient.Builder()
                .readTimeout(45, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void start(Supplier<String> tokenSupplier) {
        if (running.getAndSet(true)) return;
        executor.execute(() -> runLoop(tokenSupplier));
    }

    public void stop() {
        running.set(false);
        Response resp = currentResponse;
        if (resp != null) resp.close();
    }

    private void runLoop(Supplier<String> tokenSupplier) {
        int attempt = 0; // so lan cho lien tiep chua nhan duoc du lieu: 3s, 6s, 12s... toi da 30s
        int failures = 0;
        while (running.get()) {
            boolean gotData = false;
            long startedAt = System.currentTimeMillis();
            try {
                gotData = connectOnce(tokenSupplier.get());
                Log.i(TAG, "SSE dong sau " + (System.currentTimeMillis() - startedAt) + "ms, gotData=" + gotData);
            } catch (Exception e) {
                Log.w(TAG, "Loi ket noi SSE sau " + (System.currentTimeMillis() - startedAt) + "ms: " + e);
                Listener l = listener;
                if (l != null) l.onConnectFailure(++failures, String.valueOf(e.getMessage()));
            }
            if (gotData) failures = 0;
            notifyConnection(false);
            if (!running.get()) return;

            // Lan cho dau tien la 3s (khong phai 6s): gotData -> ve moc 3s.
            if (gotData) attempt = 0;
            long backoff = Math.min(BACKOFF_MIN_MS << Math.min(attempt, 4), BACKOFF_MAX_MS);
            attempt++;
            long jitter = (long) (backoff * (0.5 + ThreadLocalRandom.current().nextDouble()));
            // 429 luc mo SSE: theo Retry-After cua server (mo SSE bi gioi han 6 lan/phut/may).
            long retryAfter = retryAfterMs;
            retryAfterMs = 0L;
            if (retryAfter > 0) jitter = Math.max(retryAfter, BACKOFF_MIN_MS);
            try {
                Thread.sleep(jitter);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** @return true neu nhan duoc it nhat 1 su kien (ket noi thuc su song) — de dat lai backoff. */
    private boolean connectOnce(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/api/v1/kiosk/prices/stream")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Device-Code", config.getDeviceCode())
                .header("X-App-Version", config.getAppVersion())
                .header("X-Trace-Id", UUID.randomUUID().toString())
                .header("Accept", "text/event-stream")
                .build();

        boolean gotData = false;
        try (Response response = sseHttp.newCall(request).execute()) {
            currentResponse = response;
            if (!response.isSuccessful()) {
                int status = response.code();
                if (status == 401 && listener != null) listener.onUnauthorized();
                if (status == 403) {
                    // 403 code 201 = kenh kiosk tam dung (KHONG phai thu hoi) -> khong xac nhan thu hoi (II.3.3/II.4).
                    if (!"201".equals(errorCode(response)) && listener != null) listener.onDeviceRevoked();
                }
                if (status == 429) {
                    try {
                        String h = response.header("Retry-After");
                        if (h != null) retryAfterMs = Long.parseLong(h.trim()) * 1000L;
                    } catch (NumberFormatException ignored) {
                    }
                }
                throw new IOException("SSE HTTP " + status);
            }
            ResponseBody body = response.body();
            if (body == null) return false;

            BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8));
            String currentEvent = null;
            StringBuilder data = new StringBuilder();
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (currentEvent != null && data.length() > 0) {
                        if (!gotData) {
                            gotData = true;
                            notifyConnection(true);
                        }
                        handleEvent(currentEvent, data.toString());
                    }
                    currentEvent = null;
                    data.setLength(0);
                } else if (line.startsWith("event:")) {
                    currentEvent = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    data.append(line.substring(5).trim());
                }
                // ": ping" / "retry:" / field la -> bo qua (nhung van reset dong ho cho 45s vi doc duoc dong).
            }
        } finally {
            currentResponse = null;
        }
        return gotData;
    }

    /** Ma loi trong than JSON cua phan hoi loi ({"code":"201",...}); null neu khong doc duoc. */
    private String errorCode(Response response) {
        try {
            ResponseBody b = response.body();
            if (b == null) return null;
            JsonElement parsed = JsonParser.parseString(b.string());
            if (parsed.isJsonObject() && parsed.getAsJsonObject().has("code")
                    && !parsed.getAsJsonObject().get("code").isJsonNull()) {
                return parsed.getAsJsonObject().get("code").getAsString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void notifyConnection(boolean connected) {
        Listener l = listener;
        if (l != null) l.onConnectionChanged(connected);
    }

    private void handleEvent(String name, String json) {
        Log.d(TAG, "su kien: " + name + " (" + json.length() + " byte)");
        Listener l = listener;
        if (l == null) return;
        try {
            switch (name) {
                case "snapshot":
                    l.onSnapshot(gson.fromJson(json, Models.SsePriceSnapshotDto.class));
                    break;
                case "price":
                    l.onPrice(gson.fromJson(json, Models.SsePriceSnapshotDto.class));
                    break;
                case "config-changed":
                    l.onConfigChanged();
                    break;
                case "playlist-changed":
                    l.onPlaylistChanged();
                    break;
                case "replaced":
                    running.set(false);
                    l.onReplaced();
                    break;
                case "device-revoked":
                    running.set(false);
                    l.onDeviceRevoked();
                    break;
                default:
                    break; // su kien la -> bo qua theo spec
            }
        } catch (Exception e) {
            Log.w(TAG, "Parse loi (" + name + "): " + e.getMessage());
        }
    }
}
