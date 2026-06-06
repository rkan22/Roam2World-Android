package im.angry.openeuicc.ui.b2b.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import im.angry.openeuicc.ui.b2b.components.PackageCard
import im.angry.openeuicc.ui.b2b.components.SearchBar
import im.angry.openeuicc.ui.b2b.models.EsimPackage
import im.angry.openeuicc.ui.b2b.models.StoreState

@OptIn(Material3Api::class)
@Composable
fun StoreScreen(
    onBackClick: () -> Unit,
    onPackageClick: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    // Mock Data
    val mockPackages = listOf(
        EsimPackage("1", "Turkey 5GB", "5GB", "30 Days", 12.0, "USD", "Turkey", "TR", "Turkcell"),
        EsimPackage("2", "Europe 10GB", "10GB", "30 Days", 25.0, "USD", "Europe", "EU", "Orange"),
        EsimPackage("3", "USA 3GB", "3GB", "7 Days", 15.0, "USD", "USA", "US", "AT&T"),
        EsimPackage("4", "Global 20GB", "20GB", "365 Days", 85.0, "USD", "Global", "GL", "Multiple")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buy eSIM", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            SearchBar(query = searchQuery, onQueryChange = { searchQuery = it })
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Available Packages",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(mockPackages.filter { it.name.contains(searchQuery, ignoreCase = true) }) { esimPackage ->
                    PackageCard(esimPackage = esimPackage, onClick = { onPackageClick(esimPackage.id) })
                }
            }
        }
    }
}
