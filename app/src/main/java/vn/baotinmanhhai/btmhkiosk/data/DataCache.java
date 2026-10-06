package vn.baotinmanhhai.btmhkiosk.data;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;

/**
 * Cache du lieu cuoi cung nhan duoc (banner, danh muc, san pham, bang gia, cau hinh...) de khi mat mang
 * App van hien du lieu cu ("Loi mang: giu du lieu cu", spec II.2) va khoi dong nhanh hon. Ghi nguyen tu
 * (tep tam roi doi ten) de mat dien giua chung khong lam hong cache.
 */
public class DataCache {

    private static final String TAG = "DataCache";
    private final File dir;
    private final Gson gson = new Gson();

    public DataCache(Context context) {
        dir = new File(context.getFilesDir(), "cache");
        if (!dir.exists() && !dir.mkdirs()) Log.w(TAG, "Khong tao duoc thu muc cache");
    }

    public synchronized void put(String key, Object value) {
        if (value == null) return;
        File tmp = new File(dir, key + ".tmp");
        File target = new File(dir, key + ".json");
        try (Writer w = new FileWriter(tmp)) {
            gson.toJson(value, w);
        } catch (Exception e) {
            Log.w(TAG, "Ghi cache loi (" + key + "): " + e.getMessage());
            return;
        }
        if (!tmp.renameTo(target)) {
            target.delete();
            if (!tmp.renameTo(target)) Log.w(TAG, "Doi ten cache loi (" + key + ")");
        }
    }

    public synchronized <T> T get(String key, Type type) {
        File file = new File(dir, key + ".json");
        if (!file.exists()) return null;
        try (Reader r = new FileReader(file)) {
            return gson.fromJson(r, type);
        } catch (Exception e) {
            Log.w(TAG, "Doc cache loi (" + key + "): " + e.getMessage());
            return null;
        }
    }

    public synchronized void clear() {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
    }
}
