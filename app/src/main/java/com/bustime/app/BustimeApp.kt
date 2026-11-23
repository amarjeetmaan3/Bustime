package com.bustime.app

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Android 15+ draws apps edge-to-edge when targetSdk is 35 or higher (and ignores the opt-out at 36).
 * To keep every screen's content clear of the status bar, navigation bar, camera cut-out and keyboard,
 * pad each activity's content area by the system insets once, in one place.
 */
class BustimeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 29) {
            registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
                override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
                    val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
                    ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
                        val bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars() or
                                WindowInsetsCompat.Type.displayCutout() or
                                WindowInsetsCompat.Type.ime()
                        )
                        view.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
                        WindowInsetsCompat.CONSUMED
                    }
                    ViewCompat.requestApplyInsets(content)
                }
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            })
        }
    }
}
