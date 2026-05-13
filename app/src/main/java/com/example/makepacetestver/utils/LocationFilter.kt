package com.example.makepacetestver.utils

import android.location.Location
import com.example.makepacetestver.data.TrackingPoint
import com.example.makepacetestver.data.toTrackingPoint

class LocationFilter {
    private var lastLocation: TrackingPoint? = null
    
    // 최소 정확도 (meters)
    private val MIN_ACCURACY = 20f
    // 비현실적인 이동 속도 필터링
    private val MAX_SPEED_THRESHOLD = 12f 

    fun filter(newLocation: Location): TrackingPoint? {
        if (newLocation.accuracy > MIN_ACCURACY) return null
        
        val newPoint = newLocation.toTrackingPoint()
        
        if (lastLocation == null) {
            lastLocation = newPoint
            return newPoint
        }

        val timeDelta = (newPoint.timeMillis - lastLocation!!.timeMillis) / 1000f
        if (timeDelta <= 0) return null
        
        val results = FloatArray(1)
        Location.distanceBetween(
            lastLocation!!.latitude, lastLocation!!.longitude,
            newPoint.latitude, newPoint.longitude, results
        )
        val distance = results[0]
        val speed = distance / timeDelta

        if (speed > MAX_SPEED_THRESHOLD) return null

        lastLocation = newPoint
        return newPoint
    }
}
