package com.minerxgloble.minerxgloble.models

data class CryptoPrice(
    val usd: Double,
    val usd_24h_change: Double
)

typealias CryptoResponse = Map<String, CryptoPrice>
