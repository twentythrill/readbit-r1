package com.readbit.r1

import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan

data class PreviewLineText(
    val text: SpannableString,
    val wordRanges: List<IntRange>,
)

object PreviewLineTextFactory {
    fun build(
        words: List<String>,
        lineStartIndex: Int,
        selectedIndex: Int,
        accentColor: Int,
    ): PreviewLineText {
        if (words.isEmpty()) {
            return PreviewLineText(SpannableString(""), emptyList())
        }

        val builder = StringBuilder()
        val ranges = ArrayList<IntRange>(words.size)
        words.forEachIndexed { index, word ->
            if (index > 0) builder.append(' ')
            val start = builder.length
            builder.append(word)
            val endExclusive = builder.length
            ranges += start until endExclusive
        }

        val spannable = SpannableString(builder.toString())
        val selectedLocalIndex = selectedIndex - lineStartIndex
        if (selectedLocalIndex in ranges.indices) {
            val range = ranges[selectedLocalIndex]
            val start = range.first
            val endExclusive = range.last + 1
            spannable.setSpan(
                ForegroundColorSpan(accentColor),
                start,
                endExclusive,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }

        return PreviewLineText(
            text = spannable,
            wordRanges = ranges,
        )
    }

    fun buildLayout(
        text: CharSequence,
        paint: TextPaint,
        widthPx: Int,
    ): StaticLayout {
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, widthPx.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.12f)
            .build()
    }
}
