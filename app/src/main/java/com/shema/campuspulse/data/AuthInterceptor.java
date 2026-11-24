package com.shema.campuspulse.data;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {
    private final Context context;

    public AuthInterceptor(Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        String path = original.url().encodedPath();

        if (path.endsWith("auth/register/") || path.endsWith("auth/login/") || path.endsWith("auth/refresh/")) {
            return chain.proceed(original);
        }

        String token = TokenManager.getAccessToken(context);

        Request signedRequest = original;
        if (token != null && !token.isEmpty()) {
            signedRequest = original.newBuilder()
                    .header("Authorization", "Bearer " + token)
                    .method(original.method(), original.body())
                    .build();
        }

        Response response = chain.proceed(signedRequest);

        if (response.code() == 401 && !path.endsWith("auth/login/") && !path.endsWith("auth/refresh/")) {
            response.close();

            synchronized (AuthInterceptor.class) {
                String currentAccessToken = TokenManager.getAccessToken(context);
                if (currentAccessToken != null && !currentAccessToken.equals(token)) {
                    Request retryRequest = original.newBuilder()
                            .header("Authorization", "Bearer " + currentAccessToken)
                            .method(original.method(), original.body())
                            .build();
                    return chain.proceed(retryRequest);
                }

                String refreshToken = TokenManager.getRefreshToken(context);
                if (refreshToken != null && !refreshToken.isEmpty()) {
                    OkHttpClient tempClient = new OkHttpClient();
                    JsonObject jsonBody = new JsonObject();
                    jsonBody.addProperty("refresh", refreshToken);
                    RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));

                    String baseUrl = original.url().scheme() + "://" + original.url().host() + ":" + original.url().port() + "/api/";
                    Request refreshRequest = new Request.Builder()
                            .url(baseUrl + "auth/refresh/")
                            .post(body)
                            .build();

                    try (Response refreshResponse = tempClient.newCall(refreshRequest).execute()) {
                        if (refreshResponse.isSuccessful() && refreshResponse.body() != null) {
                            String respStr = refreshResponse.body().string();
                            JsonObject json = new JsonParser().parse(respStr).getAsJsonObject();
                            if (json.has("access")) {
                                String newAccess = json.get("access").getAsString();
                                String newRefresh = json.has("refresh") ? json.get("refresh").getAsString() : refreshToken;
                                TokenManager.saveTokens(context, newAccess, newRefresh);

                                Request retryRequest = original.newBuilder()
                                        .header("Authorization", "Bearer " + newAccess)
                                        .method(original.method(), original.body())
                                        .build();
                                return chain.proceed(retryRequest);
                            }
                        }
                    } catch (Exception ignored) {}
                }

                TokenManager.clearTokens(context);
                CampusRepository.get().clearUserState();
            }
        }

        return response;
    }
}
