package im.angry.openeuicc.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight

class TgtSimRechargeActivity : ComponentActivity() {
    private var mode by mutableStateOf(TgtMode.ESIM_RENEWAL)
    private var selectedPackageName by mutableStateOf("10GB / 30 Days")
    private var selectedRenewalPackageName by mutableStateOf("10GB / 30 Days")
    private var simIccid by mutableStateOf("")
    private var simCustomerName by mutableStateOf("")
    private var simCustomerPhone by mutableStateOf("")
    private var esimIccid by mutableStateOf("")
    private var esimCustomerName by mutableStateOf("")
    private var esimCustomerPhone by mutableStateOf("")
    private var esimCustomerEmail by mutableStateOf("")
    private var message by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyPrefilledIccid()
        setContent {
            R2wMockScreen(
                title = "TGT SIM Recharge",
                subtitle = "Orange Balkans recharge and eSIM renewal",
                onBack = { finish() },
                bottomTab = R2wBottomTab.Esims
            ) {
                R2wMockHero(
                    eyebrow = "Recharge",
                    title = if (mode == TgtMode.SIM_RECHARGE) "SIM top-up" else "eSIM renewal",
                    body = "Select package, enter ICCID and customer details, then continue the recharge flow.",
                    amount = if (mode == TgtMode.SIM_RECHARGE) selectedPackageName else selectedRenewalPackageName,
                    badge = "Orange"
                )

                R2wMockCard("Recharge type") {
                    R2wMockChoice("SIM Recharge", "Physical Orange Balkans SIM recharge", selected = mode == TgtMode.SIM_RECHARGE) {
                        mode = TgtMode.SIM_RECHARGE
                        message = null
                    }
                    R2wMockChoice("eSIM Renewal", "Renew data on an existing Orange eSIM", selected = mode == TgtMode.ESIM_RENEWAL) {
                        mode = TgtMode.ESIM_RENEWAL
                        message = null
                    }
                }

                if (mode == TgtMode.SIM_RECHARGE) {
                    R2wMockCard("Choose package") {
                        simPackages().forEach { option ->
                            R2wMockChoice(option, "30 days validity", selected = selectedPackageName == option) { selectedPackageName = option }
                        }
                    }
                    R2wMockCard("SIM information") {
                        R2wMockTextField(simIccid, { simIccid = it }, "ICCID")
                        R2wMockTextField(simCustomerName, { simCustomerName = it }, "Customer name")
                        R2wMockTextField(simCustomerPhone, { simCustomerPhone = it }, "Customer phone")
                    }
                    R2wMockPrimaryButton("Activate") { showSubmitted("SIM recharge request prepared") }
                } else {
                    R2wMockCard("Choose renewal package") {
                        renewalPackages().forEach { option ->
                            R2wMockChoice(option, "Orange eSIM data renewal", selected = selectedRenewalPackageName == option) { selectedRenewalPackageName = option }
                        }
                    }
                    R2wMockCard("eSIM information") {
                        R2wMockTextField(esimIccid, { esimIccid = it }, "Orange eSIM ICCID")
                        R2wMockTextField(esimCustomerName, { esimCustomerName = it }, "Customer name")
                        R2wMockTextField(esimCustomerPhone, { esimCustomerPhone = it }, "Customer phone")
                        R2wMockTextField(esimCustomerEmail, { esimCustomerEmail = it }, "Email optional")
                    }
                    R2wMockPrimaryButton("Continue Renewal") { showSubmitted("eSIM renewal request prepared") }
                }

                message?.let {
                    R2wMockCard("Status") {
                        Text(it, color = R2wMockColors.Success, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    private fun applyPrefilledIccid() {
        val prefilled = intent.getStringExtra(EXTRA_RENEW_ICCID) ?: intent.getStringExtra(EXTRA_ICCID) ?: return
        if (prefilled.isBlank()) return
        mode = TgtMode.ESIM_RENEWAL
        esimIccid = prefilled
    }

    private fun showSubmitted(text: String) {
        message = text
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_RENEW_ICCID = "renew.iccid"
        const val EXTRA_ICCID = "iccid"
    }
}

private enum class TgtMode { SIM_RECHARGE, ESIM_RENEWAL }

private fun simPackages(): List<String> = listOf(
    "3GB / 15 Days",
    "5GB / 30 Days",
    "10GB / 30 Days",
    "20GB / 30 Days"
)

private fun renewalPackages(): List<String> = listOf(
    "5GB / 30 Days",
    "10GB / 30 Days",
    "20GB / 30 Days",
    "50GB / 30 Days"
)
