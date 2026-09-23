package com.example.velocitysuites.network;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {

    public static final String BASE_URL = "https://velocitysuites.com/api/";

    private static ApiService service;

    private ApiClient() {}

    public static synchronized ApiService getService(Context context) {
        if (service == null) {
            // Mirrors RoomRepository's own null-Context contract (its
            // constructor already tolerates a null Context for JVM unit
            // tests that never make a real HTTP call) - dereferencing
            // unconditionally here defeated that contract by crashing
            // before a test could even reach RoomRepository's pure,
            // non-networked logic (e.g. isAvailableForDates()).
            Context appContext = context != null ? context.getApplicationContext() : null;

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            // BODY only in debug builds - lets the actual HTTP status code and response
            // JSON that caused a parse failure show up in Logcat during development,
            // without ever exposing raw backend responses in a production build.
            logging.setLevel(com.example.velocitysuites.BuildConfig.DEBUG
                    ? HttpLoggingInterceptor.Level.BODY
                    : HttpLoggingInterceptor.Level.BASIC);

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    // OkHttp defaults writeTimeout to 10s, which is too short for the
                    // up-to-50MB GCash receipt uploads PaymentActivity allows
                    // (MAX_RECEIPT_SIZE_BYTES) - without this, a large receipt on a
                    // slow connection can fail with a spurious write timeout well
                    // before the connect/read timeouts above ever apply.
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(chain -> authHeaderInterceptor(chain, appContext))
                    .addInterceptor(logging)
                    .build();

            Gson gson = new GsonBuilder()
                    .registerTypeAdapterFactory(new NullSafePrimitiveTypeAdapterFactory())
                    .create();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();

            service = retrofit.create(ApiService.class);
        }
        return service;
    }

    private static Response authHeaderInterceptor(Interceptor.Chain chain, Context context) throws IOException {
        Request.Builder builder = chain.request().newBuilder()
                .addHeader("Accept", "application/json");

        String token = SessionManager.getToken(context);
        if (token != null) {
            builder.addHeader("Authorization", "Bearer " + token);
        }

        return chain.proceed(builder.build());
    }
}
