package com.readbit.r1

import kotlin.math.abs

data class OrpLayout(
    val leftSegment: String,
    val pivot: String,
    val rightSegment: String,
) {
    val hasPivot: Boolean
        get() = pivot.isNotEmpty()
}

object OrpLayoutEngine {
    fun layout(
        word: String,
        measureText: (String) -> Float,
    ): OrpLayout {
        if (word.isBlank()) {
            return OrpLayout("", "", "")
        }

        val normalized = normalize(word)
        val core = normalized.core
        if (core.isEmpty()) {
            val pivotIndex = baselinePivotIndex(word.length)
            return buildLayout(word, pivotIndex)
        }

        val candidateIndices = candidateCoreIndices(core)
        val pivotInCore =
            candidateIndices.minByOrNull { candidate ->
                val left = measureText(core.substring(0, candidate)) + measureText(core[candidate].toString()) / 2f
                val right =
                    measureText(core.substring(candidate + 1)) +
                        measureText(core[candidate].toString()) / 2f
                val balancePenalty = abs(left - right)
                val rightBiasPenalty = candidate * measureText("i") * 0.18f
                balancePenalty + rightBiasPenalty
            } ?: baselinePivotIndex(core.length)

        val pivotIndex = normalized.leadingCount + pivotInCore
        return buildLayout(word, pivotIndex)
    }

    private fun candidateCoreIndices(core: String): List<Int> {
        if (core.isEmpty()) return listOf(0)
        val baseline = baselinePivotIndex(core.length)
        val offsets = listOf(0, -1, 1, -2, 2)
        val candidates =
            offsets
                .map { baseline + it }
                .filter { it in core.indices }
                .filter { core[it].isLetterOrDigit() }
                .distinct()
        if (candidates.isNotEmpty()) return candidates
        return offsets
            .map { baseline + it }
            .filter { it in core.indices }
            .distinct()
            .ifEmpty { listOf(baseline.coerceIn(0, core.lastIndex)) }
    }

    private fun buildLayout(word: String, pivotIndex: Int): OrpLayout {
        val safePivotIndex = pivotIndex.coerceIn(0, word.lastIndex)
        return OrpLayout(
            leftSegment = word.substring(0, safePivotIndex),
            pivot = word.substring(safePivotIndex, safePivotIndex + 1),
            rightSegment = word.substring(safePivotIndex + 1),
        )
    }

    private fun normalize(word: String): NormalizedWord {
        var leading = 0
        var trailing = word.length

        while (leading < word.length && isEdgeDecoration(word[leading])) {
            leading += 1
        }
        while (trailing > leading && isEdgeDecoration(word[trailing - 1])) {
            trailing -= 1
        }

        val core = word.substring(leading, trailing)
        return if (core.any { it.isLetterOrDigit() }) {
            NormalizedWord(leadingCount = leading, core = core)
        } else {
            NormalizedWord(leadingCount = 0, core = word)
        }
    }

    private fun isEdgeDecoration(char: Char): Boolean = !char.isLetterOrDigit()

    fun baselinePivotIndex(length: Int): Int {
        return when {
            length <= 1 -> 0
            length <= 5 -> 1
            length <= 9 -> 2
            length <= 13 -> 3
            else -> 4
        }.coerceAtMost((length - 1).coerceAtLeast(0))
    }

    data class NormalizedWord(
        val leadingCount: Int,
        val core: String,
    )
}
