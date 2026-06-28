package im.angry.openeuicc.ui.compose.saas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object R2wSaasColors {
    val Background = Color(0xFFF7F9FC)
    val Card = Color.White
    val Primary = Color(0xFF1263F1)
    val PrimarySoft = Color(0xFFEAF2FF)
    val Text = Color(0xFF101828)
    val Muted = Color(0xFF667085)
    val Border = Color(0xFFE4EAF2)
    val Green = Color(0xFF16A34A)
    val Orange = Color(0xFFF97316)
    val Red = Color(0xFFDC2626)
    val Purple = Color(0xFF7C3AED)
}

enum class R2wSaasNavItem {
    Dashboard,
    Partners,
    Orders,
    Pricing,
    More
}

@Composable
fun R2wSaasCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 5.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.06f)
            ),
        color = R2wSaasColors.Card,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, R2wSaasColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

@Composable
fun R2wSaasHeader(
    title: String,
    subtitle: String,
    badge: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = R2wSaasColors.Text,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    color = R2wSaasColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!badge.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = R2wSaasColors.PrimarySoft,
                    border = BorderStroke(1.dp, R2wSaasColors.Border)
                ) {
                    Text(
                        text = badge,
                        color = R2wSaasColors.Primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun R2wMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color = R2wSaasColors.Primary
) {
    Surface(
        modifier = modifier
            .shadow(
                elevation = 5.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.05f)
            ),
        color = R2wSaasColors.Card,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, R2wSaasColors.Border)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = tint.copy(alpha = 0.10f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = tint.copy(alpha = 0.08f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = title,
                color = R2wSaasColors.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = value,
                color = R2wSaasColors.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = subtitle,
                color = tint,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun R2wActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = R2wSaasColors.Primary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = R2wSaasColors.Card,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, R2wSaasColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = tint.copy(alpha = 0.10f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(18.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 11.dp)
            ) {
                Text(
                    text = title,
                    color = R2wSaasColors.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(3.dp))

                Text(
                    text = subtitle,
                    color = R2wSaasColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    lineHeight = 16.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "›",
                color = R2wSaasColors.Muted,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun R2wSaasBottomNav(
    selected: R2wSaasNavItem,
    onClick: (R2wSaasNavItem) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color.Black.copy(alpha = 0.07f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            ),
        color = R2wSaasColors.Card,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, R2wSaasColors.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            R2wBottomItem(
                item = R2wSaasNavItem.Dashboard,
                label = "Dashboard",
                icon = Icons.Default.GridView,
                selected = selected == R2wSaasNavItem.Dashboard,
                onClick = onClick
            )

            R2wBottomItem(
                item = R2wSaasNavItem.Partners,
                label = "Partners",
                icon = Icons.Default.People,
                selected = selected == R2wSaasNavItem.Partners,
                onClick = onClick
            )

            R2wBottomItem(
                item = R2wSaasNavItem.Orders,
                label = "Orders",
                icon = Icons.Default.ShoppingCart,
                selected = selected == R2wSaasNavItem.Orders,
                onClick = onClick
            )

            R2wBottomItem(
                item = R2wSaasNavItem.Pricing,
                label = "Pricing",
                icon = Icons.Default.CreditCard,
                selected = selected == R2wSaasNavItem.Pricing,
                onClick = onClick
            )

            R2wBottomItem(
                item = R2wSaasNavItem.More,
                label = "More",
                icon = Icons.Default.MoreHoriz,
                selected = selected == R2wSaasNavItem.More,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun R2wBottomItem(
    item: R2wSaasNavItem,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: (R2wSaasNavItem) -> Unit
) {
    Column(
        modifier = Modifier
            .clickable { onClick(item) }
            .padding(horizontal = 5.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) R2wSaasColors.PrimarySoft else Color.Transparent
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) R2wSaasColors.Primary else R2wSaasColors.Muted,
                modifier = Modifier
                    .padding(7.dp)
                    .size(18.dp)
            )
        }

        Spacer(Modifier.height(2.dp))

        Text(
            text = label,
            color = if (selected) R2wSaasColors.Primary else R2wSaasColors.Muted,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
            maxLines = 1
        )
    }
}
