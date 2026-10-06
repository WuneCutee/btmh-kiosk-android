package vn.baotinmanhhai.btmhkiosk.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

import vn.baotinmanhhai.btmhkiosk.BuildConfig;

/**
 * Cau hinh + trang thai kich hoat cua may, luu bang EncryptedSharedPreferences (bo nho rieng cua App,
 * khong sao luu — AndroidManifest allowBackup=false). baseUrl mac dinh theo loai build: debug tro
 * 10.0.2.2 (emulator), release tro HTTPS PROD; ky thuat vien co the doi o man Kich hoat.
 */
public class KioskConfig {

    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_DEVICE_CODE = "device_code";

    private final Context appContext;
    private final SharedPreferences prefs;

    public KioskConfig(Context context) {
        this.appContext = context.getApplicationContext();
        SharedPreferences p;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            p = EncryptedSharedPreferences.create(
                    context,
                    "btmh_kiosk_secure_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            Log.e("KioskConfig", "Khong tao duoc EncryptedSharedPreferences, dung prefs thuong", e);
            p = context.getSharedPreferences("btmh_kiosk_prefs_fallback", Context.MODE_PRIVATE);
        }
        this.prefs = p;
    }

    public Context getAppContext() {
        return appContext;
    }

    public String getBaseUrl() {
        String v = prefs.getString(KEY_BASE_URL, BuildConfig.DEFAULT_BASE_URL);
        if (v == null || v.trim().isEmpty()) return BuildConfig.DEFAULT_BASE_URL;
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }

    public void setBaseUrl(String value) {
        prefs.edit().putString(KEY_BASE_URL, value == null ? "" : value.trim()).commit();
    }

    public String getDeviceCode() {
        String v = prefs.getString(KEY_DEVICE_CODE, "");
        return v == null ? "" : v;
    }

    /** commit() (dong bo) — deviceCode phai xuong bo nho truoc khi bao kich hoat thanh cong. */
    public void setDeviceCode(String value) {
        prefs.edit().putString(KEY_DEVICE_CODE, value).commit();
    }

    /** Cot DB webview app_version/os_version chi VARCHAR(20) — luon tra chuoi da cat. */
    public String getAppVersion() {
        return cut(BuildConfig.VERSION_NAME, 20);
    }

    public String getOsVersion() {
        return cut(Build.VERSION.RELEASE, 20);
    }

    public String getDeviceModel() {
        return cut((Build.MANUFACTURER + " " + Build.MODEL).trim(), 60);
    }

    /** Xoa deviceCode (khong xoa khoa) khi may bi thu hoi. */
    public void clearDeviceCode() {
        prefs.edit().remove(KEY_DEVICE_CODE).commit();
    }

    private static String cut(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
