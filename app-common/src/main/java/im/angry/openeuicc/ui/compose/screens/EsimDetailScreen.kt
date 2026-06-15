@file:OptIn(ExperimentalMaterial3Api::class)

package im.angry.openeuicc.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.ui.compose.components.R2WStatusBadge
import im.angry.openeuicc.ui.compose.theme.*

@Composable
fun EsimDetailScreen(
    esim: MobileEsim,
    onBackClick: () -> Unit,
    onActionClick: (String) -> Unit
) {
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("eSIM Detail", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            R2WStatusBadge(
                                text = esim.statusLabel() ?: "Active",
                                status = esim.status ?: "active",
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = esim.title(),
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = esim.provider ?: "Orange",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                        Icon(
                            Icons.Default.SimCard,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            item {
                DetailSection(title = "Usage Overview") {
                    val remaining = esim.dataRemaining ?: "6.8 GB"
                    val total = "10 GB"
                    val percentage = 0.68f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Data Remaining", fontSize = 12.sp, color = TextSecondary)
                        Text("$remaining / $total", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = percentage,
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = Primary,
                        trackColor = Border,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Expires In", fontSize = 11.sp, color = TextSecondary)
                            Text("12 Days", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Expiry Date", fontSize = 11.sp, color = TextSecondary)
                            Text(esim.expiresAt ?: "Jun 6, 2025", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }
            }

            item {
                DetailSection(title = "eSIM Actions") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionIconButton(Icons.Default.QrCode, "View QR", { onActionClick("qr") }, Modifier.weight(1f))
                        ActionIconButton(Icons.Default.Download, "Install", { onActionClick("install") }, Modifier.weight(1f))
                        ActionIconButton(Icons.Default.Share, "Share", { onActionClick("share") }, Modifier.weight(1f))
                    }
                }
            }

            item {
                DetailSection(title = "eSIM Information") {
                    DetailRow(null, "ICCID", esim.iccid ?: "N/A")
                    DetailRow(null, "Provider", esim.provider ?: "N/A")
                    DetailRow(null, "Package", esim.packageName ?: "N/A")
                    DetailRow(null, "Network", "Orange 4G/5G")
                }
            }
            
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun ActionIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = Primary)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 11.sp)
        }
    }
}
