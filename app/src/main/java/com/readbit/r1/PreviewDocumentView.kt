package com.readbit.r1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlin.math.max

class PreviewDocumentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val horizontalSafetyPx = PreviewLineMetrics.horizontalSafetyPx(density)
    private val verticalSafetyPx = density * 8f
    private val selectedWordPaddingPx = PreviewLineMetrics.selectedWordPaddingPx(density)
    private val textPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.rb_text)
            textSize = 16f * density
            typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_regular)
        }
    private val selectionPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.rb_surface_alt)
        }
    private val accentColor = ContextCompat.getColor(context, R.color.rb_accent)

    private var words: List<String> = emptyList()
    private var selectedWordIndex: Int = 0
    private var wordRanges: List<IntRange> = emptyList()
    private var layoutText: CharSequence = ""
    private var staticLayout: StaticLayout? = null
    private var onWordSelected: ((Int) -> Unit)? = null

    fun submitWords(
        words: List<String>,
        selectedWordIndex: Int,
        onWordSelected: (Int) -> Unit,
    ) {
        this.words = words
        this.selectedWordIndex = selectedWordIndex.coerceIn(0, (words.size - 1).coerceAtLeast(0))
        this.onWordSelected = onWordSelected
        rebuildText()
        requestLayout()
        invalidate()
    }

    fun setSelectedWord(index: Int) {
        if (words.isEmpty()) return
        val next = index.coerceIn(0, words.lastIndex)
        if (next == selectedWordIndex) return
        selectedWordIndex = next
        rebuildText()
        requestLayout()
        invalidate()
    }

    fun selectedWordCenterY(): Int {
        val layout = staticLayout ?: return 0
        val range = wordRanges.getOrNull(selectedWordIndex) ?: return 0
        val startLine = layout.getLineForOffset(range.first)
        val endLine = layout.getLineForOffset(range.last)
        val top = layout.getLineTop(startLine).toFloat()
        val bottom = layout.getLineBottom(endLine).toFloat()
        return (paddingTop + verticalSafetyPx + (top + bottom) / 2f).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val availableWidth =
            (width - paddingLeft - paddingRight - horizontalSafetyPx * 2f).toInt().coerceAtLeast(1)
        staticLayout = PreviewLineTextFactory.buildLayout(layoutText, textPaint, availableWidth)
        val desiredHeight =
            (paddingTop + paddingBottom + verticalSafetyPx * 2f + (staticLayout?.height ?: 0)).toInt()
        setMeasuredDimension(width, resolveSize(max(desiredHeight, suggestedMinimumHeight), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layout = staticLayout ?: return
        if (words.isEmpty()) return

        val textLeft = paddingLeft.toFloat() + horizontalSafetyPx
        val textTop = paddingTop.toFloat() + verticalSafetyPx

        canvas.save()
        canvas.translate(textLeft, textTop)
        drawSelectedHighlight(canvas, layout)
        layout.draw(canvas)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val layout = staticLayout ?: return true
            val textLeft = paddingLeft.toFloat() + horizontalSafetyPx
            val textTop = paddingTop.toFloat() + verticalSafetyPx
            val localX = event.x - textLeft
            val localY = event.y - textTop
            if (localX >= 0f && localY >= 0f && localY <= layout.height) {
                val line = layout.getLineForVertical(localY.toInt())
                val offset = layout.getOffsetForHorizontal(line, localX)
                val hitIndex = wordRanges.indexOfFirst { offset in it.first..it.last }
                if (hitIndex >= 0) {
                    onWordSelected?.invoke(hitIndex)
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
                lineStartIndex = 0,
                selectedIndex = selectedWordIndex,
                accentColor = accentColor,
            )
        wordRanges = lineText.wordRanges
        layoutText = lineText.text
        staticLayout = null
    }

    private fun drawSelectedHighlight(canvas: Canvas, layout: StaticLayout) {
        val range = wordRanges.getOrNull(selectedWordIndex) ?: return
        val startLine = layout.getLineForOffset(range.first)
        val endLine = layout.getLineForOffset(range.last)
        val start = range.first
        val endExclusive = range.last + 1

        if (startLine == endLine) {
            val left = layout.getPrimaryHorizontal(start)
            val right = layout.getPrimaryHorizontal(endExclusive)
            val top = layout.getLineTop(startLine).toFloat()
            val bottom = layout.getLineBottom(startLine).toFloat()
            canvas.drawRoundRect(
                RectF(left - selectedWordPaddingPx, top, right + selectedWordPaddingPx, bottom),
                8f,
                8f,
                selectionPaint,
            )
            return
        }

        for (line in startLine..endLine) {
            val lineStart = if (line == startLine) layout.getPrimaryHorizontal(start) else layout.getLineLeft(line)
            val lineEnd =
                if (line == endLine) {
                    layout.getPrimaryHorizontal(endExclusive)
                } else {
                    layout.getLineRight(line)
                }
            val top = layout.getLineTop(line).toFloat()
            val bottom = layout.getLineBottom(line).toFloat()
            canvas.drawRoundRect(
                RectF(lineStart - selectedWordPaddingPx, top, lineEnd + selectedWordPaddingPx, bottom),
                8f,
                8f,
                selectionPaint,
            )
        }
    }
}
