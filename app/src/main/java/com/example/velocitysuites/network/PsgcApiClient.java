package com.example.velocitysuites.network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Separate Retrofit client for the public PSGC API - no auth, different base URL than {@link ApiClient}. */
public final class PsgcApiClient {

    public static final String BASE_URL = "https://psgc.gitlab.io/api/";

    private static PsgcApiService service;

    private PsgcApiClient() {}

    public static synchronized PsgcApiService getService() {
        if (service == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            service = retrofit.create(PsgcApiService.class);
        }
        return service;
    }
}
