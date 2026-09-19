package io.github.jiangyuyi.lightnovel.feature.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.text.format.DateFormat
import android.view.RoundedCorner
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Date

/** A measured footer outside the reading viewport, so pagination cannot cover it. */
@Composable
internal fun ReaderStatusFooter(
    current: Int,
    total: Int,
    paged: Boolean,
    textColor: Color,
    horizontalPadding: Int,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    var time by remember { mutableStateOf(DateFormat.getTimeFormat(context).format(Date())) }
    var battery by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    var cornerRadiusPx by remember { mutableIntStateOf(0) }

    DisposableEffect(context, view, lifecycleOwner) {
        fun updateTime() { time = DateFormat.getTimeFormat(context).format(Date()) }
        fun updateBattery(intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            battery = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else -1
            charging = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        }
        fun updateCorners() {
            cornerRadiusPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                maxOf(
                    view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0,
                    view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0,
                )
            } else 0
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) updateBattery(intent)
                updateTime()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        var registered = false
        fun start() {
            updateTime()
            updateCorners()
            if (!registered) {
                val sticky = ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                registered = true
                updateBattery(sticky)
            }
        }
        fun stop() {
            if (registered) {
                context.unregisterReceiver(receiver)
                registered = false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> { updateTime(); updateCorners() }
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateCorners() }
        view.addOnLayoutChangeListener(layoutListener)
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose {
            stop()
            view.removeOnLayoutChangeListener(layoutListener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val cornerInset = with(density) { cornerRadiusPx.toDp() }
    val sideInset = maxOf(28.dp, horizontalPadding.dp, cornerInset + 4.dp)
    val bottomInset = maxOf(20.dp, WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding())
    val color = textColor.copy(alpha = 0.6f)
    Row(
        Modifier.fillMaxWidth().padding(start = sideInset, end = sideInset, top = 8.dp, bottom = bottomInset),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(
                Modifier.size(20.dp, 10.dp).semantics {
                    contentDescription = if (battery >= 0) "电量 $battery%${if (charging) "，充电中" else ""}" else "电量未知"
                },
            ) {
                val stroke = 1.dp.toPx()
                val bodyWidth = size.width - 2.dp.toPx()
                drawRoundRect(color, Offset(stroke / 2, stroke / 2), Size(bodyWidth - stroke, size.height - stroke), CornerRadius(stroke), style = Stroke(stroke))
                drawRect(color, Offset(bodyWidth, size.height * 0.3f), Size(2.dp.toPx(), size.height * 0.4f))
                if (battery > 0) drawRect(color, Offset(2 * stroke, 2 * stroke), Size((bodyWidth - 4 * stroke) * battery / 100f, size.height - 4 * stroke))
            }
            Text(if (battery >= 0) "$battery%${if (charging) "+" else ""}" else "--%", color = color, fontSize = 11.sp)
            Text(time, color = color, fontSize = 11.sp)
        }
        Text(
            text = "${current.coerceIn(1, total.coerceAtLeast(1))} / ${total.coerceAtLeast(1)}${if (paged) "" else " 段"}",
            color = color,
            fontSize = 11.sp,
            modifier = Modifier.semantics {
                contentDescription = if (paged) "本章第 $current 页，共 $total 页" else "本章第 $current 段，共 $total 段"
            },
        )
    }
}
