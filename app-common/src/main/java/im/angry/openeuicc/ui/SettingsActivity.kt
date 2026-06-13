package im.angry.openeuicc.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.google.android.material.materialswitch.MaterialSwitch
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets

class SettingsActivity : AppCompatActivity() {
    private lateinit var scroll: View
    private lateinit var notificationsSwitch: MaterialSwitch
    private lateinit var biometricSwitch: MaterialSwitch
    private lateinit var darkModeSwitch: MaterialSwitch

    private val prefs by lazy { getSharedPreferences("r2w_mobile_settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        scroll = requireViewById(R.id.settings_scroll)
        notificationsSwitch = requireViewById(R.id.settings_notifications_switch)
        biometricSwitch = requireViewById(R.id.settings_biometric_switch)
        darkModeSwitch = requireViewById(R.id.settings_dark_mode_switch)

        setupInsets()
        setupSettings()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun setupInsets() {
        setupRootViewSystemBarInsets(
            window.decorView.rootView,
            arrayOf(
                this::activityToolbarInsetHandler,
                mainViewPaddingInsetHandler(scroll)
            ),
            consume = false
        )
    }

    private fun setupSettings() {
        findViewById<TextView>(R.id.settings_version).text = "Roam2World Mobile"

        notificationsSwitch.isChecked = prefs.getBoolean("notifications_enabled", true)
        biometricSwitch.isChecked = prefs.getBoolean("biometric_login_enabled", false)
        darkModeSwitch.isChecked = prefs.getBoolean("dark_mode_enabled", false)

        notificationsSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit { putBoolean("notifications_enabled", checked) }
            Toast.makeText(this, if (checked) "Notifications enabled" else "Notifications disabled", Toast.LENGTH_SHORT).show()
        }

        biometricSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit { putBoolean("biometric_login_enabled", checked) }
            Toast.makeText(this, if (checked) "Biometric login enabled" else "Biometric login disabled", Toast.LENGTH_SHORT).show()
        }

        darkModeSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit { putBoolean("dark_mode_enabled", checked) }
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            recreate()
        }

        findViewById<View>(R.id.settings_language_row).setOnClickListener {
            openLanguageSettings()
        }
    }

    private fun openLanguageSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val uri = Uri.fromParts("package", packageName, null)
            val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS, uri)
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return
            }
        }

        Toast.makeText(this, "Language settings are managed by system settings", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_LOCALE_SETTINGS))
    }
}
