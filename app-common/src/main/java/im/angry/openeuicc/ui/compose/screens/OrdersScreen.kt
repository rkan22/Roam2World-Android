@file:OptIn(ExperimentalMaterial3Api::class)

package im.angry.openeuicc.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.MobileOrder
import im.angry.openeuicc.ui.compose.components.R2WStatusBadge
import im.angry.openeuicc.ui.compose.theme.*

@Composable
fun OrdersScreen(
    orders: List<MobileOrder>,
    onBackClick: () -> Unit,
    onOrderClick: (MobileOrder) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Completed", "Pending", "Failed")

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Orders", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                edgePadding = 16.dp,
                divider = {},
                indicator = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Surface(
                                color = if (selectedTab == index) Primary else Color.White,
                                shape = RoundedCornerShape(12.dp),
                                border = if (selectedTab == index) null else androidx.compose.foundation.BorderStroke(1.dp, Border)
                            ) {
                                Text(
                                    text = title,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = if (selectedTab == index) Color.White else TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val filteredOrders = when (selectedTab) {
                    1 -> orders.filter { it.status?.lowercase() == "completed" || it.status?.lowercase() == "confirmed" }
                    2 -> orders.filter { it.status?.lowercase() == "pending" }
                    3 -> orders.filter { it.status?.lowercase() == "failed" }
                    else -> orders
                }

                items(filteredOrders) { order ->
                    OrderCard(order = order, onClick = { onOrderClick(order) })
                }
            }
        }
    }
}

@Composable
fun OrderCard(order: MobileOrder, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = order.customerName() ?: "Unknown Customer",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 14.sp
                )
                Text(
                    text = order.displayNumber(),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = order.createdAt ?: "",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = order.price ?: "$0.00",
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                R2WStatusBadge(
                    text = order.statusLabel() ?: "Unknown",
                    status = order.status ?: ""
                )
            }
        }
    }
}
