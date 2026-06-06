package im.angry.openeuicc.ui.b2b.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.ui.b2b.components.DetailInfoCard
import im.angry.openeuicc.ui.b2b.components.FeatureItem
import im.angry.openeuicc.ui.b2b.models.EsimPackage
import im.angry.openeuicc.ui.b2b.theme.WalletGradientEnd
import im.angry.openeuicc.ui.b2b.theme.WalletGradientStart

@OptIn(Material3Api::class)
@Composable
fun PackageDetailScreen(
    packageId: String,
    onBackClick: () -> Unit,
    onBuyClick: () -> Unit
) {
    // Mock Data for the specific package
    val esimPackage = EsimPackage(
        id = packageId,
        name = "Turkey Premium 10GB",
        dataAmount = "10GB",
        validity = "30 Days",
        price = 18.50,
        currency = "USD",
        country = "Turkey",
        countryCode = "TR",
        operatorName = "Turkcell",
        features = listOf("High Speed 4G/5G", "Instant Activation", "No Roaming Fees", "24/7 Support"),
        coverageCountries = listOf("Turkey")
    )

    Scaffold(
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 16.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterHorizontally,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Total Price", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "${esimPackage.currency} ${String.format("%.2f", esimPackage.price)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Button(
                        onClick = onBuyClick,
                        modifier = Modifier
                            .height(56.dp)
                            .width(160.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Buy Now", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(WalletGradientStart, WalletGradientEnd)
                            )
                        )
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(16.dp)
                            .statusBarsPadding()
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(24.dp)
                    ) {
                        Text(
                            text = esimPackage.country,
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = esimPackage.name,
                            color = Color.White,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DetailInfoCard(label = "Data", value = esimPackage.dataAmount, modifier = Modifier.weight(1f))
                    DetailInfoCard(label = "Validity", value = esimPackage.validity, modifier = Modifier.weight(1f))
                    DetailInfoCard(label = "Operator", value = esimPackage.operatorName, modifier = Modifier.weight(1f))
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), thickness = 1.dp, color = Color.LightGray.copy(alpha = 0.5f))
            }

            item {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Package Features",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    esimPackage.features.forEach { feature ->
                        FeatureItem(text = feature)
                    }
                }
            }
        }
    }
}
