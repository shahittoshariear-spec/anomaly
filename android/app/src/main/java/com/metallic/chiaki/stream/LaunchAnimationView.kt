// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.stream

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.metallic.chiaki.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A short transition shown when a Remote Play session opens: thin cyan lines
 * converge on the Anomaly logo, the logo pulses, then the lines burst outward
 * and the whole overlay fades away to reveal the stream.
 */
class LaunchAnimationView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null
): View(context, attrs)
{
	private val density = resources.displayMetrics.density
	private val lineCount = 16

	private val accent = ContextCompat.getColor(context, R.color.accent)

	private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = accent
		style = Paint.Style.STROKE
		strokeWidth = 1.2f * density
		strokeCap = Paint.Cap.ROUND
	}
	private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = accent
		style = Paint.Style.FILL
	}
	private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		isFilterBitmap = true
	}
	private val logoRect = RectF()

	private var logo: Bitmap? = null
	private var progress = 0f
	private var started = false
	private var animator: ValueAnimator? = null
	private var finishedCallback: (() -> Unit)? = null

	init
	{
		val source = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_foreground)
			?: BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
		logo = source?.let { trimTransparent(it) }
		isClickable = false
		isFocusable = false
	}

	fun start(onFinished: () -> Unit)
	{
		if(started)
			return
		started = true
		finishedCallback = onFinished
		if(width > 0 && height > 0)
			begin()
		else
			post { begin() }
	}

	private fun begin()
	{
		if(animator != null || !isAttachedToWindow)
			return
		animator = ValueAnimator.ofFloat(0f, 1f).apply {
			duration = 2400L
			interpolator = LinearInterpolator()
			addUpdateListener {
				progress = it.animatedValue as Float
				alpha = 1f - smooth(clamp((progress - 0.62f) / 0.38f))
				invalidate()
			}
			addListener(object: AnimatorListenerAdapter()
			{
				override fun onAnimationEnd(animation: Animator)
				{
					visibility = GONE
					finishedCallback?.invoke()
					finishedCallback = null
				}
			})
			start()
		}
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int)
	{
		super.onSizeChanged(w, h, oldw, oldh)
		if(started)
			begin()
	}

	override fun onAttachedToWindow()
	{
		super.onAttachedToWindow()
		if(started)
			begin()
	}

	override fun onDetachedFromWindow()
	{
		animator?.cancel()
		animator = null
		super.onDetachedFromWindow()
	}

	override fun onDraw(canvas: Canvas)
	{
		super.onDraw(canvas)
		val logo = logo ?: return
		if(width == 0 || height == 0)
			return

		val cx = width / 2f
		val cy = height / 2f
		val base = min(width, height) * 0.34f
		val outerRadius = hypot(width.toFloat(), height.toFloat()) * 0.62f

		val converge = smooth(clamp(progress / 0.46f))
		val burst = smooth(clamp((progress - 0.54f) / 0.46f))
		val logoAlpha = smooth(clamp((progress - 0.08f) / 0.32f))

		// Soft glow behind the logo.
		glowPaint.alpha = (36 * logoAlpha).toInt()
		canvas.drawCircle(cx, cy, base * (0.72f + 0.06f * burst), glowPaint)
		glowPaint.alpha = (18 * logoAlpha).toInt()
		canvas.drawCircle(cx, cy, base * (0.92f + 0.10f * burst), glowPaint)

		// Converging / bursting lines.
		for(i in 0 until lineCount)
		{
			val angle = (i.toFloat() / lineCount) * 2f * PI.toFloat() +
					sin(i * 12.9898f) * 0.08f
			val outerX = cx + cos(angle) * outerRadius
			val outerY = cy + sin(angle) * outerRadius
			val innerRadius = base * 0.58f
			val innerX = cx + cos(angle) * innerRadius
			val innerY = cy + sin(angle) * innerRadius

			if(progress < 0.54f)
			{
				val endX = outerX + (innerX - outerX) * converge
				val endY = outerY + (innerY - outerY) * converge
				linePaint.alpha = (255 * (0.08f + 0.92f * converge)).toInt()
				canvas.drawLine(outerX, outerY, endX, endY, linePaint)
			}
			else
			{
				val endX = innerX + (outerX - innerX) * burst
				val endY = innerY + (outerY - innerY) * burst
				linePaint.alpha = (255 * (1f - burst)).toInt()
				canvas.drawLine(innerX, innerY, endX, endY, linePaint)
			}
		}

		// Logo.
		val logoScale = 0.86f + 0.14f * converge + 0.08f * burst
		val logoSize = base * logoScale
		val aspect = logo.width.toFloat() / logo.height.toFloat()
		val lw = if(aspect >= 1f) logoSize else logoSize * aspect
		val lh = if(aspect >= 1f) logoSize / aspect else logoSize
		logoRect.set(cx - lw / 2f, cy - lh / 2f, cx + lw / 2f, cy + lh / 2f)
		logoPaint.alpha = (255 * logoAlpha * (1f - burst * 0.85f)).toInt()
		canvas.drawBitmap(logo, null, logoRect, logoPaint)
	}

	private fun trimTransparent(source: Bitmap): Bitmap
	{
		val pixels = IntArray(source.width * source.height)
		source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
		var left = source.width
		var top = source.height
		var right = -1
		var bottom = -1
		for(y in 0 until source.height)
		{
			val row = y * source.width
			for(x in 0 until source.width)
			{
				if(Color.alpha(pixels[row + x]) > 8)
				{
					if(x < left) left = x
					if(x > right) right = x
					if(y < top) top = y
					if(y > bottom) bottom = y
				}
			}
		}
		if(right < left || bottom < top)
			return source
		return Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1)
	}

	private fun clamp(value: Float) = max(0f, min(1f, value))

	private fun smooth(value: Float) = value * value * (3f - 2f * value)
}
