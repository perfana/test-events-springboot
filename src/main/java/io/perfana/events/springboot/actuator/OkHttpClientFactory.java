package io.perfana.events.springboot.actuator;

import com.squareup.okhttp.OkHttpClient;

import java.util.concurrent.TimeUnit;

public class OkHttpClientFactory {

    private OkHttpClientFactory() {}

    public static OkHttpClient instance() {
        OkHttpClient okHttpClient = new OkHttpClient();
        okHttpClient.setConnectTimeout(2000, TimeUnit.MILLISECONDS);
        okHttpClient.setReadTimeout(5000, TimeUnit.MILLISECONDS);
        okHttpClient.setWriteTimeout(5000, TimeUnit.MILLISECONDS);
        return okHttpClient;
    }
}
