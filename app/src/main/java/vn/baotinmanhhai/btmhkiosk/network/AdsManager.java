package vn.baotinmanhhai.btmhkiosk.network;

import android.content.Context;
import android.util.Log;

import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import vn.baotinmanhhai.btmhkiosk.data.DataCache;
import vn.baotinmanhhai.btmhkiosk.data.Models;

/**
 * Quang cao man cho (spec II.2.2): tai TAT CA file ve bo nho may (khong phat truc tuyen), chi phat khi tep
 * da tai du va SHA-256 khop checksum; loi tai thi bo qua muc do, phat cac muc con lai va thu lai o luot lay
 * playlist ke. URL bat bien theo noi dung nen tep da co dung checksum thi khong tai lai. Mat mang van phat
 * cac muc da tai (playlist duoc luu cache).
 */
public class AdsManager {

    private static final String TAG = "AdsManager";
    private static final String PLAYLIST_KEY = "ads_playlist";

    /** Mot muc da san sang phat: metadata + tep local. */
    public static class PlayableAd {
        public final Models.AdDto ad;
        public final File file;

        PlayableAd(Models.AdDto ad, File file) {
            this.ad = ad;
            this.file = file;
        }
    }

    private final File dir;
    private final DataCache cache;
    private final OkHttpClient http;
    private final ErrorReporter reporter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AdsManager(Context context, DataCache cache, OkHttpClient http, ErrorReporter reporter) {
        this.dir = new File(context.getFilesDir(), "ads");
        if (!dir.exists() && !dir.mkdirs()) Log.w(TAG, "Khong tao duoc thu muc ads");
        this.cache = cache;
        this.http = http;
        this.reporter = reporter;
    }

    public interface Listener {
        /** complete=false: con muc tai loi -> ben goi KHONG duoc coi playlist la "da dong bo xong" (ETag) de luot sau tai lai. */
        void onReady(List<PlayableAd> ads, boolean complete);
    }

    /** Khoi dong: phat ngay cac muc da tai tu lan truoc (khong can mang). */
    public List<PlayableAd> cachedPlayable() {
        List<Models.AdDto> playlist = cache.get(PLAYLIST_KEY,
                TypeToken.getParameterized(List.class, Models.AdDto.class).getType());
        return playlist == null ? new ArrayList<>() : playableFrom(playlist);
    }

    /** Dong bo playlist moi: luu cache, tai muc thieu, don tep cu, roi bao danh sach phat duoc. */
    public void sync(List<Models.AdDto> playlist, Listener listener) {
        executor.execute(() -> {
            List<Models.AdDto> sorted = new ArrayList<>(playlist);
            sorted.sort((a, b) -> Integer.compare(a.order, b.order));
            cache.put(PLAYLIST_KEY, sorted);

            boolean complete = true;
            for (Models.AdDto ad : sorted) {
                if (!isDownloaded(ad)) {
                    download(ad);
                    if (!isDownloaded(ad)) complete = false; // tai loi -> luot lay playlist ke phai thu lai (II.2.2)
                }
            }
            cleanup(sorted);
            listener.onReady(playableFrom(sorted), complete);
        });
    }

    private List<PlayableAd> playableFrom(List<Models.AdDto> playlist) {
        List<PlayableAd> out = new ArrayList<>();
        for (Models.AdDto ad : playlist) {
            File f = fileFor(ad);
            if (f.exists() && marker(ad).exists()) out.add(new PlayableAd(ad, f));
        }
        return out;
    }

    // ==================== TAI / KIEM TRA ====================

    private boolean isDownloaded(Models.AdDto ad) {
        File f = fileFor(ad);
        return f.exists() && marker(ad).exists() && (ad.sizeBytes <= 0 || f.length() == ad.sizeBytes);
    }

    private void download(Models.AdDto ad) {
        File part = new File(dir, fileName(ad) + ".part");
        try {
            Request request = new Request.Builder().url(ad.url).get().build();
            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new java.io.IOException("HTTP " + response.code());
                }
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                long total = 0;
                try (InputStream in = response.body().byteStream(); OutputStream out = new FileOutputStream(part)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        sha.update(buf, 0, n);
                        total += n;
                    }
                }
                String actual = hex(sha.digest());
                String expected = ad.checksum == null ? "" : ad.checksum.trim().toLowerCase(Locale.ROOT);
                if (ad.sizeBytes > 0 && total != ad.sizeBytes) {
                    throw new java.io.IOException("Kich thuoc sai: " + total + " != " + ad.sizeBytes);
                }
                if (!expected.equals(actual)) {
                    fail(ad, "checksum mismatch", expected, actual);
                    part.delete();
                    return;
                }
                File target = fileFor(ad);
                target.delete();
                if (!part.renameTo(target)) throw new java.io.IOException("Khong doi ten duoc tep tai xong");
                if (!marker(ad).createNewFile()) Log.w(TAG, "Khong tao duoc marker cho " + ad.id);
            }
        } catch (Exception e) {
            part.delete();
            fail(ad, e.getMessage() != null ? e.getMessage() : e.toString(), null, null);
        }
    }

    private void fail(Models.AdDto ad, String message, String expected, String actual) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("adId", ad.id);
        ctx.put("url", ad.url);
        if (expected != null) ctx.put("expected", expected.length() > 8 ? expected.substring(0, 8) + "…" : expected);
        if (actual != null) ctx.put("actual", actual.length() > 8 ? actual.substring(0, 8) + "…" : actual);
        reporter.report(ErrorReporter.MEDIA_DOWNLOAD, message, ctx);
    }

    /** Xoa tep quang cao khong con trong playlist (giu dung dung luong may). */
    private void cleanup(List<Models.AdDto> playlist) {
        Set<String> keep = new HashSet<>();
        for (Models.AdDto ad : playlist) {
            keep.add(fileName(ad));
            keep.add(fileName(ad) + ".ok");
        }
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!keep.contains(f.getName())) f.delete();
        }
    }

    // ==================== TEN TEP ====================

    private File fileFor(Models.AdDto ad) {
        return new File(dir, fileName(ad));
    }

    private File marker(Models.AdDto ad) {
        return new File(dir, fileName(ad) + ".ok");
    }

    /** Dat ten theo id + 12 ky tu dau checksum: doi noi dung => doi ten => tai lai. */
    private String fileName(Models.AdDto ad) {
        String sum = ad.checksum == null ? "" : ad.checksum.toLowerCase(Locale.ROOT);
        String ext = ad.isVideo() ? ".mp4" : (ad.url != null && ad.url.toLowerCase(Locale.ROOT).contains(".png") ? ".png" : ".jpg");
        return ad.id + "_" + (sum.length() > 12 ? sum.substring(0, 12) : sum) + ext;
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
