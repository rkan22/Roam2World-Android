package im.angry.openeuicc

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class ApprovalsActivity : Activity() {

    companion object {
        const val EXTRA_APPROVAL_MODE = "approval_mode"
        const val MODE_RESELLER_DEALER_WALLET = "reseller_dealer_wallet"
        const val MODE_ADMIN_RESELLER_WALLET = "admin_reseller_wallet"
    }

    private var approvalMode: String = MODE_RESELLER_DEALER_WALLET

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_approvals)

        approvalMode = intent.getStringExtra(EXTRA_APPROVAL_MODE) ?: MODE_RESELLER_DEALER_WALLET

        val titleText = findViewById<TextView>(R.id.titleText)
        val emptyText = findViewById<TextView>(R.id.emptyText)
        val container = findViewById<LinearLayout>(R.id.requestsContainer)

        titleText.text = when (approvalMode) {
            MODE_ADMIN_RESELLER_WALLET -> "Reseller Top-up Approvals"
            else -> "Dealer Wallet Approvals"
        }

        emptyText.text = when (approvalMode) {
            MODE_ADMIN_RESELLER_WALLET -> "No pending reseller top-up requests"
            else -> "No pending dealer wallet requests"
        }

        addDemoRow(container)
    }

    private fun addDemoRow(container: LinearLayout) {
        val info = TextView(this)
        info.text = when (approvalMode) {
            MODE_ADMIN_RESELLER_WALLET ->
                "Admin approvals screen ready. Backend request list will be connected here."
            else ->
                "Reseller approvals screen ready. Existing backend flow can be connected here."
        }
        info.textSize = 15f
        info.setPadding(0, 16, 0, 16)
        container.addView(info)

        val approveButton = Button(this)
        approveButton.text = "Approve Test"
        approveButton.setOnClickListener {
            Toast.makeText(this, "Approve action placeholder", Toast.LENGTH_SHORT).show()
        }
        container.addView(approveButton)

        val rejectButton = Button(this)
        rejectButton.text = "Reject Test"
        rejectButton.setOnClickListener {
            Toast.makeText(this, "Reject action placeholder", Toast.LENGTH_SHORT).show()
        }
        container.addView(rejectButton)
    }
}
