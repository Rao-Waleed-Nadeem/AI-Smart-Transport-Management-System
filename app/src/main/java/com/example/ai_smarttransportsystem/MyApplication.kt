package com.example.ai_smarttransportsystem

import android.app.Application
import com.google.android.libraries.places.api.Places
import com.google.firebase.FirebaseApp

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)  // Init Firebase first
        if (!Places.isInitialized()) {
            Places.initialize(this, BuildConfig.MAPS_API_KEY)
        }
        // Register lifecycle listener for auto-logout
        registerActivityLifecycleCallbacks(AppCloseDetector())
    }
}