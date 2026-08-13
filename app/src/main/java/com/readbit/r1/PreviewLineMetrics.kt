package com.readbit.r1

import kotlin.math.ceil

object PreviewLineMetrics {
    private const val WORD_SPACING_DP = 6f
    private const val HORIZONTAL_SAFETY_DP = 6f
    private const val SELECTED_PADDING_DP = 4f
    private const val LINE_END_RESERVE_DP = 0f
    private const val SELECTED_EXTRA_FUDGE_DP = 2f

    fun wordSpacingPx(density: Float): Float = WORD_SPACING_DP * density

    fun horizontalSafetyPx(density: Float): Float = HORIZONTAL_SAFETY_DP * density

    fun selectedWordPaddingPx(density: Float): Float = SELECTED_PADDING_DP * density

    fun lineEndReservePx(density: Float): Float = LINE_END_RESERVE_DP * density

    fun selectedWordExtraWidthPx(density: Float): Float =
        selectedWordPaddingPx(density) * 2f + (SELECTED_EXTRA_FUDGE_DP * density)

    fun ceilWidth(value: Float): Float = ceil(value.toDouble()).toFloat()
}
