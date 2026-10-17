package com.baryobenta.community;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

/**
 * Keeps the signed-in user's token and basic profile on the device so the
 * app stays logged in between launches. The real account lives in PostgreSQL;
 * this is only the session.
 */
public final class SessionManager {

    private static final String PREFS_NAME = "baryobenta_session_v2";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_NAME = "name";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_LOCATION = "location";
    private static final String KEY_VERIFIED = "verified";

    private SessionManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void saveSession(Context context, String token, UserInfo user) {
        prefs(context).edit()
                .putString(KEY_TOKEN, token)
                .putInt(KEY_USER_ID, user.getUserId())
                .putString(KEY_NAME, user.getFullName())
                .putString(KEY_USERNAME, user.getUsername())
                .putString(KEY_LOCATION, user.getLocation())
                .putBoolean(KEY_VERIFIED, user.isVerified())
                .apply();
    }

    public static String getToken(Context context) {
        return prefs(context).getString(KEY_TOKEN, "");
    }

    public static boolean isLoggedIn(Context context) {
        return !TextUtils.isEmpty(getToken(context));
    }

    public static int getUserId(Context context) {
        return prefs(context).getInt(KEY_USER_ID, 0);
    }

    public static String getName(Context context) {
        return prefs(context).getString(KEY_NAME, "");
    }

    public static String getLocation(Context context) {
        String loc = prefs(context).getString(KEY_LOCATION, "");
        return TextUtils.isEmpty(loc) ? context.getString(R.string.location_default) : loc;
    }

    public static void updateProfile(Context context, String name, String location, boolean verified) {
        prefs(context).edit()
                .putString(KEY_NAME, name)
                .putString(KEY_LOCATION, location)
                .putBoolean(KEY_VERIFIED, verified)
                .apply();
    }

    public static boolean isVerified(Context context) {
        return prefs(context).getBoolean(KEY_VERIFIED, false);
    }

    public static void logout(Context context) {
        prefs(context).edit().clear().apply();
    }
}
