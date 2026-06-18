package im.angry.openeuicc.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.ui.compose.theme.Border
import im.angry.openeuicc.ui.compose.theme.CardWhite
import im.angry.openeuicc.ui.compose.theme.Danger
import im.angry.openeuicc.ui.compose.theme.DividerBlue
import im.angry.openeuicc.ui.compose.theme.Primary
import im.angry.openeuicc.ui.compose.theme.PrimaryDark
import im.angry.openeuicc.ui.compose.theme.PrimaryLight
import im.angry.openeuicc.ui.compose.theme.R2WColors
import im.angry.openeuicc.ui.compose.theme.Success
import im.angry.openeuicc.ui.compose.theme.TextPrimary
import im.angry.openeuicc.ui.compose.theme.TextSecondary
import im.angry.openeuicc.ui.compose.theme.Warning

@Composable
fun R2WCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun R2WPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Primary)
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun R2WSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun R2WHeroCard(
    title: String,
    amount: String,
    subtitle: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Primary, PrimaryDark)
                    )
                )
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.TopStart)) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = amount,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Text(text = subtitle, color = Primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun R2WWalletBalanceCard(
    balance: String,
    mainBalance: String,
    bonus: String,
    pending: String,
    onRechargeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Wallet Balance",
                        color = Color.White.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = balance,
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                WalletMiniStat("Main Balance", mainBalance)
                WalletMiniStat("Bonus", bonus)
                WalletMiniStat("Pending", pending)
            }

            Button(
                onClick = onRechargeClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text("Recharge", color = Primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WalletMiniStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun R2WStatCard(
    title: String,
    value: String,
    trend: String? = null,
    isPositive: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            if (trend != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = trend,
                    color = if (isPositive) Success else Warning,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun R2WActionCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun R2WQuickActionGrid(
    onAddEsim: () -> Unit,
    onRecharge: () -> Unit,
    onCheckGb: () -> Unit,
    onOpenEuicc: () -> Unit,
    onReports: () -> Unit,
    modifier: Modifier = Modifier
) {
    R2WCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Quick Actions", fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("Edit", color = Primary, fontWeight = FontWeight.SemiBold)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionItem("Add eSIM", Icons.Default.Add, onAddEsim)
            QuickActionItem("Recharge", Icons.Default.AccountBalanceWallet, onRecharge)
            QuickActionItem("Check GB", Icons.Default.BarChart, onCheckGb)
            QuickActionItem("OpenEUICC", Icons.Default.QrCodeScanner, onOpenEuicc)
            QuickActionItem("Reports", Icons.Default.Assessment, onReports)
        }
    }
}

@Composable
fun RowScope.QuickActionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(R2WColors.Sky)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = title, tint = Primary, modifier = Modifier.size(22.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
fun R2WProgressRow(
    title: String,
    value: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(value, color = Primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(DividerBlue)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Primary)
            )
        }
    }
}

@Composable
fun R2WStatusBadge(
    text: String,
    status: String,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor) = when (status.lowercase()) {
        "active", "completed", "success", "paid", "ready" -> Success.copy(alpha = 0.12f) to Success
        "pending", "warning", "review" -> Warning.copy(alpha = 0.12f) to Warning
        "failed", "danger", "error", "expired" -> Danger.copy(alpha = 0.12f) to Danger
        else -> TextSecondary.copy(alpha = 0.12f) to TextSecondary
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
