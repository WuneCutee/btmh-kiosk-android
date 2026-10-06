package vn.baotinmanhhai.btmhkiosk.network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/** OkHttpClient dung chung cho DeviceAuthService + KioskApiService (request ngan han, KHONG dung cho SSE). */
public final class NetworkModule {

    private NetworkModule() {
    }

    private static volatile OkHttpClient sharedClient;

    public static OkHttpClient sharedClient() {
        OkHttpClient result = sharedClient;
        if (result == null) {
            synchronized (NetworkModule.class) {
                result = sharedClient;
                if (result == null) {
                    result = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .writeTimeout(20, TimeUnit.SECONDS)
                            .build();
                    sharedClient = result;
                }
            }
        }
        return result;
    }
}
