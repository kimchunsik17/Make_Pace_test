package com.example.makepacetestver.ui

import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.makepacetestver.data.PaceStrategy
import com.example.makepacetestver.data.TrackingPoint
import com.example.makepacetestver.data.toTrackingPoint
import com.example.makepacetestver.data.db.RunDao
import com.example.makepacetestver.data.db.RunEntity
import com.example.makepacetestver.data.db.RunPointEntity
import com.example.makepacetestver.service.TrackingService
import com.example.makepacetestver.utils.VoiceCoachingManager
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.roundToInt

class RunningViewModel(private val runDao: RunDao) : ViewModel() {

    private var voiceManager: VoiceCoachingManager? = null
    private var selectedStrategy: PaceStrategy? = null
    private var targetPaceSeconds: Int = 0
    private var currentPaceInSeconds: Int = 0

    private val _distance = MutableStateFlow(0f)
    val distance = _distance.asStateFlow()

    private val _currentPace = MutableStateFlow("0'00")
    val currentPace = _currentPace.asStateFlow()

    private val _elapsedTime = MutableStateFlow("00:00:00")
    val elapsedTime = _elapsedTime.asStateFlow()

    private val _elevationGain = MutableStateFlow(0.0)
    val elevationGain = _elevationGain.asStateFlow()

    private val _pathPoints = MutableStateFlow<List<LatLng>>(emptyList())
    val pathPoints = _pathPoints.asStateFlow()

    private val _pathPointsWithDetails = MutableStateFlow<List<TrackingPoint>>(emptyList())
    
    private var lastLocation: Location? = null
    private var timerJob: Job? = null
    private var startTimeMillis = 0L

    fun startTimer() {
        startTimeMillis = System.currentTimeMillis()
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - startTimeMillis
                val seconds = (elapsed / 1000) % 60
                val minutes = (elapsed / (1000 * 60)) % 60
                val hours = (elapsed / (1000 * 60 * 60))
                _elapsedTime.value = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                delay(1000)
            }
        }
    }

    fun stopTimer(): Long {
        timerJob?.cancel()
        return System.currentTimeMillis() - startTimeMillis
    }

    fun initVoiceManager(context: Context) {
        if (voiceManager == null) {
            voiceManager = VoiceCoachingManager(context)
        }
    }

    fun setStrategy(strategy: PaceStrategy) {
        this.selectedStrategy = strategy
        this.targetPaceSeconds = strategy.basePaceMinutes * 60
    }

    init {
        TrackingService.locationUpdates
            .onEach { location ->
                calculateMetrics(location)
                updatePath(location)
            }
            .launchIn(viewModelScope)
            
        startCoachingLoop()
    }

    private fun startCoachingLoop() {
        viewModelScope.launch {
            while (true) {
                delay(30000) // 30초마다 체크
                if (selectedStrategy != null && currentPaceInSeconds > 0) {
                    voiceManager?.coachPace(
                        currentPaceInSeconds,
                        targetPaceSeconds,
                        selectedStrategy!!.tolerancePercentage
                    )
                }
            }
        }
    }

    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()

    fun togglePause() {
        _isPaused.value = !_isPaused.value
        if (_isPaused.value) {
            timerJob?.cancel()
        } else {
            startTimer() // Resume
        }
    }

    private fun calculateMetrics(newLocation: Location) {
        if (_isPaused.value) {
            lastLocation = newLocation // 위치는 갱신하되 계산은 건너뜀
            return
        }
        lastLocation?.let { last ->
            val distanceToLast = last.distanceTo(newLocation)
            val timeDelta = (newLocation.time - last.time) / 1000f // seconds

            if (distanceToLast > 0.1 && timeDelta > 0) { // 아주 작은 움직임도 감지
                _distance.value += distanceToLast

                // 고도 계산
                if (newLocation.hasAltitude() && last.hasAltitude()) {
                    val altDiff = newLocation.altitude - last.altitude
                    if (altDiff > 0) _elevationGain.value += altDiff
                }

                // 페이스 계산: 속도 데이터가 없으면 수동 계산 (거리 / 시간)
                val speed = if (newLocation.speed > 0) newLocation.speed else (distanceToLast / timeDelta)
                
                if (speed > 0.1) { // 걷는 수준 이상의 움직임만 반영
                    currentPaceInSeconds = (1000 / speed).toInt()
                    val paceMinutes = currentPaceInSeconds / 60
                    val paceSeconds = currentPaceInSeconds % 60
                    _currentPace.value = String.format(Locale.getDefault(), "%d'%02d", paceMinutes, paceSeconds)
                }
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
            
            // Firebase Sync (JSON 파일 추가 후 아래 주석 해제)
            // syncToFirebase(runEntity, runPoints)
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager?.shutdown()
    }
}
