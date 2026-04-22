package com.example.verson1;

import android.app.Application;

public class ShiftSyncApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
    }
}

