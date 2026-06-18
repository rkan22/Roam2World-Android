@file:OptIn(ExperimentalMaterial3Api::class)

package im.angry.openeuicc.ui.compose.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.MobileDashboardData
import im.angry.openeuicc.auth.MobileDashboardOrder
import java.text.NumberFormat
import java.util.Locale

private val B2BBlue = Color(0xFF1263F1)
private val B2BBlueDark = Color(0xFF0047D8)
private val B2BText = Color(0xFF111827)
private val B2BMuted = Color(0xFF6B7280)
private val B2BBorder = Color(0xFFE5E7EB)
private val B2BBg = Color(0xFFF8FAFF)
private val B2BGreen = Color(0xFF12A150)
private val B2BOrange = Color(0xFFF97316)
private val B2BTeal = Color(0xFF14B8A6)

@Composable
fun CompactDashboardScreen(
    userName: String,
    data: MobileDashboardData?,
    onWalletClick: () -> Unit,
    onActionClick: (String) -> Unit
) {
    Scaffold(containerColor = B2BBg) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { CompactHeader(userName) }
                item { CompactWallet(money(data?.currentBalance, "$2,450.50"), onWalletClick) { onActionClick("wallet") } }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompactMetric(Icons.Default.Assessment, B2BBlue, "Today Sales", money(data?.todaySales, "$1,234"), Modifier.weight(1f))
                            CompactMetric(Icons.Default.CalendarMonth, B2BBlue, "Monthly Sales", money(data?.monthlySales, "$15,678"), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompactMetric(Icons.Default.SimCard, B2BTeal, "Active eSIMs", data?.activeEsimCount ?: "245", Modifier.weight(1f))
                            CompactMetric(Icons.Default.Refresh, B2BOrange, "Expiring Soon", data?.expiredEsimCount ?: "12", Modifier.weight(1f))
                        }
                    }
                }
                item { CompactRecent(data?.recentOrders.orEmpty()) { onActionClick("orders") } }
                item { CompactActions(onActionClick) }
                item { Spacer(Modifier.height(72.dp)) }
            }
            CompactBottomNav(onActionClick)
        }
    }
}

@Composable
private fun CompactHeader(userName: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Roam", color = B2BText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("2", color = B2BTeal, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("World", color = B2BText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Box(Modifier.padding(start = 7.dp).clip(RoundedCornerShape(7.dp)).background(B2BBlue).padding(horizontal = 8.dp, vertical = 5.dp)) {
                    Text("B2B", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Welcome back, ${userName.ifBlank { "Admin" }}",
                color = B2BText,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Here's what's happening with your business today.",
                color = B2BMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = {}, modifier = Modifier.size(38.dp)) {
            Box {
                Icon(Icons.Default.Notifications, null, tint = B2BText, modifier = Modifier.size(24.dp))
                Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFFEF4444)))
            }
        }
    }
}

@Composable
private fun CompactWallet(balance: String, onAddFunds: () -> Unit, onTransactions: () -> Unit) {
    Card(Modifier.fillMaxWidth().height(122.dp), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(B2BBlue), elevation = CardDefaults.cardElevation(5.dp)) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(B2BBlue, B2BBlueDark))).padding(18.dp)) {
            Column(Modifier.align(Alignment.CenterStart)) {
                Text("Wallet Balance", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(balance, color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("≈ $13,482.56 USD", color = Color.White.copy(alpha = 0.86f), fontSize = 11.sp)
            }
            Surface(Modifier.align(Alignment.CenterEnd).clickable(onClick = onAddFunds), color = Color.White.copy(alpha = 0.92f), shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, null, tint = B2BBlue, modifier = Modifier.size(16.dp))
                    Text("Add Funds", color = B2BBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("View Transactions ›", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomEnd).clickable(onClick = onTransactions))
        }
    }
}

@Composable
private fun CompactMetric(icon: ImageVector, tint: Color, title: String, value: String, modifier: Modifier) {
    Card(modifier.height(86.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, B2BBorder), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(15.dp)).background(tint.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(title, color = B2BMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                Text(value, color = B2BText, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CompactRecent(orders: List<MobileDashboardOrder>, onViewAll: () -> Unit) {
    val order = orders.firstOrNull() ?: MobileDashboardOrder("1", "ORD-2025-0512-1021", "Global Connect LLC", "10 × Europe eSIM • May 25, 2025", "$200.00", "Completed")
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, B2BBorder), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Recent Purchases", color = B2BText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("View All", color = B2BBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onViewAll))
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, B2BBorder)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(999.dp)).background(B2BBlue.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Business, null, tint = B2BBlue, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(order.title, color = B2BText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(order.subtitle, color = B2BMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(order.amount, "$200.00"), color = B2BText, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        Text(order.status ?: "Completed", color = B2BGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactActions(onActionClick: (String) -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, B2BBorder), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Quick Actions", color = B2BText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactAction(Icons.Default.ShoppingCart, "Buy", { onActionClick("store") }, Modifier.weight(1f))
                CompactAction(Icons.Default.GridView, "EUICC", { onActionClick("openeuicc") }, Modifier.weight(1f))
                CompactAction(Icons.Default.ReceiptLong, "History", { onActionClick("orders") }, Modifier.weight(1f))
                CompactAction(Icons.Default.AccountBalanceWallet, "Wallet", { onActionClick("wallet") }, Modifier.weight(1f))
                CompactAction(Icons.Default.Refresh, "Top Up", { onActionClick("recharge") }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CompactAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(modifier.height(76.dp).clickable(onClick = onClick), color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, B2BBorder), shadowElevation = 1.dp) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = B2BBlue, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, color = B2BText, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun CompactBottomNav(onActionClick: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 8.dp, border = BorderStroke(1.dp, B2BBorder)) {
        Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 8.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            CompactBottomItem(Icons.Default.GridView, "Dashboard", true, {})
            CompactBottomItem(Icons.Default.SimCard, "eSIMs", false) { onActionClick("check_gb") }
            CompactBottomItem(Icons.Default.People, "Customers", false) { onActionClick("crm") }
            CompactBottomItem(Icons.Default.ReceiptLong, "Orders", false) { onActionClick("orders") }
            CompactBottomItem(Icons.Default.GridView, "More", false) { onActionClick("more") }
        }
    }
}

@Composable
private fun CompactBottomItem(icon: ImageVector, title: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (selected) B2BBlue else B2BMuted, modifier = Modifier.size(22.dp))
        Text(title, color = if (selected) B2BBlue else B2BMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

private fun money(value: String?, fallback: String): String {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return fallback
    if (raw.any { it == '$' || it == '€' || it == '₺' || it.isLetter() }) return raw
    val numeric = raw.replace(",", "").toDoubleOrNull() ?: return raw
    return NumberFormat.getCurrencyInstance(Locale.US).format(numeric)
}
