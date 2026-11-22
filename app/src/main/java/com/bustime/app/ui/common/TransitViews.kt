package com.bustime.app.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.bustime.app.R

/**
 * Boarding-pass ticket perforation divider:
 * Left and right semi-circular cutouts matching the screen background + a subtle dashed tear line.
 */
class TicketPerforationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.bgScreen)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = ContextCompat.getColor(context, R.color.divider)
    }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * density
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.cardStroke)
        alpha = 170
        pathEffect = DashPathEffect(floatArrayOf(5f * density, 5f * density), 0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cy = height / 2f
        val radius = 9.5f * density
        // Dashed tear line across the ticket
        canvas.drawLine(radius + 6f * density, cy, width - radius - 6f * density, cy, dashPaint)
        // Left & right semi-circular ticket cutouts
        canvas.drawCircle(0f, cy, radius, cutoutPaint)
        canvas.drawCircle(width.toFloat(), cy, radius, cutoutPaint)
        canvas.drawCircle(0f, cy, radius, borderPaint)
        canvas.drawCircle(width.toFloat(), cy, radius, borderPaint)
    }
}

/**
 * Vertical dotted route rail connecting From (Emerald glowing dot) to To (Vibrant Saffron glowing dot).
 */
class DottedRouteRailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val originColor = ContextCompat.getColor(context, R.color.colorTertiary)
    private val destColor = ContextCompat.getColor(context, R.color.colorAccent)

    private val originFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = originColor
    }
    private val originHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = withAlpha(originColor, 68)
    }
    private val destFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = destColor
    }
    private val destHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = withAlpha(destColor, 68)
    }
    private val railPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.cardStroke)
        pathEffect = DashPathEffect(floatArrayOf(2.5f * density, 5.5f * density), 0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val topY = 30f * density
        val bottomY = (height - 30f * density).coerceAtLeast(topY + 28f * density)
        // Dotted rail
        canvas.drawLine(cx, topY + 11f * density, cx, bottomY - 11f * density, railPaint)
        // Origin node (Glowing Emerald)
        canvas.drawCircle(cx, topY, 9.5f * density, originHalo)
        canvas.drawCircle(cx, topY, 4.5f * density, originFill)
        // Destination node (Glowing Saffron)
        canvas.drawCircle(cx, bottomY, 9.5f * density, destHalo)
        canvas.drawCircle(cx, bottomY, 4.5f * density, destFill)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}

/**
 * Horizontal dotted highway track with Saffron end-dots inside Available Buses cards.
 */
class DashedHighwayTrackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.cardStroke)
        pathEffect = DashPathEffect(floatArrayOf(2f * density, 5f * density), 0f)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.colorAccent)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cy = height / 2f
        val dotR = 3.5f * density
        val leftX = dotR + 2f * density
        val rightX = width - dotR - 2f * density
        if (rightX > leftX) {
            canvas.drawLine(leftX + dotR + 3f * density, cy, rightX - dotR - 3f * density, cy, linePaint)
            canvas.drawCircle(leftX, cy, dotR, dotPaint)
            canvas.drawCircle(rightX, cy, dotR, dotPaint)
        }
    }
}

/**
 * Vertical timeline node with glowing translucent outer halo and connecting top/bottom rail.
 */
class GlowingStopNodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private var nodeColor: Int = ContextCompat.getColor(context, R.color.stopNodeMid)
    private var showTopLine: Boolean = true
    private var showBottomLine: Boolean = true
    private var isEndpoint: Boolean = false

    private val railPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        color = ContextCompat.getColor(context, R.color.timelineRail)
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun configure(color: Int, hasTop: Boolean, hasBottom: Boolean, endpoint: Boolean) {
        nodeColor = color
        showTopLine = hasTop
        showBottomLine = hasBottom
        isEndpoint = endpoint
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = 22f * density
        val haloR = if (isEndpoint) 10f * density else 7.5f * density
        val coreR = if (isEndpoint) 4.8f * density else 3.6f * density

        if (showTopLine) {
            canvas.drawLine(cx, 0f, cx, cy - haloR * 0.6f, railPaint)
        }
        if (showBottomLine) {
            canvas.drawLine(cx, cy + haloR * 0.6f, cx, height.toFloat(), railPaint)
        }

        haloPaint.color = Color.argb(66, Color.red(nodeColor), Color.green(nodeColor), Color.blue(nodeColor))
        corePaint.color = nodeColor
        canvas.drawCircle(cx, cy, haloR, haloPaint)
        canvas.drawCircle(cx, cy, coreR, corePaint)
    }
}

/**
 * Subtle geometric concentric arcs backdrop for the Bus Stand Spotlight Card.
 */
class SpotlightArcBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = ContextCompat.getColor(context, R.color.colorPrimaryContainer)
    }
    private val accentArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = ContextCompat.getColor(context, R.color.colorSecondaryContainer)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width.toFloat() - 12f * density
        val cy = 12f * density
        canvas.drawCircle(cx, cy, 42f * density, accentArcPaint)
        canvas.drawCircle(cx, cy, 74f * density, arcPaint)
        canvas.drawCircle(cx, cy, 108f * density, arcPaint)
    }
}

/**
 * Springy 0.97x bouncy press feedback on touch down + spring overshoot release.
 */
@SuppressLint("ClickableViewAccessibility")
fun View.applyBouncyPress() {
    setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(220)
                    .setInterpolator(OvershootInterpolator(2.2f)).start()
            }
        }
        false
    }
}

/** Pure UI display helper to format duration between two HH:mm timestamps. */
object TransitDurationFormatter {
    fun formatBetween(fromHHmm: String?, toHHmm: String?): String? {
        val start = parseMinutes(fromHHmm) ?: return null
        val end = parseMinutes(toHHmm) ?: return null
        var diff = end - start
        if (diff <= 0) diff += 24 * 60
        if (diff <= 0 || diff > 20 * 60) return null
        val hours = diff / 60
        val mins = diff % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    private fun parseMinutes(hhmm: String?): Int? {
        val clean = hhmm?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val parts = clean.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }
}
