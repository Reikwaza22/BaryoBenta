package com.baryobenta.community;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Runs a Retrofit call and hands back either the data or a readable error
 * message. Also signs the user out if the server says the session is invalid.
 */
public final class ApiHelper {

    public interface Result<T> {
        void onSuccess(T data, String message);

        void onError(String message);
    }

    private ApiHelper() {
    }

    public static <T> void call(final Context context, Call<ApiResponse<T>> call, final Result<T> result) {
        call.enqueue(new Callback<ApiResponse<T>>() {
            @Override
            public void onResponse(Call<ApiResponse<T>> c, Response<ApiResponse<T>> response) {
                if (isGone(context)) return;

                ApiResponse<T> body = response.body();
                if (body == null && response.errorBody() != null) {
                    try {
                        body = new Gson().fromJson(response.errorBody().string(), ApiResponse.class);
                    } catch (Exception ignored) {
                        body = null;
                    }
                }

                if (response.code() == 401 && SessionManager.isLoggedIn(context)) {
                    forceLogout(context);
                    return;
                }

                if (response.isSuccessful() && body != null && body.isSuccess()) {
                    result.onSuccess(body.getData(), body.getMessage());
                } else if (body != null && body.getMessage() != null) {
                    result.onError(body.getMessage());
                } else {
                    result.onError(context.getString(R.string.error_server, response.code()));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<T>> c, Throwable t) {
                if (isGone(context)) return;
                result.onError(context.getString(R.string.error_network));
            }
        });
    }

    private static boolean isGone(Context context) {
        return context instanceof Activity
                && (((Activity) context).isFinishing() || ((Activity) context).isDestroyed());
    }

    private static void forceLogout(Context context) {
        SessionManager.logout(context);
        Toast.makeText(context.getApplicationContext(), R.string.error_session_expired, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(context.getApplicationContext(), AuthActivity.class);
        intent.putExtra(AuthActivity.EXTRA_MODE, AuthActivity.MODE_LOGIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.getApplicationContext().startActivity(intent);
    }
}
