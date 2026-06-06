package im.angry.openeuicc.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object R2WColor {
    val Primary = Color(0xFF0A66FF)
    val PrimaryDark = Color(0xFF0047CC)
    val Secondary = Color(0xFF1E88E5)
    val Success = Color(0xFF16A34A)
    val Warning = Color(0xFFF59E0B)
    val Danger = Color(0xFFDC2626)
    val Background = Color(0xFFF5F8FC)
    val Card = Color.White
    val Border = Color(0xFFE2E8F0)
    val TextPrimary = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF64748B)
    val BlueSoft = Color(0xFFEAF2FF)
}

object R2WSpacing {
    val Xxs = 4.dp
    val Xs = 8.dp
    val Sm = 12.dp
    val Md = 16.dp
    val Lg = 24.dp
    val Xl = 32.dp
    val Xxl = 40.dp
}

object R2WRadius {
    val Small = 12.dp
    val Medium = 16.dp
    val Large = 22.dp
    val Hero = 28.dp
}

private val R2WLightScheme = lightColorScheme(
    primary = R2WColor.Primary,
    onPrimary = Color.White,
    primaryContainer = R2WColor.BlueSoft,
    onPrimaryContainer = R2WColor.PrimaryDark,
    secondary = R2WColor.Secondary,
    background = R2WColor.Background,
    surface = R2WColor.Card,
    onSurface = R2WColor.TextPrimary,
    onSurfaceVariant = R2WColor.TextSecondary,
    error = R2WColor.Danger
)

@Composable
fun R2WTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = R2WLightScheme,
        content = content
    )
}

@Composable
fun R2WScreen(content: @Composable () -> Unit) {
    R2WTheme {
        Surface(color = R2WColor.Background) {
            content()
        }
    }
}

@Composable
fun R2WHeroCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val gradient = Brush.linearGradient(
        colors = listOf(R2WColor.Primary, R2WColor.Secondary, R2WColor.PrimaryDark)
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(R2WRadius.Hero))
            .background(gradient)
            .padding(R2WSpacing.Lg)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.9f)
        )
        Spacer(modifier = Modifier.height(R2WSpacing.Sm))
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(R2WSpacing.Sm))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.82f)
        )
    }
}

@Composable
fun R2WStatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    R2WCard(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = R2WColor.TextSecondary
        )
        Spacer(modifier = Modifier.height(R2WSpacing.Xs))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = R2WColor.TextPrimary,
            fontWeight = FontWeight.Bold
        )
        subtitle?.let {
            Spacer(modifier = Modifier.height(R2WSpacing.Xxs))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = R2WColor.TextSecondary
            )
        }
    }
}

@Composable
fun R2WActionCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    R2WCard(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = R2WColor.TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(R2WSpacing.Xs))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = R2WColor.TextSecondary
        )
    }
}

@Composable
fun R2WCard(
    modifier: Modifier = Modifier,
    radius: Dp = R2WRadius.Large,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(radius),
        colors = CardDefaults.cardColors(containerColor = R2WColor.Card),
        border = BorderStroke(1.dp, R2WColor.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(R2WSpacing.Md),
            content = { content() }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun R2WDesignSystemPreview() {
    R2WScreen {
        Column(
            modifier = Modifier.padding(R2WSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(R2WSpacing.Md)
        ) {
            R2WHeroCard(
                title = "Wallet Balance",
                value = "€12,480.00",
                subtitle = "Available for eSIM purchases and dealer allocation"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(R2WSpacing.Md)) {
                R2WStatCard(
                    title = "Monthly Sales",
                    value = "248",
                    subtitle = "+12%",
                    modifier = Modifier.weight(1f)
                )
                R2WStatCard(
                    title = "Active eSIMs",
                    value = "1,204",
                    subtitle = "Live",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(R2WSpacing.Md)) {
                R2WActionCard(
                    title = "Buy eSIM",
                    subtitle = "Browse packages",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(0.dp))
                R2WActionCard(
                    title = "Reports",
                    subtitle = "Revenue analytics",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
