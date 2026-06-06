package im.angry.openeuicc.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import im.angry.openeuicc.common.R

class TgtSimRechargeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tgt_sim_recharge)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.r2w_tgt_recharge)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
