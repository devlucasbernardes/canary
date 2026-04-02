package com.canary.devicecare.ui.storage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.canary.devicecare.R
import com.canary.devicecare.ui.theme.ChartApps
import com.canary.devicecare.ui.theme.ChartCache
import com.canary.devicecare.ui.theme.ChartFree
import com.canary.devicecare.ui.theme.ChartMedia
import com.canary.devicecare.ui.theme.ChartSystem
import com.canary.devicecare.util.FormatUtils
import com.canary.devicecare.viewmodel.StorageViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalyzerScreen(viewModel: StorageViewModel = viewModel()) {
    val storageInfo by viewModel.storageInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.storage_analyzer_title)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { viewModel.analyzeStorage() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Total usage summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(
                                R.string.storage_used_of_total,
                                FormatUtils.formatBytes(storageInfo.usedBytes),
                                FormatUtils.formatBytes(storageInfo.totalBytes)
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(Modifier.height(8.dp))

                        val usedPercent = if (storageInfo.totalBytes > 0)
                            storageInfo.usedBytes.toFloat() / storageInfo.totalBytes else 0f

                        LinearProgressIndicator(
                            progress = usedPercent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(MaterialTheme.shapes.small),
                        )
                    }
                }

                // Donut chart
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val total = storageInfo.totalBytes.toFloat().coerceAtLeast(1f)

                        val appAngle by animateFloatAsState(
                            targetValue = (storageInfo.appBytes / total) * 360f,
                            animationSpec = tween(1000), label = "app"
                        )
                        val mediaAngle by animateFloatAsState(
                            targetValue = (storageInfo.mediaBytes / total) * 360f,
                            animationSpec = tween(1000), label = "media"
                        )
                        val cacheAngle by animateFloatAsState(
                            targetValue = (storageInfo.cacheBytes / total) * 360f,
                            animationSpec = tween(1000), label = "cache"
                        )
                        val systemAngle by animateFloatAsState(
                            targetValue = (storageInfo.systemBytes / total) * 360f,
                            animationSpec = tween(1000), label = "system"
                        )
                        val freeAngle by animateFloatAsState(
                            targetValue = (storageInfo.freeBytes / total) * 360f,
                            animationSpec = tween(1000), label = "free"
                        )

                        Canvas(
                            modifier = Modifier.size(200.dp)
                        ) {
                            val strokeWidth = 40f
                            val radius = (size.minDimension - strokeWidth) / 2
                            val topLeft = Offset(
                                (size.width - radius * 2) / 2,
                                (size.height - radius * 2) / 2
                            )
                            val arcSize = Size(radius * 2, radius * 2)

                            var startAngle = -90f

                            // Apps
                            drawArc(
                                color = ChartApps,
                                startAngle = startAngle,
                                sweepAngle = appAngle,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            startAngle += appAngle

                            // Media
                            drawArc(
                                color = ChartMedia,
                                startAngle = startAngle,
                                sweepAngle = mediaAngle,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            startAngle += mediaAngle

                            // Cache
                            drawArc(
                                color = ChartCache,
                                startAngle = startAngle,
                                sweepAngle = cacheAngle,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            startAngle += cacheAngle

                            // System
                            drawArc(
                                color = ChartSystem,
                                startAngle = startAngle,
                                sweepAngle = systemAngle,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            startAngle += systemAngle

                            // Free
                            drawArc(
                                color = ChartFree,
                                startAngle = startAngle,
                                sweepAngle = freeAngle,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        // Legend
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StorageLegendItem(
                                color = ChartApps,
                                label = stringResource(R.string.storage_apps),
                                size = FormatUtils.formatBytes(storageInfo.appBytes)
                            )
                            StorageLegendItem(
                                color = ChartMedia,
                                label = stringResource(R.string.storage_media),
                                size = FormatUtils.formatBytes(storageInfo.mediaBytes)
                            )
                            StorageLegendItem(
                                color = ChartCache,
                                label = stringResource(R.string.storage_cache),
                                size = FormatUtils.formatBytes(storageInfo.cacheBytes)
                            )
                            StorageLegendItem(
                                color = ChartSystem,
                                label = stringResource(R.string.storage_system),
                                size = FormatUtils.formatBytes(storageInfo.systemBytes)
                            )
                            StorageLegendItem(
                                color = ChartFree,
                                label = stringResource(R.string.storage_free),
                                size = FormatUtils.formatBytes(storageInfo.freeBytes)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun StorageLegendItem(color: Color, label: String, size: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(16.dp),
            shape = CircleShape,
            color = color
        ) {}

        Spacer(Modifier.width(12.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = size,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
