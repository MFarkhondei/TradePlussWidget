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
        if (totalAssetsToman == 0L) return 0.0
        val rate = usdRateToman?.takeIf { it.isFinite() && it > 0 }
            ?: items.firstOrNull {
                val symbol = it.symbol.trim().uppercase()
                val name = it.coinName.trim()
                symbol in setOf("USD", "USDT", "USDTR", "DOLLAR") ||
                    name.contains("دلار") || name.contains("تتر")
            }?.currentPrice?.toDouble()?.takeIf { it > 0 }
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
