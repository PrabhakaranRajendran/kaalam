package com.prabhu.kaalam

import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.prabhu.kaalam.data.Repo
import com.prabhu.kaalam.notify.Reminders
import com.prabhu.kaalam.security.Prefs
import com.prabhu.kaalam.security.Vault
import com.prabhu.kaalam.ui.AppRoot
import com.prabhu.kaalam.ui.AppState
import com.prabhu.kaalam.ui.KaalamTheme

class KaalamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Repo.init(this)
        Reminders.createChannel(this)
        Reminders.schedule(this)
    }
}

/** FragmentActivity (not plain ComponentActivity) because the fingerprint prompt needs it. */
class MainActivity : FragmentActivity() {
    companion object {
        const val EXTRA_WISH_PERSON = "wishPerson"
        const val EXTRA_WISH_EVENT = "wishEvent"
    }

    private fun handleWish(intent: android.content.Intent?) {
        val p = intent?.getStringExtra(EXTRA_WISH_PERSON) ?: return
        val e = intent.getStringExtra(EXTRA_WISH_EVENT) ?: return
        intent.removeExtra(EXTRA_WISH_PERSON)
        AppState.pendingWish = p to e
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleWish(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Repo.init(this)
        handleWish(intent)
        enableEdgeToEdge()
        applySecureFlags()
        setContent { KaalamTheme { AppRoot(this) } }
    }

    /** "Hide app preview" and "Block screenshots" from Security settings. */
    fun applySecureFlags() {
        val secure = Prefs.blockScreenshots || (Prefs.hidePreview && Build.VERSION.SDK_INT < 33)
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!Prefs.hidePreview)
    }

    override fun onStop() {
        super.onStop()
        AppState.backgroundAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val now = SystemClock.elapsedRealtime()
        // Coming back from Google sign-in, the file picker etc. shouldn't lock the app.
        if (now < AppState.skipLockUntil) {
            AppState.skipLockUntil = 0
            return
        }
        if (AppState.backgroundAt > 0 && Vault.hasPin() && now - AppState.backgroundAt >= Prefs.lockAfterSec * 1000L) {
            AppState.lock()
        }
        Reminders.schedule(this)
    }
}
