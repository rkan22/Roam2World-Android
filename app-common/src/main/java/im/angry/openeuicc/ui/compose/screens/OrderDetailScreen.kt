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
import im.angry.openeuicc.auth.MobileOrder
import im.angry.openeuicc.ui.compose.components.R2WStatusBadge
import im.angry.openeuicc.ui.compose.theme.*

@Composable
fun OrderDetailScreen(
    order: MobileOrder,
    onBackClick: () -> Unit,
    onActionClick: (String) -> Unit
) {
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Order Detail", fontWeight = FontWeight.Bold) },
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
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        R2WStatusBadge(
                            text = order.statusLabel() ?: "Unknown",
                            status = order.status ?: "",
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "Order ID",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = order.displayNumber(),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                DetailSection(title = "Customer Information") {
                    DetailRow(Icons.Default.Person, "Name", order.customerName() ?: "N/A")
                    DetailRow(Icons.Default.Phone, "Phone", order.customerPhone ?: "N/A")
                    DetailRow(Icons.Default.Email, "Email", order.customerEmail ?: "N/A")
                }
            }

            item {
                DetailSection(title = "Package Details") {
                    DetailRow(Icons.Default.Inventory, "Package", order.packageName)
                    DetailRow(Icons.Default.Business, "Provider", order.provider ?: "N/A")
                    DetailRow(Icons.Default.CalendarToday, "Date", order.createdAt ?: "N/A")
                }
            }

            item {
                DetailSection(title = "Pricing Summary") {
                    DetailRow(null, "Package Price", order.price ?: "$0.00")
                    DetailRow(null, "Tax", "$0.00")
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = Border)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Paid", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(order.price ?: "$0.00", fontWeight = FontWeight.Bold, color = Primary, fontSize = 18.sp)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onActionClick("receipt") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Receipt")
                    }
                    Button(
                        onClick = { onActionClick("reorder") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Order Again")
                    }
                }
            }
        }
    }
}

@Composable
fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun DetailRow(icon: ImageVector?, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Text(value, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
    }
}
