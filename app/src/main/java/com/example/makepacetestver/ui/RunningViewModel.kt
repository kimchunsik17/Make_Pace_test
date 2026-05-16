package com.example.makepacetestver.ui

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.makepacetestver.data.TrackingPoint
import com.example.makepacetestver.data.toTrackingPoint
import com.example.makepacetestver.data.db.RunDao
import com.example.makepacetestver.data.db.RunEntity
import com.example.makepacetestver.data.db.RunPointEntity
import com.example.makepacetestver.service.TrackingService
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class RunningViewModel(private val runDao: RunDao) : ViewModel() {

    private val _distance = MutableStateFlow(0f)
    val distance = _distance.asStateFlow()

    private val _currentPace = MutableStateFlow("0'00")
    val currentPace = _currentPace.asStateFlow()

    private val _elevationGain = MutableStateFlow(0.0)
    val elevationGain = _elevationGain.asStateFlow()

    private val _pathPoints = MutableStateFlow<List<LatLng>>(emptyList())
    val pathPoints = _pathPoints.asStateFlow()

    private val _pathPointsWithDetails = MutableStateFlow<List<TrackingPoint>>(emptyList())
    
    private var lastLocation: Location? = null

    init {
        TrackingService.locationUpdates
            .onEach { location ->
                calculateMetrics(location)
                updatePath(location)
            }
            .launchIn(viewModelScope)
    }

    private fun calculateMetrics(newLocation: Location) {
        lastLocation?.let { last ->
            val distanceToLast = last.distanceTo(newLocation)
            _distance.value += distanceToLast

            // 고도 기록: 올라간 높이(Elevation Gain)만 누적
            if (newLocation.hasAltitude() && last.hasAltitude()) {
                val altDiff = newLocation.altitude - last.altitude
                if (altDiff > 0) {
                    _elevationGain.value += altDiff
                }
            }

            if (newLocation.speed > 0.5) {
                val paceSecondsPerKm = (1000 / newLocation.speed).roundToInt()
                val minutes = paceSecondsPerKm / 60
                val seconds = paceSecondsPerKm % 60
                _currentPace.value = String.format("%d'%02d", minutes, seconds)
            }
        }
        lastLocation = newLocation
    }

    private fun updatePath(location: Location) {
        val trackingPoint = location.toTrackingPoint()
        _pathPointsWithDetails.value = _pathPointsWithDetails.value + trackingPoint
        
        val newLatLng = LatLng(location.latitude, location.longitude)
        _pathPoints.value = _pathPoints.value + newLatLng
    }

    fun saveRun(durationMillis: Long) {
        viewModelScope.launch {
            val runEntity = RunEntity(
                timestamp = System.currentTimeMillis(),
                avgPace = _currentPace.value,
                distanceMeter = _distance.value,
                durationMillis = durationMillis,
                elevationGain = _elevationGain.value,
                pathPointsJson = Gson().toJson(_pathPoints.value)
            )
            val runId = runDao.insertRun(runEntity)

            val runPoints = _pathPointsWithDetails.value.map {
                RunPointEntity(
                    runId = runId,
                    timestamp = it.timeMillis,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitude = it.altitude,
                    instantaneousSpeed = it.speed
                )
            }
            runDao.insertRunPoints(runPoints)
            
            // Firebase Sync
            syncToFirebase(runEntity, runPoints)
        }
    }

    private fun syncToFirebase(run: RunEntity, points: List<RunPointEntity>) {
        val db = FirebaseFirestore.getInstance()
        val runData = hashMapOf(
            "timestamp" to run.timestamp,
            "avgPace" to run.avgPace,
            "distanceMeter" to run.distanceMeter,
            "durationMillis" to run.durationMillis,
            "elevationGain" to run.elevationGain
        )

        db.collection("runs").add(runData)
            .addOnSuccessListener { documentReference ->
                val pointsBatch = db.batch()
                points.forEach { point ->
                    val pointData = hashMapOf(
                        "timestamp" to point.timestamp,
                        "lat" to point.latitude,
                        "lng" to point.longitude,
                        "alt" to point.altitude,
                        "speed" to point.instantaneousSpeed
                    )
                    val pointRef = documentReference.collection("points").document()
                    pointsBatch.set(pointRef, pointData)
                }
                pointsBatch.commit()
            }
    }
}
