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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.ui.b2b.theme.SuccessGreen

@OptIn(Material3Api::class)
@Composable
fun EsimInventoryScreen(
    onBackClick: () -> Unit,
    onEsimClick: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My eSIMs", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    text = "Active Subscriptions",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(listOf("Turkey 10GB", "Europe 20GB")) { name ->
                EsimItem(name = name, status = "Active", onClick = { onEsimClick("123") })
            }
            
            item {
                Text(
                    text = "Expired",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    color = Color.Gray
                )
            }
            
            items(listOf("USA 3GB")) { name ->
                EsimItem(name = name, status = "Expired", onClick = { onEsimClick("456") })
            }
        }
    }
}

@Composable
fun EsimItem(name: String, status: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(80.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = name, fontWeight = FontWeight.Bold)
                Text(text = "ICCID: 890123...", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            StatusBadge(status = status)
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val color = if (status == "Active") SuccessGreen else Color.Gray
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(Material3Api::class)
@Composable
fun OpenEuiccManagerScreen(onBackClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Manager", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(20.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "eUICC Info", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "EID: 89049032...", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "OS Version: 2.3.1", style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(text = "Installed Profiles", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            
            ProfileItem(name = "Roam2World eSIM", active = true)
            ProfileItem(name = "Personal SIM", active = false)
        }
    }
}

@Composable
fun ProfileItem(name: String, active: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = name)
        Switch(checked = active, onCheckedChange = {})
    }
}
