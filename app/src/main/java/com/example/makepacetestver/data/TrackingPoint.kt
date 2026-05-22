package com.example.makepacetestver.data

import android.location.Location

data class TrackingPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timeMillis: Long,
    val accuracy: Float,
    val speed: Float,
    val predictedPace: Float? = null // AI 예측 페이스 (분/km)
)

fun Location.toTrackingPoint(predictedPace: Float? = null) = TrackingPoint(
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    timeMillis = time,
    accuracy = accuracy,
    speed = speed,
    predictedPace = predictedPace
)
