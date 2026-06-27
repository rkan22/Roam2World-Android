package im.angry.openeuicc.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class PurchaseReviewActivity : ComponentActivity() {

    private var loading by mutableStateOf(false)
    private var simIccid by mutableStateOf("")
    private var message by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = PackageNameCleaner.clean(intent.getStringExtra(EXTRA_NAME)) ?: "eSIM Package"
        val price = intent.getStringExtra(EXTRA_PRICE) ?: "USD 0.00"
        val customer = listOfNotNull(
            intent.getStringExtra(EXTRA_CUSTOMER_FIRST_NAME),
            intent.getStringExtra(EXTRA_CUSTOMER_LAST_NAME)
        ).joinToString(" ").ifBlank { "Customer" }

        setContent {
            PurchaseReviewScreen(
                packageName = packageName,
                packageMeta = packageMeta(),
                packagePrice = price,
                provider = intent.getStringExtra(EXTRA_PROVIDER) ?: "Roam2World",
                country = intent.getStringExtra(EXTRA_COUNTRY) ?: "--",
                customerName = customer,
                customerPhone = intent.getStringExtra(EXTRA_CUSTOMER_PHONE) ?: "--",
                simIccid = simIccid,
                loading = loading,
                message = message,
                requiresSimIccid = requiresSimIccid(),
                onSimIccidChange = { simIccid = it },
                onBack = { finish() },
                onConfirm = { submit() }
            )
        }
    }

    private fun packageMeta(): String =
        listOfNotNull(
            intent.getStringExtra(EXTRA_DATA),
            intent.getStringExtra(EXTRA_VALIDITY),
            intent.getStringExtra(EXTRA_COUNTRY)
        )
            .filter { it.isNotBlank() }
            .joinToString("  •  ")

    private fun submit() {
        val msg = "Purchase flow started"
        message = msg
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun requiresSimIccid(): Boolean {
        val provider = intent.getStringExtra(EXTRA_PROVIDER).orEmpty().lowercase()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty().lowercase()
        val type = intent.getStringExtra(EXTRA_TYPE).orEmpty().lowercase()

        // TGT has two separate flows:
        // 1) TGT eSIM purchase: no ICCID required
        // 2) TGT physical SIM Card recharge: ICCID required
        val isTgt = provider.contains("tgt")
        val isEsim =
            type.contains("esim") ||
            name.contains("esim") ||
            name.contains("e-sim")

        val isPhysicalSimCard =
            type == "sim" ||
            type.contains("sim card") ||
            type.contains("physical") ||
            name.contains("sim card") ||
            name.contains("physical sim")

        return isTgt && isPhysicalSimCard && !isEsim
    }

    companion object {
        private const val EXTRA_ID = "package.id"
        private const val EXTRA_PROVIDER = "package.provider"
        private const val EXTRA_TYPE = "package.type"
        private const val EXTRA_NAME = "package.name"
        private const val EXTRA_COUNTRY = "package.country"
        private const val EXTRA_PRICE = "package.price"
        private const val EXTRA_DATA = "package.data"
        private const val EXTRA_VALIDITY = "package.validity"
        private const val EXTRA_CUSTOMER_FIRST_NAME = "customer.first_name"
        private const val EXTRA_CUSTOMER_LAST_NAME = "customer.last_name"
        private const val EXTRA_CUSTOMER_PHONE = "customer.phone"

        fun createIntent(
            context: Context,
            intent: Intent,
            first: String,
            last: String,
            phone: String,
            balance: String?
        ): Intent {
            val i = Intent(context, PurchaseReviewActivity::class.java)
            listOf(
                EXTRA_ID,
                EXTRA_PROVIDER,
                EXTRA_TYPE,
                EXTRA_NAME,
                EXTRA_COUNTRY,
                EXTRA_PRICE,
                EXTRA_DATA,
                EXTRA_VALIDITY
            ).forEach { i.putExtra(it, intent.getStringExtra(it)) }
            i.putExtra(EXTRA_CUSTOMER_FIRST_NAME, first)
            i.putExtra(EXTRA_CUSTOMER_LAST_NAME, last)
            i.putExtra(EXTRA_CUSTOMER_PHONE, phone)
            return i
        }
    }
}

