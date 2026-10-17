package com.baryobenta.community;

import android.content.Context;

import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Builds the single Retrofit instance. Every request automatically carries
 * the signed-in user's token so the API knows who is calling.
 *
 * >>> BASE_URL points at the deployed BaryoBenta API on Vercel.
 * >>> For local development, change it to your machine's address
 * >>> (e.g. http://10.0.2.2:8000/ for the emulator against `uvicorn`).
 */
public class RetrofitClient {

    // Deployed API (production configuration). Keep the trailing slash.
    // Local development: uvicorn api.main:app --reload  ->  http://<PC-IP>:8000/
    // (emulator on the same PC: http://10.0.2.2:8000/)
    public static final String BASE_URL = "https://baryo-benta.vercel.app/";

    private static ApiService api;

    public static synchronized ApiService getApi(Context context) {
        if (api == null) {
            final Context app = context.getApplicationContext();

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(new Interceptor() {
                        @Override
                        public okhttp3.Response intercept(Chain chain) throws java.io.IOException {
                            Request.Builder builder = chain.request().newBuilder();
                            String token = SessionManager.getToken(app);
                            if (token != null && !token.isEmpty()) {
                                builder.header("X-Auth-Token", token);
                            }
                            builder.header("Accept", "application/json");
                            return chain.proceed(builder.build());
                        }
                    })
                    .build();

            api = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService.class);
        }
        return api;
    }
}
