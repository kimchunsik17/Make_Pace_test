package com.example.makepacetestver.data

import android.location.Location

data class TrackingPoint(
    val latitude: Double,
    val longitude: Double,
    val timeMillis: Long,
    val accuracy: Float,
    val speed: Float // m/s
)

fun Location.toTrackingPoint() = TrackingPoint(
    latitude = latitude,
    longitude = longitude,
    timeMillis = time,
    accuracy = accuracy,
    speed = speed
)
