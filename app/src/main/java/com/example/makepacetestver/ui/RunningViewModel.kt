package com.example.makepacetestver.ui

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.makepacetestver.data.db.RunDao
import com.example.makepacetestver.data.db.RunEntity
import com.example.makepacetestver.service.TrackingService
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class RunningViewModel(private val runDao: RunDao) : ViewModel() {

    private val _distance = MutableStateFlow(0f)
    val distance = _distance.asStateFlow()

    private val _currentPace = MutableStateFlow("0'00\"")
    val currentPace = _currentPace.asStateFlow()

    private val _pathPoints = MutableStateFlow<List<LatLng>>(emptyList())
    val pathPoints = _pathPoints.asStateFlow()

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

            if (newLocation.speed > 0.5) {
                val paceSecondsPerKm = (1000 / newLocation.speed).roundToInt()
                val minutes = paceSecondsPerKm / 60
                val seconds = paceSecondsPerKm % 60
                _currentPace.value = String.format("%d'%02d\"", minutes, seconds)
            }
        }
        lastLocation = newLocation
    }

    private fun updatePath(location: Location) {
        val newLatLng = LatLng(location.latitude, location.longitude)
        _pathPoints.value = _pathPoints.value + newLatLng
    }

    fun saveRun(durationMillis: Long) {
        viewModelScope.launch {
            val run = RunEntity(
                timestamp = System.currentTimeMillis(),
                avgPace = _currentPace.value,
                distanceMeter = _distance.value,
                durationMillis = durationMillis,
                pathPointsJson = Gson().toJson(_pathPoints.value)
            )
            runDao.insertRun(run)
        }
    }
}
