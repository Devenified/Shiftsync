package com.example.verson1;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * Central JWT + role storage for ShiftSync.
 */
public final class SessionManager {

    public static final String PREFS_NAME = "ShiftSyncPrefs";
    public static final String TOKEN_KEY = "auth_token";
    public static final String ROLE_KEY = "user_role";
    public static final String ROLE_WORKER = "worker";
    public static final String ROLE_EMPLOYER = "employer";

    private static final String TAG = "SessionManager";

    private SessionManager() {}

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String getToken(Context context) {
        return prefs(context).getString(TOKEN_KEY, null);
    }

    public static String getRole(Context context) {
        return prefs(context).getString(ROLE_KEY, null);
    }

    public static boolean isLoggedIn(Context context) {
        return getToken(context) != null && getRole(context) != null;
    }

    public static boolean isWorker(Context context) {
        return ROLE_WORKER.equalsIgnoreCase(getRole(context));
    }

    public static boolean isEmployer(Context context) {
        return ROLE_EMPLOYER.equalsIgnoreCase(getRole(context));
    }

    public static void clearSession(Context context) {
        prefs(context).edit().remove(TOKEN_KEY).remove(ROLE_KEY).apply();
    }

    public static void logoutToLogin(Context context) {
        String token = getToken(context);

        // Clear session and go to login IMMEDIATELY so the user is never stuck
        clearAndGoToLogin(context);

        // Fire-and-forget backend logout in background (non-blocking)
        if (token != null) {
            new Thread(() -> {
                try {
                    ApiClient.post("/api/users/logout", token, "{}");
                } catch (Exception ignored) {
                    // Backend logout is best-effort; local session is already cleared
                }
            }).start();
        }
    }

    /**
     * Clears session and navigates to login
     */
    private static void clearAndGoToLogin(Context context) {
        clearSession(context);
        Intent intent = new Intent(context, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    // Update profile data in session
    public static void updateProfileData(Context context, JSONObject userData) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        try {
            // Update essential profile data
            editor.putString("user_fullName", userData.optString("fullName", ""));
            editor.putString("user_phoneNumber", userData.optString("phoneNumber", ""));
            editor.putString("user_location", userData.optString("location", ""));
            editor.putBoolean("user_isAvailable", userData.optBoolean("isAvailable", false));
            editor.putBoolean("user_hasProfile", userData.optBoolean("hasProfile", false));
            editor.putInt("user_experienceYears", userData.optInt("experienceYears", 0));
            editor.putFloat("user_rating", (float) userData.optDouble("rating", 0));
            editor.putInt("user_completedShifts", userData.optInt("completedShifts", 0));
            editor.putFloat("user_totalEarnings", (float) userData.optDouble("totalEarnings", 0));
            
            // Store skills as JSON string
            if (userData.has("skills")) {
                editor.putString("user_skills", userData.getJSONArray("skills").toString());
            }
            
            editor.apply();
            
            // Broadcast update to all activities
            Intent intent = new Intent("PROFILE_UPDATED");
            intent.putExtra("updated_data", userData.toString());
            context.sendBroadcast(intent);
            
        } catch (Exception e) {
            // Handle error gracefully
        }
    }

    // Get cached profile data
    public static String getProfileData(Context context, String key) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(key, "");
    }

    /**
     * If current session is not a worker, sends user to the correct home or login.
     *
     * @return true if caller should continue as worker.
     */
    public static boolean ensureWorker(Activity activity) {
        if (!isLoggedIn(activity)) {
            logoutToLogin(activity);
            return false;
        }
        if (isWorker(activity)) {
            return true;
        }
        if (isEmployer(activity)) {
            activity.startActivity(new Intent(activity, EmployerDashboardActivity.class));
            activity.finish();
            return false;
        }
        logoutToLogin(activity);
        return false;
    }

    /**
     * If current session is not an employer, sends user to the correct home or login.
     */
    public static boolean ensureEmployer(Activity activity) {
        if (!isLoggedIn(activity)) {
            logoutToLogin(activity);
            return false;
        }
        if (isEmployer(activity)) {
            return true;
        }
        if (isWorker(activity)) {
            activity.startActivity(new Intent(activity, WorkerDashboardNewActivity.class));
            activity.finish();
            return false;
        }
        logoutToLogin(activity);
        return false;
    }
}
