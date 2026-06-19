package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

class SupportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            R2wMockScreen(
                title = "Support & FAQ",
                subtitle = "Help center and quick actions",
                onBack = { finish() },
                bottomTab = R2wBottomTab.More
            ) {
                R2wMockHero(
                    eyebrow = "Help desk",
                    title = "How can we help?",
                    body = "Find answers fast or jump into the right workflow for orders, recharge and wallet operations.",
                    badge = "24/7"
                )

                R2wMockCard("Open a ticket") {
                    R2wMockTextField(value = "", onValueChange = {}, label = "Subject")
                    R2wMockTextField(value = "", onValueChange = {}, label = "Describe your issue", singleLine = false, minLines = 3)
                    R2wMockPrimaryButton("Submit ticket") { }
                }

                R2wMockCard("Quick actions") {
                    R2wMockChoice("eSIM Orders", "View purchase history and activation status", selected = false) {
                        startActivity(Intent(this@SupportActivity, PurchaseHistoryActivity::class.java))
                    }
                    R2wMockChoice("TGT SIM Recharge", "Send SIM recharge or eSIM renewal request", selected = false) {
                        startActivity(Intent(this@SupportActivity, TgtSimRechargeActivity::class.java))
                    }
                    R2wMockChoice("Wallet", "Check balance and request top-up", selected = false) {
                        startActivity(Intent(this@SupportActivity, WalletActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    }
                    R2wMockChoice("OpenEUICC", "Open installation and device tools", selected = false) {
                        startActivity(Intent(this@SupportActivity, OpenEuiccIntegrationActivity::class.java))
                    }
                }

                R2wMockCard("FAQ") {
                    R2wMockStep("01", "How do I recharge a SIM?", "Open TGT SIM Recharge, choose package, enter ICCID and customer details, then activate.")
                    R2wMockStep("02", "Where is my QR code?", "Open eSIM Orders and select the order detail screen after checkout is completed.")
                    R2wMockStep("03", "How do I add wallet balance?", "Create a Wallet Request and follow its approval status in request history.")
                    R2wMockStep("04", "What if installation fails?", "Use OpenEUICC tools and verify the activation code before retrying.")
                }

                R2wMockCard("Status") {
                    R2wMockLine("Average response", "< 2 hours")
                    R2wMockLine("Priority", "Wallet / checkout issues", strong = true)
                    Text("For urgent activation problems, include ICCID, order number and phone model.", color = R2wMockColors.Muted, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
