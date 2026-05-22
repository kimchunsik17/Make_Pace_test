package com.example.makepacetestver.ui

import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.makepacetestver.data.PaceStrategy
import com.example.makepacetestver.data.TrackingPoint
import com.example.makepacetestver.data.UserPreferences
import com.example.makepacetestver.data.toTrackingPoint
import com.example.makepacetestver.data.db.RunDao
import com.example.makepacetestver.data.db.RunEntity
import com.example.makepacetestver.data.db.RunPointEntity
import com.example.makepacetestver.service.TrackingService
import com.example.makepacetestver.utils.RunningCoachPredictor
import com.example.makepacetestver.utils.VoiceCoachingManager
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
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
    private var predictor: RunningCoachPredictor? = null
    private var userPrefs: UserPreferences? = null
    
    private var selectedStrategy: PaceStrategy? = null
    private var targetPaceSeconds: Int = 0
    private var currentPaceInSeconds: Int = 0

    // User Profile for ML
    private var userAge = 30f
    private var userGender = 1f // Male default
    private var userWeight = 70f

    private val _distance = MutableStateFlow(0f)
    val distance = _distance.asStateFlow()

    private val _currentPace = MutableStateFlow("--'--")
    val currentPace = _currentPace.asStateFlow()

    private val _averagePace = MutableStateFlow("0'00")
    val averagePace = _averagePace.asStateFlow()

    private val _predictedPace = MutableStateFlow<Float?>(null)
    val predictedPace = _predictedPace.asStateFlow()

    private val _elapsedTime = MutableStateFlow("00:00:00")
    val elapsedTime = _elapsedTime.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()

    private val _elevationGain = MutableStateFlow(0.0)
    val elevationGain = _elevationGain.asStateFlow()

    private val _pathPoints = MutableStateFlow<List<LatLng>>(emptyList())
    val pathPoints = _pathPoints.asStateFlow()

    private val _pathPointsWithDetails = MutableStateFlow<List<TrackingPoint>>(emptyList())
    
    private var lastLocation: Location? = null
    private var timerJob: Job? = null
    private var startTimeMillis = 0L

    fun togglePause() {
        _isPaused.value = !_isPaused.value
        if (_isPaused.value) {
            timerJob?.cancel()
        } else {
            startTimer()
        }
    }

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

    fun initPredictor(context: Context) {
        if (predictor == null) {
            predictor = RunningCoachPredictor(context)
            userPrefs = UserPreferences(context)
            loadProfileFromCache() // 캐시된 프로필 먼저 로드
            fetchUserProfileForML() // 가능하면 최신화
        }
    }

    private fun loadProfileFromCache() {
        userPrefs?.let {
            userAge = it.getAge().toFloat()
            userWeight = it.getWeight().toFloat()
            userGender = if (it.getGender() == "male") 1f else 0f
        }
    }

    private fun fetchUserProfileForML() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(user.uid).get().addOnSuccessListener { userDoc ->
            val researchId = userDoc.getString("researchId") ?: return@addOnSuccessListener
            db.collection("research_data").document(researchId).get().addOnSuccessListener { researchDoc ->
                userAge = researchDoc.getLong("age")?.toFloat() ?: 30f
                userWeight = researchDoc.getDouble("weight")?.toFloat() ?: 70f
                userGender = if (researchDoc.getString("gender") == "male") 1f else 0f
            }
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
                    // 1. 머신러닝 예측값이 있으면 이를 바탕으로 타겟 페이스 보정 (선택 사항)
                    val coachingTarget = if (_predictedPace.value != null) {
                        // 예측된 페이스를 70%, 전략 페이스를 30% 비율로 섞어 동적 타겟 생성 예시
                        ((_predictedPace.value!! * 60 * 0.7) + (targetPaceSeconds * 0.3)).toInt()
                    } else {
                        targetPaceSeconds
                    }

                    voiceManager?.coachPace(
                        currentPaceInSeconds,
                        coachingTarget,
                        selectedStrategy!!.tolerancePercentage
                    )
                }
            }
        }
    }

    private fun calculateMetrics(newLocation: Location) {
        if (_isPaused.value) {
            lastLocation = newLocation
            return
        }
        lastLocation?.let { last ->
            val distanceToLast = last.distanceTo(newLocation)
            val timeDelta = (newLocation.time - last.time) / 1000f

            if (distanceToLast > 0.1 && timeDelta > 0) {
                _distance.value += distanceToLast
                
                // 1. 현재 페이스 계산 (속도 기반, 필터링 적용)
                val speed = if (newLocation.speed > 0) newLocation.speed else (distanceToLast / timeDelta)
                if (speed > 0.5) { // 걷는 속도 이상일 때만 페이스 업데이트
                    val currentPaceSec = (1000 / speed).toInt()
                    _currentPace.value = String.format(Locale.getDefault(), "%d'%02d", currentPaceSec / 60, currentPaceSec % 60)
                } else {
                    _currentPace.value = "--'--"
                }

                // 2. 평균 페이스 계산 (전체 거리 / 경과 시간)
                val totalElapsedSec = (System.currentTimeMillis() - startTimeMillis) / 1000
                if (_distance.value > 10 && totalElapsedSec > 0) {
                    val avgPaceSec = (totalElapsedSec / (_distance.value / 1000)).toInt()
                    if (avgPaceSec < 1500) { // 비현실적인 페이스 방지
                        _averagePace.value = String.format(Locale.getDefault(), "%d'%02d", avgPaceSec / 60, avgPaceSec % 60)
                    }
                }

                if (newLocation.hasAltitude() && last.hasAltitude()) {
                    val altDiff = newLocation.altitude - last.altitude
                    if (altDiff > 0) _elevationGain.value += altDiff
                    
                    val grade = (altDiff / distanceToLast).toFloat()
                    val currentPaceMinKm = if (speed > 0.1) (1000f / speed) / 60f else 0f

                    if (currentPaceMinKm > 0) {
                        val predicted = predictor?.predictNextPace(
                            currentPace = currentPaceMinKm,
                            currentGrade = grade,
                            age = userAge,
                            gender = userGender,
                            weight = userWeight
                        )
                        _predictedPace.value = predicted
                    }
                }
            }
        }
        lastLocation = newLocation
    }

    private fun updatePath(location: Location) {
        val trackingPoint = location.toTrackingPoint(_predictedPace.value)
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
                    instantaneousSpeed = it.speed,
                    predictedPace = it.predictedPace
                )
            }
            runDao.insertRunPoints(runPoints)
            
            syncToFirebase(runEntity, runPoints)
        }
    }

    private fun syncToFirebase(run: RunEntity, points: List<RunPointEntity>) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        
        db.collection("users").document(user.uid).get().addOnSuccessListener { userDoc ->
            val researchId = userDoc.getString("researchId") ?: return@addOnSuccessListener
            
            val runData = hashMapOf(
                "researchId" to researchId,
                "timestamp" to run.timestamp,
                "avgPace" to run.avgPace,
                "distanceMeter" to run.distanceMeter,
                "durationMillis" to run.durationMillis,
                "elevationGain" to run.elevationGain
            )

            db.collection("research_data").document(researchId)
                .collection("runs").add(runData)
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

    override fun onCleared() {
        super.onCleared()
        voiceManager?.shutdown()
        predictor?.close()
    }
}
