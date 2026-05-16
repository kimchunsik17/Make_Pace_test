package com.example.makepacetestver.data

import android.location.Location

data class TrackingPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timeMillis: Long,
    val accuracy: Float,
    val speed: Float
)

fun Location.toTrackingPoint() = TrackingPoint(
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    timeMillis = time,
    accuracy = accuracy,
    speed = speed
)
