package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class FloatingNotificationItem(
    val key: String,
    val title: String,
    val message: String,
    val type: String = "ALERT", // "ALERT", "PARSED", "BILL", "INFO", "TRANSACTION"
    val entityId: Int? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

private data class NotificationVisuals(
    val accentColor: Color,
    val containerColor: Color,
    val borderColor: Color,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val tagLabel: String
)

private fun getVisualsForType(type: String): NotificationVisuals {
    return when (type) {
        "ALERT" -> NotificationVisuals(
            accentColor = CrimsonRed,
            containerColor = Color(0xFF220A0E),
            borderColor = CrimsonRed.copy(alpha = 0.75f),
            icon = Icons.Default.Warning,
            iconBg = CrimsonBadgeBg,
            iconTint = CrimsonLight,
            tagLabel = "ALERT"
        )
        "PARSED", "TRANSACTION" -> NotificationVisuals(
            accentColor = EmeraldGreen,
            containerColor = Color(0xFF081C15),
            borderColor = EmeraldGreen.copy(alpha = 0.65f),
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            iconBg = EmeraldBadgeBg,
            iconTint = EmeraldLight,
            tagLabel = "UPI DETECTED"
        )
        "BILL" -> NotificationVisuals(
            accentColor = AmberWarning,
            containerColor = Color(0xFF241604),
            borderColor = AmberWarning.copy(alpha = 0.65f),
            icon = Icons.Default.CalendarToday,
            iconBg = AmberBadgeBg,
            iconTint = AmberLight,
            tagLabel = "DUE SOON"
        )
        else -> NotificationVisuals(
            accentColor = RoyalBlue,
            containerColor = Color(0xFF0D1832),
            borderColor = RoyalBlue.copy(alpha = 0.65f),
            icon = Icons.Default.Notifications,
            iconBg = RoyalBlueBadgeBg,
            iconTint = RoyalBlueLight,
            tagLabel = "INSIGHT"
        )
    }
}

@Composable
fun FloatingStackedNotificationOverlay(
    notifications: List<FloatingNotificationItem>,
    onDismiss: (FloatingNotificationItem) -> Unit,
    modifier: Modifier = Modifier,
    onAction: ((FloatingNotificationItem) -> Unit)? = null
) {
    if (notifications.isEmpty()) return

    val totalCount = notifications.size
    // Render up to 3 stacked cards (N1 top, N2 behind, N3 behind)
    val visibleItems = notifications.take(3)

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val dismissThreshold = with(density) { 85.dp.toPx() }
    val flingDistance = with(density) { 450.dp.toPx() }

    val topItem = visibleItems.firstOrNull()
    val topOffsetX = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }

    // When the top notification changes (promotion of next item), reset offset
    LaunchedEffect(topItem?.key) {
        topOffsetX.snapTo(0f)
        isDismissing = false
    }

    // Box containing stacked cards
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp), // Compact height ensuring balance card remains visible
        contentAlignment = Alignment.TopCenter
    ) {
        // Draw cards in reverse order so index 0 (top item) is rendered last and sits on top
        for (i in (visibleItems.size - 1) downTo 0) {
            val item = visibleItems[i]
            val isTop = (i == 0)

            val animatedYOffset by animateDpAsState(
                targetValue = (i * 7).dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "stack_y_offset_$i"
            )

            val animatedScale by animateFloatAsState(
                targetValue = 1f - (i * 0.04f),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "stack_scale_$i"
            )

            val animatedAlpha by animateFloatAsState(
                targetValue = 1f - (i * 0.18f),
                animationSpec = tween(durationMillis = 200),
                label = "stack_alpha_$i"
            )

            val visuals = getVisualsForType(item.type)

            val cardModifier = if (isTop) {
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(topOffsetX.value.roundToInt(), animatedYOffset.roundToPx()) }
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                        // Slight rotation based on drag offset for tactile feel
                        rotationZ = (topOffsetX.value / 40f).coerceIn(-7f, 7f)
                        // Dynamic alpha while dragging off
                        alpha = if (abs(topOffsetX.value) > 10f) {
                            (1f - (abs(topOffsetX.value) / (dismissThreshold * 2.2f))).coerceIn(0.15f, 1f)
                        } else {
                            animatedAlpha
                        }
                    }
                    .zIndex(10f)
                    .pointerInput(item.key) {
                        // Accidental dismissal prevention: detectHorizontalDragGestures only activates
                        // if horizontal drag exceeds touch slop before vertical movement does.
                        // Vertical dragging is allowed to pass smoothly to the underlying scroll container.
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val currentX = topOffsetX.value
                                if (abs(currentX) > dismissThreshold && !isDismissing) {
                                    isDismissing = true
                                    coroutineScope.launch {
                                        val targetX = if (currentX > 0) flingDistance else -flingDistance
                                        topOffsetX.animateTo(
                                            targetValue = targetX,
                                            animationSpec = tween(
                                                durationMillis = 180,
                                                easing = FastOutLinearInEasing
                                            )
                                        )
                                        onDismiss(item)
                                        isDismissing = false
                                        topOffsetX.snapTo(0f)
                                    }
                                } else if (!isDismissing) {
                                    coroutineScope.launch {
                                        topOffsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                if (!isDismissing) {
                                    coroutineScope.launch {
                                        topOffsetX.animateTo(0f, spring())
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (!isDismissing) {
                                    change.consume()
                                    coroutineScope.launch {
                                        topOffsetX.snapTo(topOffsetX.value + dragAmount)
                                    }
                                }
                            }
                        )
                    }
            } else {
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, animatedYOffset.roundToPx()) }
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                        alpha = animatedAlpha
                    }
                    .zIndex((10 - i).toFloat())
            }

            Surface(
                modifier = cardModifier
                    .shadow(
                        elevation = if (isTop) 8.dp else (4 - i).dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = visuals.accentColor.copy(alpha = 0.35f)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        BorderStroke(
                            1.dp,
                            if (isTop) visuals.borderColor else visuals.borderColor.copy(alpha = 0.4f)
                        ),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable(enabled = isTop && item.onAction != null) {
                        onAction?.invoke(item) ?: item.onAction?.invoke()
                    },
                color = visuals.containerColor,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Alert Type Icon
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(visuals.iconBg)
                            .border(1.dp, visuals.borderColor.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = visuals.icon,
                            contentDescription = visuals.tagLabel,
                            tint = visuals.iconTint,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Text & Details
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate50,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            // Tag label badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(visuals.iconBg)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = visuals.tagLabel,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = visuals.iconTint
                                )
                            }
                        }

                        Text(
                            text = item.message,
                            fontSize = 11.sp,
                            color = Slate300,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 1.dp)
                        )

                        if (isTop) {
                            Text(
                                text = "Swipe or tap ✕ to dismiss",
                                fontSize = 9.sp,
                                color = Slate500,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Right Side: Queue Counter ("1 of 3") and Close Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (totalCount > 1) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Slate850)
                                    .border(1.dp, Slate700, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "1 of $totalCount",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoLight
                                )
                            }
                        }

                        if (isTop) {
                            IconButton(
                                onClick = {
                                    if (!isDismissing) {
                                        isDismissing = true
                                        coroutineScope.launch {
                                            topOffsetX.animateTo(
                                                targetValue = flingDistance,
                                                animationSpec = tween(
                                                    durationMillis = 180,
                                                    easing = FastOutLinearInEasing
                                                )
                                            )
                                            onDismiss(item)
                                            isDismissing = false
                                            topOffsetX.snapTo(0f)
                                        }
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss notification",
                                    tint = Slate400,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
