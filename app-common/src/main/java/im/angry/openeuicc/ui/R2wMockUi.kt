package im.angry.openeuicc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

object R2wMockColors {
    val Background = Color(0xFFF4F7FB)
    val Primary = Color(0xFF2563EB)
    val PrimaryDark = Color(0xFF133F9F)
    val Accent = Color(0xFF06B6D4)
    val Text = Color(0xFF111827)
    val Muted = Color(0xFF6B7280)
    val Border = Color(0xFFE5E7EB)
    val Success = Color(0xFF16A34A)
    val Danger = Color(0xFFDC2626)
    val Warning = Color(0xFFF59E0B)
}

@Composable
fun R2wMockScreen(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    bottomTab: R2wBottomTab? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = R2wMockColors.Background) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = if (bottomTab == null) 24.dp else 116.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    R2wMockHeader(title = title, subtitle = subtitle, onBack = onBack)
                    content()
                    Spacer(modifier = Modifier.height(8.dp))
                }
                bottomTab?.let {
                    R2wBottomNav(selected = it, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

@Composable
fun R2wMockHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = R2wMockColors.Text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(subtitle, color = R2wMockColors.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, R2wMockColors.Border, RoundedCornerShape(14.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) { Text("Back", color = R2wMockColors.Primary, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun R2wMockHero(
    eyebrow: String,
    title: String,
    body: String,
    amount: String? = null,
    badge: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(R2wMockColors.Primary, R2wMockColors.PrimaryDark)))
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(eyebrow.uppercase(), color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        Text(body, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium)
                    }
                    badge?.let { R2wMockBadge(text = it, background = Color.White.copy(alpha = 0.18f), color = Color.White) }
                }
                amount?.let {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Available balance", color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.labelMedium)
                        Text(it, color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun R2wMockCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = R2wMockColors.Text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            HorizontalDivider(color = R2wMockColors.Border)
            content()
        }
    }
}

@Composable
fun R2wMockLine(label: String, value: String, strong: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = R2wMockColors.Muted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = R2wMockColors.Text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (strong) FontWeight.Black else FontWeight.SemiBold)
    }
}

@Composable
fun R2wMockBadge(text: String, background: Color = Color(0xFFEFF6FF), color: Color = R2wMockColors.Primary) {
    Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(background).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
    }
}

@Composable
fun R2wMockPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = R2wMockColors.Primary)
    ) { Text(text, fontWeight = FontWeight.Black) }
}

@Composable
fun R2wMockSecondaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(18.dp)
    ) { Text(text, fontWeight = FontWeight.Bold, color = R2wMockColors.Primary) }
}

@Composable
fun R2wMockTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    error: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        supportingText = { error?.let { Text(it) } },
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = R2wMockColors.Primary,
            focusedLabelColor = R2wMockColors.Primary,
            unfocusedBorderColor = R2wMockColors.Border
        )
    )
}

@Composable
fun R2wMockStep(number: String, title: String, body: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(34.dp).background(Color(0xFFEFF6FF), CircleShape), contentAlignment = Alignment.Center) {
            Text(number, color = R2wMockColors.Primary, fontWeight = FontWeight.Black)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = R2wMockColors.Text, fontWeight = FontWeight.Black)
            Text(body, color = R2wMockColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun R2wMockChoice(label: String, detail: String? = null, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) R2wMockColors.Primary else R2wMockColors.Border
    val bg = if (selected) Color(0xFFEFF6FF) else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = R2wMockColors.Text, fontWeight = FontWeight.Black)
            detail?.let { Text(it, color = R2wMockColors.Muted, style = MaterialTheme.typography.bodySmall) }
        }
        Spacer(Modifier.width(10.dp))
        R2wMockBadge(if (selected) "Selected" else "Select")
    }
}
