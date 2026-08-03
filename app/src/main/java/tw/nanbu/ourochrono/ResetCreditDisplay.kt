package tw.nanbu.ourochrono

enum class ResetHeartState {
    RED,
    WHITE
}

data class ResetCreditDisplay(
    val hearts: List<ResetHeartState>,
    val multiplier: Int?,
    val unavailable: Boolean
)

object ResetCreditDisplayFactory {
    fun create(resetCredits: Int?): ResetCreditDisplay {
        if (resetCredits == null) {
            return ResetCreditDisplay(
                hearts = emptyList(),
                multiplier = null,
                unavailable = true
            )
        }

        val count = resetCredits.coerceAtLeast(0)
        if (count <= MAX_INDIVIDUAL_HEARTS) {
            return ResetCreditDisplay(
                hearts = List(MAX_INDIVIDUAL_HEARTS) { index ->
                    if (index < count) ResetHeartState.RED else ResetHeartState.WHITE
                },
                multiplier = null,
                unavailable = false
            )
        }

        return ResetCreditDisplay(
            hearts = listOf(ResetHeartState.RED),
            multiplier = count,
            unavailable = false
        )
    }

    private const val MAX_INDIVIDUAL_HEARTS = 3
}
