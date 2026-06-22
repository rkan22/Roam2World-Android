package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.common.R

class SupportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val blue = Color(0xFF1263F1)
            val bg = Color(0xFFF4F8FD)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(WindowInsets.statusBars.asPaddingValues())
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 96.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { finish() }) {
                            Text("Back", color = blue, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "Support",
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF07142F),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.width(64.dp))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF1263F1), Color(0xFF0B3BAA))
                                    )
                                )
                                .padding(22.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    R2wIconBadge(
                                        iconRes = R.drawable.r2w_ic_customer,
                                        contentDescription = "Support",
                                        background = Color.White.copy(alpha = 0.18f)
                                    )

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column {
                                        Text(
                                            text = "How can we help?",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 24.sp
                                        )
                                        Text(
                                            text = "Orders, wallet, installation and recharge help",
                                            color = Color.White.copy(alpha = 0.82f),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(22.dp))

                                Text(
                                    text = "Average response: < 2 hours",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 25.sp
                                )

                                Text(
                                    text = "Include order number, ICCID and phone model for faster support.",
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    SupportCard(title = "Quick Actions") {
                        SupportAction(
                            iconRes = R.drawable.r2w_ic_receipt,
                            title = "eSIM Orders",
                            subtitle = "View purchase history and activation status",
                            onClick = {
                                startActivity(Intent(this@SupportActivity, PurchaseHistoryActivity::class.java))
                            }
                        )

                        SupportAction(
                            iconRes = R.drawable.r2w_ic_renewal,
                            title = "TGT SIM Recharge",
                            subtitle = "Recharge physical SIM or renew existing Orange eSIM",
                            onClick = {
                                startActivity(Intent(this@SupportActivity, TgtSimRechargeActivity::class.java))
                            }
                        )

                        SupportAction(
                            iconRes = R.drawable.r2w_ic_wallet,
                            title = "Wallet",
                            subtitle = "Check balance and request top-up",
                            onClick = {
                                startActivity(
                                    Intent(this@SupportActivity, WalletActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                )
                            }
                        )

                        SupportAction(
                            iconRes = R.drawable.r2w_ic_package,
                            title = "Installation Tools",
                            subtitle = "Open eSIM installation and device tools",
                            onClick = {
                                startActivity(Intent(this@SupportActivity, OpenEuiccIntegrationActivity::class.java))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    SupportCard(title = "FAQ") {
                        FaqItem(
                            number = "01",
                            question = "How do I recharge a physical SIM?",
                            answer = "Open TGT SIM Recharge, choose a package, enter ICCID and customer details, then prepare the recharge request."
                        )

                        FaqItem(
                            number = "02",
                            question = "Where is my QR code?",
                            answer = "Open eSIM Orders and select your completed order. The QR or activation details appear inside the order detail."
                        )

                        FaqItem(
                            number = "03",
                            question = "How do I add wallet balance?",
                            answer = "Create a Wallet Request and track approval status from Wallet Request History."
                        )

                        FaqItem(
                            number = "04",
                            question = "What if installation fails?",
                            answer = "Verify the activation code, device compatibility and network connection, then retry from installation tools."
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                R2wBottomNav(
                    selected = R2wBottomTab.More,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(WindowInsets.navigationBars.asPaddingValues())
                )
            }
        }
    }
}

@Composable
private fun SupportCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF07142F),
                fontWeight = FontWeight.Black,
                fontSize = 20.sp
            )

            HorizontalDivider(color = Color(0xFFE8EDF5))

            content()
        }
    }
}

@Composable
private fun SupportAction(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), shape)
            .background(Color(0xFFF8FAFD), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        R2wIconBadge(
            iconRes = iconRes,
            contentDescription = title
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color(0xFF07142F),
                fontWeight = FontWeight.Black,
                fontSize = 16.sp
            )
            Text(
                text = subtitle,
                color = Color(0xFF738099),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }

        Text(
            text = "Open",
            color = Color(0xFF1263F1),
            fontWeight = FontWeight.Black,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun FaqItem(
    number: String,
    question: String,
    answer: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFFEAF2FF), RoundedCornerShape(14.dp))
                .padding(horizontal = 11.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Color(0xFF1263F1),
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = question,
                color = Color(0xFF07142F),
                fontWeight = FontWeight.Black,
                fontSize = 15.sp
            )
            Text(
                text = answer,
                color = Color(0xFF738099),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}
