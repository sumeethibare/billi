package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.model.Bill
import com.example.domain.CurrencyFormatter
import com.example.ui.theme.MoneyBodyStyle
import com.example.ui.theme.MoneyTitleStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact visual receipt strip displayed at the top of the Bills tab when receipts are attached.
 */
@Composable
fun ReceiptsGalleryStrip(
    receiptBills: List<Bill>,
    receiptCache: Map<String, Bitmap?>,
    onRequestLoadReceipt: (String) -> Unit,
    onOpenReceiptZoom: (Bill) -> Unit,
    onOpenFullGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (receiptBills.isEmpty()) return

    val feedback = LocalBilliFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("receipts_gallery_strip")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Receipts (${receiptBills.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            TextButton(
                onClick = {
                    feedback?.tick()
                    onOpenFullGallery()
                },
                modifier = Modifier.testTag("open_receipt_gallery_button")
            ) {
                Text("Gallery")
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(receiptBills, key = { it.id }) { bill ->
                LaunchedEffect(bill.id) {
                    if (!receiptCache.containsKey(bill.id)) {
                        onRequestLoadReceipt(bill.id)
                    }
                }
                val bmp = receiptCache[bill.id]
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .width(96.dp)
                        .height(112.dp)
                        .billiPressScale()
                        .clickable {
                            feedback?.tick()
                            onOpenReceiptZoom(bill)
                        }
                        .testTag("receipt_strip_item_${bill.id}")
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Receipt for ${bill.title}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            BilliShimmerBox(
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(16.dp)
                            )
                            Icon(
                                imageVector = Icons.Rounded.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(22.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                                    )
                                )
                                .padding(horizontal = 7.dp, vertical = 6.dp)
                        ) {
                            Column {
                                Text(
                                    text = bill.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = CurrencyFormatter.format(bill.amountCents),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFA7F3D0),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full 2-column visual archive of every receipt photo in the trip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptGallerySheet(
    receiptBills: List<Bill>,
    receiptCache: Map<String, Bitmap?>,
    onRequestLoadReceipt: (String) -> Unit,
    onSelectReceipt: (Bill) -> Unit,
    onDismiss: () -> Unit
) {
    val feedback = LocalBilliFeedback.current
    val dateFmt = remember { SimpleDateFormat("MMM d", Locale.US) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Trip Receipt Gallery",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${receiptBills.size} attached receipts • Tap any photo to pinch-zoom",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                items(receiptBills, key = { it.id }) { bill ->
                    LaunchedEffect(bill.id) {
                        if (!receiptCache.containsKey(bill.id)) {
                            onRequestLoadReceipt(bill.id)
                        }
                    }
                    val bmp = receiptCache[bill.id]
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .billiPressScale()
                            .clickable {
                                feedback?.tick()
                                onSelectReceipt(bill)
                            }
                            .testTag("gallery_receipt_card_${bill.id}")
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.15f)
                                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                            ) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Receipt photo for ${bill.title}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    BilliShimmerBox(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(0.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = bill.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = CurrencyFormatter.format(bill.amountCents),
                                        style = MoneyBodyStyle.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (bill.billDateMillis > 0L) {
                                        Text(
                                            text = dateFmt.format(Date(bill.billDateMillis)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-screen Pinch-to-Zoom, Double-Tap Zoom, and HorizontalPager Receipt Viewer.
 * Swiping left/right switches receipts when zoomed out (1x); zooming in unlocks bounded 2D pan.
 */
@Composable
fun ReceiptZoomViewerOverlay(
    receiptBills: List<Bill>,
    initialBillId: String,
    receiptCache: Map<String, Bitmap?>,
    onRequestLoadReceipt: (String) -> Unit,
    onClose: () -> Unit
) {
    if (receiptBills.isEmpty()) return

    val feedback = LocalBilliFeedback.current
    val initialIndex = remember(receiptBills, initialBillId) {
        receiptBills.indexOfFirst { it.id == initialBillId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialIndex) { receiptBills.size }
    var currentPageZoomed by remember { mutableStateOf(false) }

    val currentBill = receiptBills.getOrElse(pagerState.currentPage) { receiptBills.first() }
    val dateFmt = remember { SimpleDateFormat("EEE, MMM d • h:mm a", Locale.US) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("receipt_zoom_viewer")
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !currentPageZoomed,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val bill = receiptBills[page]
            LaunchedEffect(bill.id) {
                if (!receiptCache.containsKey(bill.id)) {
                    onRequestLoadReceipt(bill.id)
                }
            }
            val bitmap = receiptCache[bill.id]

            var targetScale by remember(bill.id) { mutableFloatStateOf(1f) }
            var offset by remember(bill.id) { mutableStateOf(Offset.Zero) }
            var containerSize by remember { mutableStateOf(IntSize.Zero) }

            val animatedScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = BilliMotion.snappySpring(LocalReduceMotion.current),
                label = "receipt_zoom_scale"
            )

            LaunchedEffect(pagerState.currentPage, targetScale) {
                if (pagerState.currentPage == page) {
                    currentPageZoomed = targetScale > 1.03f
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerSize = it }
                    .pointerInput(bill.id) {
                        detectTapGestures(
                            onDoubleTap = {
                                feedback?.tick()
                                if (targetScale > 1.1f) {
                                    targetScale = 1f
                                    offset = Offset.Zero
                                } else {
                                    targetScale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(bill.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (targetScale * zoom).coerceIn(1f, 4f)
                            targetScale = nextScale
                            if (nextScale <= 1.02f) {
                                offset = Offset.Zero
                            } else {
                                val maxX = (containerSize.width * (nextScale - 1f)) / 2f
                                val maxY = (containerSize.height * (nextScale - 1f)) / 2f
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Zoomable receipt for ${bill.title}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = animatedScale
                                scaleY = animatedScale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                } else {
                    BilliShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .aspectRatio(0.75f),
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                if (targetScale > 1.05f) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(end = 16.dp, bottom = 92.dp)
                            .clickable {
                                feedback?.tick()
                                targetScale = 1f
                                offset = Offset.Zero
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ZoomOutMap,
                                contentDescription = "Reset zoom",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${String.format(Locale.US, "%.1fx", targetScale)} • Reset",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Top Bar overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.14f)
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${receiptBills.size} • Pinch or double-tap to zoom",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            IconButton(
                onClick = {
                    feedback?.tick()
                    onClose()
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .testTag("close_receipt_zoom_button")
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close receipt viewer", tint = Color.White)
            }
        }

        // Bottom caption overlay
        Surface(
            color = Color.Black.copy(alpha = 0.78f),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentBill.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = buildList {
                        if (currentBill.locationName.isNotBlank()) add(currentBill.locationName)
                        if (currentBill.billDateMillis > 0L) add(dateFmt.format(Date(currentBill.billDateMillis)))
                    }.joinToString(" • ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = CurrencyFormatter.format(currentBill.amountCents),
                    style = MoneyTitleStyle,
                    color = Color(0xFFA7F3D0)
                )
            }
        }
    }
}
