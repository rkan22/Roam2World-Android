package im.angry.openeuicc.ui.b2b.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import im.angry.openeuicc.ui.b2b.components.*
import im.angry.openeuicc.ui.b2b.models.DashboardEvent
import im.angry.openeuicc.ui.b2b.theme.SuccessGreen

@OptIn(Material3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Hello, ${uiState.adminName} 👋",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Welcome back to your wallet",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                WalletBalanceCard(
                    balance = uiState.walletBalance,
                    currency = uiState.currency,
                    onAddMoneyClick = { viewModel.onEvent(DashboardEvent.OnQuickActionClick("Add Money")) }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    QuickActionItem(
                        title = "Buy eSIM",
                        icon = Icons.Default.ShoppingCart,
                        containerColor = MaterialTheme.colorScheme.primary,
                        onClick = { viewModel.onEvent(DashboardEvent.OnQuickActionClick("Buy eSIM")) }
                    )
                    QuickActionItem(
                        title = "OpenEUICC",
                        icon = Icons.Default.Settings,
                        containerColor = SuccessGreen,
                        onClick = { viewModel.onEvent(DashboardEvent.OnQuickActionClick("OpenEUICC")) }
                    )
                    QuickActionItem(
                        title = "Wallet",
                        icon = Icons.Default.AccountBalanceWallet,
                        containerColor = Color(0xFFFB8C00),
                        onClick = { viewModel.onEvent(DashboardEvent.OnQuickActionClick("Wallet")) }
                    )
                    QuickActionItem(
                        title = "History",
                        icon = Icons.Default.History,
                        containerColor = Color(0xFF7B1FA2),
                        onClick = { viewModel.onEvent(DashboardEvent.OnQuickActionClick("History")) }
                    )
                }
            }

            item {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            items(uiState.recentPurchases) { purchase ->
                // Transaction item UI
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = purchase.customerName, fontWeight = FontWeight.Bold)
                            Text(text = purchase.packageName, style = MaterialTheme.typography.labelMedium)
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text(
                                text = "-${purchase.currency} ${purchase.amount}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(text = purchase.date, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
