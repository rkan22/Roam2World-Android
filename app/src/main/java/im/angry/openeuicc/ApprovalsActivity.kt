package im.angry.openeuicc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import im.angry.openeuicc.ui.WalletApprovalsActivity

class ApprovalsActivity : Activity() {
    companion object {
        const val EXTRA_APPROVAL_MODE = "approval_mode"
        const val MODE_ADMIN_RESELLER_WALLET = "admin_reseller_wallet"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivity(
            Intent(this, WalletApprovalsActivity::class.java).apply {
                putExtra(EXTRA_APPROVAL_MODE, intent.getStringExtra(EXTRA_APPROVAL_MODE) ?: MODE_ADMIN_RESELLER_WALLET)
            }
        )
        finish()
    }
}
