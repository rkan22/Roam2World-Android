package im.angry.openeuicc.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets

class SupportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.r2w_support)

        setupInsets()
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
                mainViewPaddingInsetHandler(requireViewById(R.id.support_scroll))
            ),
            consume = false
        )
    }

    private fun setupActions() {
        requireViewById<MaterialButton>(R.id.support_esim_orders).setOnClickListener {
            startActivity(Intent(this, PurchaseHistoryActivity::class.java))
        }
        requireViewById<MaterialButton>(R.id.support_tgt_recharge).setOnClickListener {
            startActivity(Intent(this, TgtSimRechargeActivity::class.java))
        }
        requireViewById<MaterialButton>(R.id.support_wallet).setOnClickListener {
            startActivity(Intent(this, WalletActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
        requireViewById<MaterialButton>(R.id.support_openeuicc).setOnClickListener {
            openNativeOpenEuicc()
        }
    }

    private fun openNativeOpenEuicc() {
        val target = targetActivityName(DashboardActivity.META_ESIM_ACTIVITY)
        if (target.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.dashboard_missing_esim_target), Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent().setClassName(this, target))
    }

    private fun targetActivityName(key: String): String? {
        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        return appInfo.metaData?.getString(key)
    }
}
