package im.angry.openeuicc.ui.b2b.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.ui.b2b.theme.WalletGradientEnd
import im.angry.openeuicc.ui.b2b.theme.WalletGradientStart

@OptIn(Material3Api::class)
@Composable
fun WalletDetailScreen(onBackClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Wallet", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.horizontalGradient(listOf(WalletGradientStart, WalletGradientEnd)))
                            .padding(24.dp)
                    ) {
                        Column {
                            Text(text = "Current Balance", color = Color.White.copy(alpha = 0.8f))
                            Text(text = "$ 8,540.50", color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FinanceAction(icon = Icons.Default.Add, label = "Deposit")
                    FinanceAction(icon = Icons.Default.Send, label = "Withdraw")
                }
            }
            
            item {
                Text(text = "Transaction History", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            items(listOf("Turkey 10GB Purchase", "Balance Deposit", "Europe 5GB Purchase")) { title ->
                TransactionItem(title = title)
            }
        }
    }
}

@Composable
fun FinanceAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(text = label, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun TransactionItem(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = title, fontWeight = FontWeight.Medium)
            Text(text = "June 06, 2026", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        Text(
            text = if (title.contains("Deposit")) "+$500.00" else "-$18.50",
            fontWeight = FontWeight.Bold,
            color = if (title.contains("Deposit")) Color(0xFF4CAF50) else Color.Red
        )
    }
}
