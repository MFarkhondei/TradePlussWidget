package com.tradepluss.widget.model

data class WidgetResponse(
    val success: Boolean = false,
    val message: String? = null,
    val username: String? = null,
    val totalAssetsToman: Long = 0,
    val totalAssetsUsd: Double? = null,
    val usdRateToman: Double? = null,
    val dailyBuyToman: Long = 0,
    val dailyProfitToman: Long = 0,
    val dailyProfitPercent: Double = 0.0,
    val items: List<AssetItem> = emptyList(),
    val weeklyDates: List<String> = emptyList(),
    val weeklyValues: List<Long> = emptyList(),
    val updatedAt: String? = null
) {
    fun dollarValue(): Double? {
        totalAssetsUsd?.takeIf { it.isFinite() && it >= 0 }?.let { return it }
        if (totalAssetsToman == 0L) return 0.0
        val rate = usdRateToman?.takeIf { it.isFinite() && it > 0 }
            ?: items.firstOrNull {
                it.symbol.trim().equals("USDT", ignoreCase = true) && it.currentPrice > 0
            }?.currentPrice?.toDouble()
        return rate?.let { totalAssetsToman.toDouble() / it }
    }
}

data class AssetItem(
    val coinName: String = "",
    val symbol: String = "",
    val currentPrice: Long = 0,
    val currentValue: Long = 0,
    val profitPercent: Double = 0.0
)
