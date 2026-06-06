package im.angry.openeuicc.ui

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
        val comingSoon = View.OnClickListener {
            Toast.makeText(this, "Support topic details coming soon", Toast.LENGTH_SHORT).show()
        }
        requireViewById<MaterialButton>(R.id.support_esim_orders).setOnClickListener(comingSoon)
        requireViewById<MaterialButton>(R.id.support_tgt_recharge).setOnClickListener(comingSoon)
        requireViewById<MaterialButton>(R.id.support_wallet).setOnClickListener(comingSoon)
        requireViewById<MaterialButton>(R.id.support_openeuicc).setOnClickListener(comingSoon)
    }
}
