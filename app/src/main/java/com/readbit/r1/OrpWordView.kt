package com.readbit.r1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlin.math.max

class OrpWordView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val wordTextSizePx =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 28f, resources.displayMetrics)
    private val basePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.rb_text)
            textSize = wordTextSizePx
            typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_regular)
            textAlign = Paint.Align.LEFT
        }
    private val accentPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.rb_accent)
            textSize = basePaint.textSize
            typeface = basePaint.typeface
            textAlign = Paint.Align.LEFT
        }

    private var word: String = ""

    fun setWord(value: String) {
        word = value
        contentDescription = value
        invalidate()
    }

    fun fitsWord(candidate: String, marginPx: Float = 10f * resources.displayMetrics.density): Boolean {
        if (candidate.isBlank()) return true
        val availableWidth = width.takeIf { it > 0 }?.toFloat() ?: resources.displayMetrics.widthPixels.toFloat()
        if (basePaint.measureText(candidate) <= availableWidth - paddingLeft - paddingRight - marginPx * 2f) {
            return true
        }
        val halfWidth = (resources.displayMetrics.widthPixels - paddingLeft - paddingRight) / 2f - marginPx
        return orpExtents(candidate).let { max(it.first, it.second) <= halfWidth }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = max(suggestedMinimumHeight, (basePaint.textSize * 1.5f).toInt() + paddingTop + paddingBottom)
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (word.isBlank()) return
        val centerX = width / 2f
        val baseline = height / 2f - (basePaint.descent() + basePaint.ascent()) / 2f
        val layout = OrpLayoutEngine.layout(word) { text -> basePaint.measureText(text) }
        val leftWidth = basePaint.measureText(layout.leftSegment)
        val pivotWidth = accentPaint.measureText(layout.pivot)
        val totalWidth = basePaint.measureText(word)
        val idealStartX = centerX - leftWidth - pivotWidth / 2f
        val horizontalMargin = 8f * resources.displayMetrics.density
        val minStartX = paddingLeft + horizontalMargin
        val maxStartX = max(minStartX, width - paddingRight - horizontalMargin - totalWidth)
        val startX = idealStartX.coerceIn(minStartX, maxStartX)
        if (layout.leftSegment.isNotEmpty()) {
            canvas.drawText(layout.leftSegment, startX, baseline, basePaint)
        }
        canvas.drawText(layout.pivot, startX + leftWidth, baseline, accentPaint)
        if (layout.rightSegment.isNotEmpty()) {
            canvas.drawText(layout.rightSegment, startX + leftWidth + pivotWidth, baseline, basePaint)
        }
    }

    private fun orpExtents(candidate: String): Pair<Float, Float> {
        val layout = OrpLayoutEngine.layout(candidate) { text -> basePaint.measureText(text) }
        val pivotWidth = basePaint.measureText(layout.pivot)
        val left = basePaint.measureText(layout.leftSegment) + pivotWidth / 2f
        val right = basePaint.measureText(layout.rightSegment) + pivotWidth / 2f
        return left to right
    }
}
