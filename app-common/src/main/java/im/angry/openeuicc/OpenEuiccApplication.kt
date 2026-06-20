package im.angry.openeuicc

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import im.angry.openeuicc.di.AppContainer
import im.angry.openeuicc.di.DefaultAppContainer

open class OpenEuiccApplication : Application() {
    open val appContainer: AppContainer by lazy {
        DefaultAppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()

        val darkModeEnabled = getSharedPreferences("r2w_mobile_settings", MODE_PRIVATE)
            .getBoolean("dark_mode_enabled", false)
        AppCompatDelegate.setDefaultNightMode(
            if (darkModeEnabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        // Observe dynamic colors changes
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}