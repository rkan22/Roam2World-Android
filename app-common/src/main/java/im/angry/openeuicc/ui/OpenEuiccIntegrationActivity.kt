package im.angry.openeuicc.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets

class OpenEuiccIntegrationActivity : AppCompatActivity() {
    private lateinit var scroll: NestedScrollView
    private lateinit var nativeStatus: TextView
    private lateinit var profiles: LinearLayout
    private lateinit var installNew: MaterialButton
    private lateinit var refreshProfiles: MaterialButton
    private lateinit var openNative: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_openeuicc_integration)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "OpenEUICC"

        scroll = requireViewById(R.id.openeuicc_scroll)
        nativeStatus = requireViewById(R.id.openeuicc_native_status)
        profiles = requireViewById(R.id.openeuicc_profiles)
        installNew = requireViewById(R.id.openeuicc_install_new)
        refreshProfiles = requireViewById(R.id.openeuicc_refresh_profiles)
        openNative = requireViewById(R.id.openeuicc_open_native)

        setupInsets()
        renderStatus()
        renderProfiles()
        setupActions()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
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

    private fun setupActions() {
        installNew.setOnClickListener { openNativeOpenEuicc() }
        refreshProfiles.setOnClickListener {
            Toast.makeText(this, "Profiles refreshed", Toast.LENGTH_SHORT).show()
            renderStatus()
            renderProfiles()
        }
        openNative.setOnClickListener { openNativeOpenEuicc() }
    }

    private fun renderStatus() {
        val target = targetActivityName(DashboardActivity.META_ESIM_ACTIVITY)
        nativeStatus.text = if (target.isNullOrBlank()) {
            "Native OpenEUICC target unavailable on this build. You can still view eSIMs inside Roam2World."
        } else {
            "Ready • Native OpenEUICC integration available"
        }
    }

    private fun renderProfiles() {
        profiles.removeAllViews()
        listOf(
            ProfileUi("Orange Europe 50GB", "ICCID 89320400012345678", "Active", "25 May 2024"),
            ProfileUi("Roam2World Turkey 20GB", "ICCID 89320400012345679", "Inactive", "24 May 2024"),
            ProfileUi("Orange Balkans 30GB", "ICCID 89320400012345680", "Pending", "26 May 2024")
        ).forEach { profile ->
            profiles.addView(profileView(profile))
        }
    }

    private fun profileView(profile: ProfileUi): View {
        val view = LayoutInflater.from(this).inflate(R.layout.openeuicc_profile_item, profiles, false)
        view.requireViewById<TextView>(R.id.openeuicc_profile_name).text = profile.name
        view.requireViewById<TextView>(R.id.openeuicc_profile_iccid).text = profile.iccid
        view.requireViewById<TextView>(R.id.openeuicc_profile_status).text = profile.status
        view.requireViewById<TextView>(R.id.openeuicc_profile_date).text = profile.date
        view.requireViewById<MaterialButton>(R.id.openeuicc_profile_switch).setOnClickListener {
            Toast.makeText(this, "Switch profile via native OpenEUICC", Toast.LENGTH_SHORT).show()
            openNativeOpenEuicc()
        }
        view.requireViewById<MaterialButton>(R.id.openeuicc_profile_delete).setOnClickListener {
            Toast.makeText(this, "Delete profile via native OpenEUICC", Toast.LENGTH_SHORT).show()
            openNativeOpenEuicc()
        }
        return view
    }

    private fun openNativeOpenEuicc() {
        val target = targetActivityName(DashboardActivity.META_ESIM_ACTIVITY)
        if (target.isNullOrBlank()) {
            Toast.makeText(this, "Native OpenEUICC target unavailable", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent().setClassName(this, target))
    }

    private fun targetActivityName(key: String): String? =
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            .metaData
            ?.getString(key)

    private data class ProfileUi(
        val name: String,
        val iccid: String,
        val status: String,
        val date: String
    )
}
