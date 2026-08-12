package com.readbit.r1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlin.math.max

class PreviewBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val fontScale = resources.displayMetrics.density
    private val selectedWordPaddingPx = PreviewLineMetrics.selectedWordPaddingPx(fontScale)
    private val horizontalSafetyPx = PreviewLineMetrics.horizontalSafetyPx(fontScale)
    private val verticalSafetyPx = 0f // Removed to make chunks sit flush
    private val trackedTypeface = ResourcesCompat.getFont(context, R.font.space_grotesk_regular)
    private val accentColor = ContextCompat.getColor(context, R.color.rb_accent)
    private val textPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.rb_text)
            textSize = 16f * fontScale
            typeface = trackedTypeface
        }
    private val subtlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.rb_surface_alt)
        }

    private var words: List<String> = emptyList()
    private var wordRanges: List<IntRange> = emptyList()
    private var lineStartIndex: Int = 0
    private var selectedIndex: Int = -1
    private var listener: ((Int) -> Unit)? = null
    private var layoutText: CharSequence = ""
    private var staticLayout: StaticLayout? = null

    fun bind(
        lineWords: List<String>,
        lineStartIndex: Int,
        selectedIndex: Int,
        onWordSelected: (Int) -> Unit,
    ) {
        this.words = lineWords
        this.lineStartIndex = lineStartIndex
        this.selectedIndex = selectedIndex
        listener = onWordSelected
        rebuildText()
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val availableTextWidth =
            (width - paddingLeft - paddingRight - horizontalSafetyPx * 2f).toInt().coerceAtLeast(1)
        staticLayout = buildLayout(availableTextWidth)
        val desiredHeight =
            (paddingTop + paddingBottom + (staticLayout?.height ?: 0)).toInt()
        setMeasuredDimension(width, resolveSize(max(desiredHeight, suggestedMinimumHeight), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layout = staticLayout ?: return
        if (words.isEmpty()) return

        val textLeft = paddingLeft.toFloat() + horizontalSafetyPx
        val textTop = paddingTop.toFloat()
        val selectedLocalIndex = selectedIndex - lineStartIndex

        canvas.save()
        canvas.translate(textLeft, textTop)
        if (selectedLocalIndex in wordRanges.indices) {
            val range = wordRanges[selectedLocalIndex]
            val start = range.first
            val endExclusive = range.last + 1
            val startLine = layout.getLineForOffset(start)
            val endLine = layout.getLineForOffset((endExclusive - 1).coerceAtLeast(start))
            if (startLine == endLine) {
                val left = layout.getPrimaryHorizontal(start)
                val right = layout.getPrimaryHorizontal(endExclusive)
                val top = layout.getLineTop(startLine).toFloat()
                val bottom = layout.getLineBottom(startLine).toFloat()
                canvas.drawRoundRect(
                    RectF(
                        left - selectedWordPaddingPx,
                        top,
                        right + selectedWordPaddingPx,
                        bottom,
                    ),
                    8f,
                    8f,
                    subtlePaint,
                )
            }
        }
        layout.draw(canvas)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val layout = staticLayout ?: return true
            val textLeft = paddingLeft.toFloat() + horizontalSafetyPx
            val textTop = paddingTop.toFloat()
            val localX = event.x - textLeft
            val localY = event.y - textTop
            if (localX >= 0f && localY >= 0f && localY <= layout.height) {
                val line = layout.getLineForVertical(localY.toInt())
                val offset = layout.getOffsetForHorizontal(line, localX)
                var hitIndex = wordRanges.indexOfFirst { offset >= it.first && offset <= it.last }
                if (hitIndex < 0 && wordRanges.isNotEmpty()) {
                    var minDistance = Int.MAX_VALUE
                    for (i in wordRanges.indices) {
                        val range = wordRanges[i]
                        val distance = minOf(Math.abs(offset - range.first), Math.abs(offset - range.last))
                        if (distance < minDistance) {
                            minDistance = distance
                            hitIndex = i
                        }
                    }
                }
                if (hitIndex >= 0) {
                    listener?.invoke(lineStartIndex + hitIndex)
                    performClick()
                    return true
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun rebuildText() {
        if (words.isEmpty()) {
            wordRanges = emptyList()
            layoutText = ""
            staticLayout = null
            return
        }

        val lineText =
            PreviewLineTextFactory.build(
                words = words,
                lineStartIndex = lineStartIndex,
                selectedIndex = selectedIndex,
                accentColor = accentColor,
            )
        wordRanges = lineText.wordRanges
        layoutText = lineText.text
        staticLayout = null
    }

    private fun buildLayout(availableTextWidth: Int): StaticLayout {
        return PreviewLineTextFactory.buildLayout(layoutText, textPaint, availableTextWidth)
    }
}
