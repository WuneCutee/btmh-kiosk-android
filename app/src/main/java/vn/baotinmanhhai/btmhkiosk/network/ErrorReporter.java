package vn.baotinmanhhai.btmhkiosk.network;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import vn.baotinmanhhai.btmhkiosk.data.DataCache;
import vn.baotinmanhhai.btmhkiosk.data.KioskClock;
import vn.baotinmanhhai.btmhkiosk.data.Models;

/**
 * Bao loi tu may (spec II.2.13): gom lai, gui toi da 1 request/phut, moi request toi da 50 loi. Moi loi co
 * id (UUID) GIU NGUYEN khi gui lai lo (server bo ban trung). Loi/5xx/429 -> giu hang doi va gui lai ca lo;
 * hang doi duoc luu xuong dia de khong mat khi App khoi dong lai. KHONG BAO GIO dua token/khoa vao message.
 */
public class ErrorReporter {

    private static final String TAG = "ErrorReporter";
    private static final String CACHE_KEY = "pending_errors";
    private static final int MAX_BATCH = 50;
    private static final int MAX_QUEUE = 300;
    private static final int MAX_MESSAGE = 500;
    private static final int MAX_CONTEXT_BYTES = 2048;

    public static final String MEDIA_DOWNLOAD = "MEDIA_DOWNLOAD";
    public static final String MEDIA_PLAYBACK = "MEDIA_PLAYBACK";
    public static final String SSE_CONNECT = "SSE_CONNECT";
    public static final String HTTP_5XX = "HTTP_5XX";
    public static final String NETWORK = "NETWORK";
    public static final String HARDWARE = "HARDWARE";
    public static final String OTHER = "OTHER";

    private final DataCache cache;
    private final Gson gson = new Gson();
    private final List<Models.ErrorItem> queue = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private volatile KioskApiService api;
    private int consecutive5xx = 0;
    private boolean reported5xxForStreak = false;
    private long blockedUntilMs = 0L;
    private boolean started = false;

    public ErrorReporter(DataCache cache) {
        this.cache = cache;
        List<Models.ErrorItem> saved = cache.get(CACHE_KEY, TypeToken.getParameterized(List.class, Models.ErrorItem.class).getType());
        if (saved != null) queue.addAll(saved);
    }

    public void attach(KioskApiService api) {
        this.api = api;
        synchronized (this) {
            if (started) return;
            started = true;
        }
        scheduler.scheduleWithFixedDelay(this::flush, 60, 60, TimeUnit.SECONDS);
    }

    /** Ghi nhan 1 loi; context tu dong cat cho vua 2048 byte UTF-8. */
    public void report(String type, String message, Map<String, Object> context) {
        Models.ErrorItem item = new Models.ErrorItem();
        item.id = UUID.randomUUID().toString();
        item.occurredAt = KioskClock.isoNow();
        item.type = type;
        item.message = truncate(message == null ? "" : message, MAX_MESSAGE);
        item.context = fitContext(context);
        synchronized (this) {
            queue.add(item);
            while (queue.size() > MAX_QUEUE) queue.remove(0);
            persist();
        }
        Log.w(TAG, type + ": " + item.message);
    }

    /** Goi sau MOI request API: 5xx lien tiep >= 3 lan -> bao 1 loi HTTP_5XX cho moi chuoi loi. */
    public void onHttpResult(String path, int status, String traceId) {
        boolean report = false;
        synchronized (this) {
            if (status >= 500) {
                consecutive5xx++;
                if (consecutive5xx >= 3 && !reported5xxForStreak) {
                    reported5xxForStreak = true;
                    report = true;
                }
            } else {
                consecutive5xx = 0;
                reported5xxForStreak = false;
            }
        }
        if (report) {
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("path", path);
            ctx.put("httpStatus", status);
            if (traceId != null) ctx.put("traceId", traceId);
            report(HTTP_5XX, "HTTP " + status + " lien tiep >= 3 lan", ctx);
        }
    }

    // ==================== GUI ====================

    private void flush() {
        KioskApiService service = api;
        if (service == null) return;
        if (System.currentTimeMillis() < blockedUntilMs) return;

        final List<Models.ErrorItem> batch;
        synchronized (this) {
            if (queue.isEmpty()) return;
            batch = new ArrayList<>(queue.subList(0, Math.min(MAX_BATCH, queue.size())));
        }
        Models.ErrorReportRequest request = new Models.ErrorReportRequest();
        request.errors = batch;
        service.postErrors(request, new KioskApiService.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                synchronized (ErrorReporter.this) {
                    queue.removeAll(batch);
                    persist();
                }
            }

            @Override
            public void onError(Exception error) {
                // Giu nguyen lo (cung id) de gui lai o luot sau; 429 thi cho du Retry-After.
                if (error instanceof Models.ApiException && ((Models.ApiException) error).httpStatus == 429) {
                    long wait = Math.max(60, ((Models.ApiException) error).retryAfterSeconds);
                    blockedUntilMs = System.currentTimeMillis() + wait * 1000;
                } else if (error instanceof Models.ApiException && ((Models.ApiException) error).httpStatus == 400) {
                    // Lo bi tu choi vi sai dinh dang (khong the gui lai duoc) -> bo de khong ket vinh vien.
                    synchronized (ErrorReporter.this) {
                        queue.removeAll(batch);
                        persist();
                    }
                }
            }
        });
    }

    private void persist() {
        cache.put(CACHE_KEY, new ArrayList<>(queue));
    }

    // ==================== TIEN ICH ====================

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Cat context cho JSON gon <= 2048 byte UTF-8: bo bot khoa tu cuoi, cat chuoi dai. */
    private Map<String, Object> fitContext(Map<String, Object> context) {
        if (context == null || context.isEmpty()) return null;
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : context.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String) v = truncate((String) v, 300);
            copy.put(e.getKey(), v);
        }
        while (!copy.isEmpty() && gson.toJson(copy).getBytes(StandardCharsets.UTF_8).length > MAX_CONTEXT_BYTES) {
            String lastKey = null;
            for (String k : copy.keySet()) lastKey = k;
            copy.remove(lastKey);
        }
        return copy.isEmpty() ? null : copy;
    }
}
