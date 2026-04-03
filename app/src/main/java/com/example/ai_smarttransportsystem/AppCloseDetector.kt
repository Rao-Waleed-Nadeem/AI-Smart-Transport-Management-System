// New file: AppCloseDetector.kt
package com.example.ai_smarttransportsystem

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.firebase.auth.FirebaseAuth

class AppCloseDetector : Application.ActivityLifecycleCallbacks {

    private var activityReferences = 0
    private var isActivityChangingConfigurations = false

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        if (++activityReferences == 1 && !isActivityChangingConfigurations) {
            // App came to foreground
        }
    }

    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {
        isActivityChangingConfigurations = activity.isChangingConfigurations
        if (--activityReferences == 0 && !isActivityChangingConfigurations) {
            // App went to background or closed
            FirebaseAuth.getInstance().signOut()
            Log.d("Auth", "Auto-logout: App closed or backgrounded")
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}