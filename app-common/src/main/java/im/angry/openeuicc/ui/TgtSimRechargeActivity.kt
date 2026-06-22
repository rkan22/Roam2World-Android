package im.angry.openeuicc.ui

import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.common.R

class TgtSimRechargeActivity : ComponentActivity() {
    private var mode by mutableStateOf(TgtMode.SIM_RECHARGE)
    private var selectedPackageName by mutableStateOf("10GB / 30 Days")
    private var selectedRenewalPackageName by mutableStateOf("10GB / 30 Days")
    private var simIccid by mutableStateOf("")
    private var simCustomerName by mutableStateOf("")
    private var simCustomerPhone by mutableStateOf("")
    private var esimIccid by mutableStateOf("")
    private var esimCustomerName by mutableStateOf("")
    private var esimCustomerPhone by mutableStateOf("")
    private var message by mutableStateOf<String?>(null)
    private var error by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyPrefilledIccid()

        setContent {
            val blue = Color(0xFF1263F1)
            val orange = Color(0xFFFF7900)
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
                            text = "TGT Recharge",
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
                                        listOf(
                                            orange,
                                            Color(0xFFE55200)
                                        )
                                    )
                                )
                                .padding(22.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    R2wIconBadge(
                                        iconRes = R.drawable.r2w_ic_renewal,
                                        contentDescription = "Recharge",
                                        background = Color.White.copy(alpha = 0.20f)
                                    )

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column {
                                        Text(
                                            text = if (mode == TgtMode.SIM_RECHARGE) "Physical SIM Recharge" else "eSIM Renewal",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 23.sp
                                        )
                                        Text(
                                            text = "Orange / TGT connectivity service",
                                            color = Color.White.copy(alpha = 0.84f),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = if (mode == TgtMode.SIM_RECHARGE) selectedPackageName else selectedRenewalPackageName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 31.sp
                                )

                                Text(
                                    text = if (mode == TgtMode.SIM_RECHARGE)
                                        "ICCID printed on the physical SIM is required"
                                    else
                                        "Existing Orange eSIM ICCID is required",
                                    color = Color.White.copy(alpha = 0.86f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

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
                                text = "Recharge Type",
                                color = Color(0xFF07142F),
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            )

                            RechargeModeChoice(
                                title = "Physical SIM Recharge",
                                subtitle = "Recharge a TGT / Orange physical SIM card with ICCID",
                                selected = mode == TgtMode.SIM_RECHARGE,
                                iconRes = R.drawable.r2w_ic_package,
                                accent = orange
                            ) {
                                mode = TgtMode.SIM_RECHARGE
                                clearMessages()
                            }

                            RechargeModeChoice(
                                title = "eSIM Renewal",
                                subtitle = "Renew data on an existing Orange eSIM profile",
                                selected = mode == TgtMode.ESIM_RENEWAL,
                                iconRes = R.drawable.r2w_ic_renewal,
                                accent = blue
                            ) {
                                mode = TgtMode.ESIM_RENEWAL
                                clearMessages()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                R2wIconBadge(
                                    iconRes = R.drawable.r2w_ic_data,
                                    contentDescription = "Package"
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "Choose Package",
                                        color = Color(0xFF07142F),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp
                                    )
                                    Text(
                                        text = if (mode == TgtMode.SIM_RECHARGE) "Physical SIM recharge bundles" else "eSIM renewal bundles",
                                        color = Color(0xFF738099),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE8EDF5))

                            val options = if (mode == TgtMode.SIM_RECHARGE) simPackages() else renewalPackages()
                            options.forEach { option ->
                                RechargePackageChoice(
                                    title = option,
                                    subtitle = if (option.contains("60")) "60 days validity" else "30 days validity",
                                    selected = if (mode == TgtMode.SIM_RECHARGE) selectedPackageName == option else selectedRenewalPackageName == option,
                                    accent = if (mode == TgtMode.SIM_RECHARGE) orange else blue
                                ) {
                                    if (mode == TgtMode.SIM_RECHARGE) {
                                        selectedPackageName = option
                                    } else {
                                        selectedRenewalPackageName = option
                                    }
                                    clearMessages()
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                R2wIconBadge(
                                    iconRes = R.drawable.r2w_ic_customer,
                                    contentDescription = "Customer"
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = if (mode == TgtMode.SIM_RECHARGE) "SIM Information" else "eSIM Information",
                                        color = Color(0xFF07142F),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp
                                    )
                                    Text(
                                        text = "Required customer and ICCID details",
                                        color = Color(0xFF738099),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE8EDF5))

                            if (mode == TgtMode.SIM_RECHARGE) {
                                RechargeTextField(simIccid, { simIccid = it }, "Physical SIM ICCID", orange)
                                RechargeTextField(simCustomerName, { simCustomerName = it }, "Customer name", orange)
                                RechargeTextField(simCustomerPhone, { simCustomerPhone = it }, "Customer phone", orange)
                            } else {
                                RechargeTextField(esimIccid, { esimIccid = it }, "Orange eSIM ICCID", blue)
                                RechargeTextField(esimCustomerName, { esimCustomerName = it }, "Customer name", blue)
                                RechargeTextField(esimCustomerPhone, { esimCustomerPhone = it }, "Customer phone", blue)
                            }
                        }
                    }

                    error?.let {
                        Spacer(modifier = Modifier.height(14.dp))
                        TgtStatusCard(it, danger = true)
                    }

                    message?.let {
                        Spacer(modifier = Modifier.height(14.dp))
                        TgtStatusCard(it, danger = false)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { submit() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mode == TgtMode.SIM_RECHARGE) orange else blue
                        ),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Text(
                            text = if (mode == TgtMode.SIM_RECHARGE) "Prepare SIM Recharge" else "Prepare eSIM Renewal",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp
                        )
                    }

                    TextButton(
                        onClick = { finish() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Cancel",
                            color = Color(0xFF738099),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                R2wBottomNav(
                    selected = R2wBottomTab.Esims,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(WindowInsets.navigationBars.asPaddingValues())
                )
            }
        }
    }

    private fun applyPrefilledIccid() {
        val prefilled = intent.getStringExtra(EXTRA_RENEW_ICCID) ?: intent.getStringExtra(EXTRA_ICCID) ?: return
        if (prefilled.isBlank()) return
        mode = TgtMode.ESIM_RENEWAL
        esimIccid = prefilled
    }

    private fun submit() {
        clearMessages()

        if (mode == TgtMode.SIM_RECHARGE) {
            if (simIccid.trim().isBlank()) {
                error = "Physical SIM ICCID is required."
                return
            }
            if (simCustomerName.trim().isBlank() || simCustomerPhone.trim().isBlank()) {
                error = "Customer name and phone are required."
                return
            }
            showSubmitted("SIM recharge request prepared: $selectedPackageName")
        } else {
            if (esimIccid.trim().isBlank()) {
                error = "Existing eSIM ICCID is required for renewal."
                return
            }
            if (esimCustomerName.trim().isBlank() || esimCustomerPhone.trim().isBlank()) {
                error = "Customer name and phone are required."
                return
            }
            showSubmitted("eSIM renewal request prepared: $selectedRenewalPackageName")
        }
    }

    private fun clearMessages() {
        message = null
        error = null
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

@Composable
private fun RechargeModeChoice(
    title: String,
    subtitle: String,
    selected: Boolean,
    iconRes: Int,
    accent: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else Color(0xFFE2E8F0),
                shape = shape
            )
            .background(
                color = if (selected) accent.copy(alpha = 0.08f) else Color(0xFFF8FAFD),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        R2wIconBadge(
            iconRes = iconRes,
            contentDescription = title,
            background = if (selected) accent.copy(alpha = 0.14f) else Color(0xFFEAF2FF)
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
            text = if (selected) "Selected" else "Choose",
            color = if (selected) accent else Color(0xFF738099),
            fontWeight = FontWeight.Black,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun RechargePackageChoice(
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else Color(0xFFE2E8F0),
                shape = shape
            )
            .background(
                color = if (selected) accent.copy(alpha = 0.08f) else Color.White,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            text = if (selected) "✓" else "",
            color = accent,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp
        )
    }
}

@Composable
private fun RechargeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    accent: Color
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = Color(0xFFDCE4F0)
        )
    )
}

@Composable
private fun TgtStatusCard(
    text: String,
    danger: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (danger) Color(0xFFFFEEF0) else Color(0xFFEFFFF6)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            color = if (danger) Color(0xFFB42318) else Color(0xFF087443),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

private enum class TgtMode { SIM_RECHARGE, ESIM_RENEWAL }

private fun simPackages(): List<String> = listOf(
    "10GB / 30 Days",
    "20GB / 30 Days",
    "30GB / 30 Days",
    "50GB / 30 Days",
    "20GB / 60 Days",
    "60GB / 60 Days"
)

private fun renewalPackages(): List<String> = listOf(
    "5GB / 30 Days",
    "10GB / 30 Days",
    "20GB / 30 Days",
    "50GB / 30 Days"
)