@Composable
private fun PurchaseReviewScreen(
    packageName: String,
    packageMeta: String,
    packagePrice: String,
    provider: String,
    country: String,
    customerName: String,
    customerPhone: String,
    simIccid: String,
    loading: Boolean,
    message: String?,
    requiresSimIccid: Boolean,
    onSimIccidChange: (String) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    val blue = Color(0xFF1263F1)
    val dark = Color(0xFF07142F)
    val muted = Color(0xFF738099)
    val bg = Color(0xFFF4F8FD)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E9F3), RoundedCornerShape(16.dp))
                    ) {
                        Text("‹", color = dark, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "Confirm Order",
                        color = dark,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 52.dp),
                        textAlign = TextAlign.Center
                    )
                }

                ReviewHeroCard(
                    packageName = packageName,
                    packageMeta = packageMeta,
                    packagePrice = packagePrice,
                    provider = provider,
                    blue = blue
                )

                ReviewInfoCard(title = "Customer") {
                    ReviewLine(
                        iconRes = im.angry.openeuicc.common.R.drawable.r2w_ic_customer,
                        label = "Name",
                        value = customerName
                    )
                    ReviewLine(
                        iconRes = im.angry.openeuicc.common.R.drawable.r2w_ic_phone,
                        label = "Phone",
                        value = customerPhone
                    )
                }

                ReviewInfoCard(title = "Package") {
                    ReviewLine(
                        iconRes = im.angry.openeuicc.common.R.drawable.r2w_ic_provider,
                        label = "Provider",
                        value = provider.ifBlank { "Roam2World" }
                    )
                    ReviewLine(
                        iconRes = im.angry.openeuicc.common.R.drawable.r2w_ic_coverage,
                        label = "Coverage",
                        value = country.ifBlank { "--" }
                    )
                }

                if (requiresSimIccid) {
                    ReviewInfoCard(title = "SIM ICCID") {
                        OutlinedTextField(
                            value = simIccid,
                            onValueChange = onSimIccidChange,
                            label = { Text("ICCID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = blue,
                                unfocusedBorderColor = Color(0xFFDCE4F0)
                            )
                        )
                    }
                }

                ReviewInfoCard(title = "Payment") {
                    ReviewLine(
                        iconRes = im.angry.openeuicc.common.R.drawable.r2w_ic_payment,
                        label = "Payment method",
                        value = "Wallet"
                    )
                    ReviewLine(
                        iconRes = im.angry.openeuicc.common.R.drawable.r2w_ic_package,
                        label = "Tax",
                        value = "USD 0.00"
                    )
                    ReviewLine(
                        iconRes = im.angry.openeuicc.common.R.drawable.r2w_ic_total,
                        label = "Total",
                        value = packagePrice,
                        strong = true
                    )
                }

                message?.takeIf { it.isNotBlank() }?.let {
                    ReviewStatusCard(it)
                }

                Button(
                    onClick = onConfirm,
                    enabled = !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = blue),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Processing")
                    } else {
                        Text("Confirm Purchase", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ReviewHeroCard(
    packageName: String,
    packageMeta: String,
    packagePrice: String,
    provider: String,
    blue: Color
) {
    val parts = packageMeta.split("•").map { it.trim() }.filter { it.isNotBlank() }
    val data = parts.getOrNull(0) ?: "Mobile data"
    val validity = formatReviewValidity(parts.getOrNull(1) ?: "30 Days")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = im.angry.openeuicc.common.R.drawable.store_banner),
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 118.dp, height = 82.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = packageName,
                        color = Color(0xFF07142F),
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = listOf(data, validity).joinToString("  •  "),
                        color = Color(0xFF5F6B82),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = provider.ifBlank { "Roam2World" },
                        color = Color(0xFF5F6B82),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFE8EDF5))

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReviewMiniSummaryItem("Data", data, blue, Modifier.weight(1f))
                ReviewMiniSummaryItem("Validity", validity, blue, Modifier.weight(1f))
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("Price", color = Color(0xFF738099), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(packagePrice, color = blue, fontSize = 19.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}


@Composable
private fun ReviewMiniSummaryItem(
    label: String,
    value: String,
    blue: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEAF2FF)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (label == "Data") "⇅" else "▣", color = blue, fontWeight = FontWeight.Black)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(label, color = Color(0xFF738099), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color(0xFF07142F), fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ReviewInfoCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
private fun ReviewLine(
    iconRes: Int,
    label: String,
    value: String,
    strong: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            modifier = Modifier.size(30.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            color = Color(0xFF738099),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            color = if (strong) Color(0xFF1263F1) else Color(0xFF07142F),
            fontSize = 15.sp,
            fontWeight = if (strong) FontWeight.Black else FontWeight.Bold,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ReviewStatusCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = Color(0xFF9A3412),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatReviewValidity(raw: String): String {
    val value = raw.trim().replace(Regex("\\s+"), " ")
    val match = Regex("^(\\d+)\\s*(day|days|d)$", RegexOption.IGNORE_CASE).find(value)
    return if (match != null) "${match.groupValues[1]} Days" else value
}
