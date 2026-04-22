package com.example.verson1;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Captures unhandled crashes and shows a readable screen instead of silent "keeps stopping".
 */
public final class CrashReporter {

    private CrashReporter() {}

    public static void install(Context context) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                Intent i = new Intent(context, CrashScreenActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                i.putExtra(CrashScreenActivity.EXTRA_STACKTRACE, stacktrace(throwable));
                i.putExtra(CrashScreenActivity.EXTRA_THREAD, thread != null ? thread.getName() : "unknown");
                i.putExtra(CrashScreenActivity.EXTRA_DEVICE, Build.MANUFACTURER + " " + Build.MODEL);
                i.putExtra(CrashScreenActivity.EXTRA_SDK, String.valueOf(Build.VERSION.SDK_INT));
                context.startActivity(i);

                // Give the activity plenty of time to start before killing the process.
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(2);
                }, 3000);
            } catch (Exception ignored) {
                System.exit(2);
            }
        });
    }

    public static void install(Activity activity) {
        install(activity.getApplicationContext());
    }

    private static String stacktrace(Throwable t) {
        if (t == null) return "";
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}

