package com.example.makepacetestver.ui

import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.makepacetestver.data.PaceStrategy
import com.example.makepacetestver.data.StrategyProvider
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
    private var isPlannedRunning: Boolean = true
    private var isTrackingActive: Boolean = false // 실제 추적 상태 추가

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

    private val _appropriatePace = MutableStateFlow<Float?>(null)
    val appropriatePace = _appropriatePace.asStateFlow()

    private val _elapsedTime = MutableStateFlow("00:00:00")
    val elapsedTime = _elapsedTime.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()

    private val _elevationGain = MutableStateFlow(0.0)
    val elevationGain = _elevationGain.asStateFlow()

    private val _pathPoints = MutableStateFlow<List<LatLng>>(emptyList())
    val pathPoints = _pathPoints.asStateFlow()

    private val _lastSavedRunId = MutableStateFlow(-1L)
    val lastSavedRunId = _lastSavedRunId.asStateFlow()

    // 클럽 루트 모드: 선택한 참조 경로 및 목표 페이스
    private val _clubRoutePoints = MutableStateFlow<List<LatLng>>(emptyList())
    val clubRoutePoints = _clubRoutePoints.asStateFlow()

    fun setClubRoute(points: List<LatLng>, targetPaceSec: Int, routeId: String = "") {
        _clubRoutePoints.value = points
        targetPaceSeconds = targetPaceSec
        isPlannedRunning = true
        currentClubRouteId = routeId
    }

    fun clearClubRoute() {
        _clubRoutePoints.value = emptyList()
        currentClubRouteId = ""
    }

    /** Planned Running 모드에서 목표 페이스/거리 설정 */
    fun setGoalPace(paceSec: Int, targetDistanceKm: Float = 0f) {
        targetPaceSeconds = paceSec
        if (targetDistanceKm > 0f) goalTargetDistanceKm = targetDistanceKm
        isPlannedRunning = true
    }


    private val _pathPointsWithDetails = MutableStateFlow<List<TrackingPoint>>(emptyList())
    
    private var lastLocation: Location? = null
    private var timerJob: Job? = null
    private var lastStartTimeMillis = 0L
    private var accumulatedTimeMillis = 0L  // 일시정지 전까지 누적된 시간
    private var lastDistanceMilestone = 0 // km 단위
    private val reachedPercentMilestones = mutableSetOf<Int>() // 25, 50, 75, 100
    private var goalTargetDistanceKm: Float = 0f  // 목표 다이얼로그로 설정한 목표 거리
    private var smoothedPaceSeconds: Int = 0  // EMA 스무딩된 페이스

    // 현재 클럽 루트 ID (댓글/완주 기록용)
    var currentClubRouteId: String = ""

    fun togglePause() {
        _isPaused.value = !_isPaused.value
        if (_isPaused.value) {
            // 일시정지: 현재까지 경과 시간 누적 저장
            accumulatedTimeMillis += System.currentTimeMillis() - lastStartTimeMillis
            timerJob?.cancel()
        } else {
            // 재개: 마지막 시작 시간 갱신하고 타이머 재시작
            startTimer()
        }
    }

    fun resetRunData() {
        _distance.value = 0f
        _currentPace.value = "--'--"
        _averagePace.value = "0'00"
        _appropriatePace.value = null
        _elapsedTime.value = "00:00:00"
        _elevationGain.value = 0.0
        _pathPoints.value = emptyList()
        _pathPointsWithDetails.value = emptyList()
        _isPaused.value = false
        _lastSavedRunId.value = -1L
        lastLocation = null
        lastStartTimeMillis = 0L
        accumulatedTimeMillis = 0L
        currentPaceInSeconds = 0
        smoothedPaceSeconds = 0
        lastDistanceMilestone = 0
        reachedPercentMilestones.clear()
        goalTargetDistanceKm = 0f
        currentClubRouteId = ""
    }

    fun startTimer() {
        lastStartTimeMillis = System.currentTimeMillis()
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val elapsed = accumulatedTimeMillis + (System.currentTimeMillis() - lastStartTimeMillis)
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
        return if (_isPaused.value) {
            // 일시정지 상태: 이미 누적된 시간이 정확함 (중복 합산 방지)
            accumulatedTimeMillis
        } else {
            accumulatedTimeMillis + (System.currentTimeMillis() - lastStartTimeMillis)
        }
    }

    fun initVoiceManager(context: Context) {
        if (voiceManager == null) {
            voiceManager = VoiceCoachingManager(context)
        }
    }

    fun speakImmediate(text: String) {
        voiceManager?.speak(text)
    }

    fun initPredictor(context: Context) {
        if (predictor == null) {
            predictor = RunningCoachPredictor(context)
            userPrefs = UserPreferences(context)
            loadProfileFromCache() // 캐시된 프로필 먼저 로드
            fetchUserProfileForML() // 가능하면 최신화
            
            // 마지막 전략 로드
            if (selectedStrategy == null) {
                userPrefs?.getLastStrategyId()?.let { lastId ->
                    StrategyProvider.strategies.find { it.id == lastId }?.let {
                        selectedStrategy = it
                    }
                }
            }

            // 프로필 로드 후 전략이 이미 선택되어 있다면 타겟 페이스 재계산
            selectedStrategy?.let { calculateDynamicTargetPace(it) }
        }
    }

    private fun loadProfileFromCache() {
        userPrefs?.let {
            userAge = it.getAge().toFloat()
            userWeight = it.getWeight()
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
                
                // 최신 데이터 로드 후 재계산
                selectedStrategy?.let { calculateDynamicTargetPace(it) }
            }
        }
    }

    fun setStrategy(strategy: PaceStrategy) {
        this.selectedStrategy = strategy
        this.isPlannedRunning = true
        userPrefs?.saveLastStrategyId(strategy.id)
        calculateDynamicTargetPace(strategy)
    }

    fun setPlannedRunning(enabled: Boolean) {
        this.isPlannedRunning = enabled
        if (enabled && selectedStrategy != null) {
            calculateDynamicTargetPace(selectedStrategy!!)
        }
    }

    fun setTrackingActive(active: Boolean) {
        this.isTrackingActive = active
    }

    private fun calculateDynamicTargetPace(strategy: PaceStrategy) {
        var baseSeconds = strategy.basePaceMinutes * 60
        
        // 유저 프로필 기반 동적 보정 (더 정교하게)
        // 1. 나이: 30세 기준, 1세당 1.5초 보정 (고연령일수록 여유있게)
        baseSeconds += ((userAge - 30) * 1.5f).toInt()
        
        // 2. 체중: 70kg 기준, 1kg당 2.5초 보정
        baseSeconds += ((userWeight - 70) * 2.5f).toInt()
        
        // 3. 성별: 여성(0) 기준, 남성(1)은 40초 빠르게
        if (userGender == 1f) {
            baseSeconds -= 40
        }

        this.targetPaceSeconds = baseSeconds.coerceIn(240, 1200)
    }

    init {
        TrackingService.locationUpdates
            .onEach { location ->
                if (!isTrackingActive) return@onEach
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
                
                // 추적 중이 아니거나, 일시정지 상태거나, 가이드 모드가 아니면 건너뜀
                if (!isTrackingActive || _isPaused.value || !isPlannedRunning) continue

                val currentTargetSec = if (_appropriatePace.value != null) {
                    (_appropriatePace.value!! * 60).toInt()
                } else {
                    targetPaceSeconds
                }

                if (currentPaceInSeconds > 0) {
                    voiceManager?.coachPace(
                        currentPaceInSeconds,
                        currentTargetSec,
                        0.05f
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

            if (distanceToLast > 0 && timeDelta > 0) {
                _distance.value += distanceToLast
                
                // 1. 현재 페이스 계산 (속도 기반, 필터링 적용)
                val speed = if (newLocation.speed > 0) newLocation.speed else (distanceToLast / timeDelta)
                
                // 속도 보정: 0.5m/s(시속 1.8km) 이하이면 멈춘 것으로 간주
                if (speed > 0.5) {
                    val rawPaceSec = (1000 / speed).toInt()
                    if (rawPaceSec in 120..900) {
                        // EMA 스무딩 (α=0.25: 새 값 25%, 이전 값 75%) — GPS 튐 방지
                        smoothedPaceSeconds = if (smoothedPaceSeconds == 0) rawPaceSec
                        else ((0.25f * rawPaceSec) + (0.75f * smoothedPaceSeconds)).toInt()
                        currentPaceInSeconds = smoothedPaceSeconds
                        _currentPace.value = String.format(Locale.getDefault(), "%d'%02d", currentPaceInSeconds / 60, currentPaceInSeconds % 60)
                    } else if (rawPaceSec > 900) {
                        _currentPace.value = "--'--"
                        currentPaceInSeconds = 0
                    }
                } else {
                    currentPaceInSeconds = 0
                    _currentPace.value = "--'--"
                }

                // 2. 평균 페이스 계산 (전체 거리 / 경과 시간, 일시정지 시간 제외)
                val totalElapsedSec = (accumulatedTimeMillis + (System.currentTimeMillis() - lastStartTimeMillis)) / 1000
                if (_distance.value > 10 && totalElapsedSec > 0) {
                    val avgPaceSec = (totalElapsedSec / (_distance.value / 1000)).toInt()
                    if (avgPaceSec < 1500) { // 비현실적인 페이스 방지
                        _averagePace.value = String.format(Locale.getDefault(), "%d'%02d", avgPaceSec / 60, avgPaceSec % 60)
                    }

                    // 1km 마다 브리핑 (사용자 요청 반영)
                    val currentKmInt = (_distance.value / 1000).toInt()
                    if (currentKmInt > lastDistanceMilestone) {
                        lastDistanceMilestone = currentKmInt
                        val briefText = "현재 ${currentKmInt} 킬로미터 지점입니다. 평균 페이스는 ${_averagePace.value} 입니다."
                        speakImmediate(briefText)
                    }

                    // 목표 거리 기반 구간 브리핑
                    checkDistanceMilestones()
                }

                val grade = if (newLocation.hasAltitude() && last.hasAltitude()) {
                    val altDiff = newLocation.altitude - last.altitude
                    if (altDiff > 0) _elevationGain.value += altDiff
                    (altDiff / distanceToLast).toFloat()
                } else {
                    0f
                }
                
                val currentPaceMinKm = if (speed > 0.1) (1000f / speed) / 60f else 0f

                if (currentPaceMinKm > 0) {
                    val predictedRaw = predictor?.predictNextPace(
                        currentPace = currentPaceMinKm,
                        currentGrade = grade,
                        age = userAge,
                        gender = userGender,
                        weight = userWeight
                    )

                    // [핵심] 적정 페이스 블렌딩 로직
                    if (predictedRaw != null && targetPaceSeconds > 0) {
                        val strategyTargetMinKm = targetPaceSeconds / 60f
                        // AI 비중을 40%로 상향하여 더 역동적인 변화 유도
                        val blendedPace = (predictedRaw * 0.4f) + (strategyTargetMinKm * 0.6f)
                        _appropriatePace.value = blendedPace
                    }
                }
            }
        }
        lastLocation = newLocation
    }

    private fun updatePath(location: Location) {
        val trackingPoint = location.toTrackingPoint(_appropriatePace.value)
        _pathPointsWithDetails.value = _pathPointsWithDetails.value + trackingPoint
        
        val newLatLng = LatLng(location.latitude, location.longitude)
        _pathPoints.value = _pathPoints.value + newLatLng
    }

    private fun checkDistanceMilestones() {
        val targetKm = selectedStrategy?.customTargetDistanceKm ?: goalTargetDistanceKm
        if (targetKm <= 0) return

        val currentKm = _distance.value / 1000f
        val percent = ((currentKm / targetKm) * 100).toInt()

        if (targetKm <= 3.0f) {
            // 3km 이하: 절반(50%), 도착(100%)
            if (percent >= 50 && !reachedPercentMilestones.contains(50)) {
                reachedPercentMilestones.add(50)
                val text = "목표 거리의 절반인 ${String.format(Locale.getDefault(), "%.1f", currentKm)} 킬로미터를 달렸습니다."
                speakImmediate(text)
            } else if (percent >= 100 && !reachedPercentMilestones.contains(100)) {
                reachedPercentMilestones.add(100)
                speakImmediate("목표 거리에 도달했습니다! 정말 대단해요.")
            }
        } else {
            // 3km 초과: 25% 구간마다
            val milestones = listOf(25, 50, 75, 100)
            for (m in milestones) {
                if (percent >= m && !reachedPercentMilestones.contains(m)) {
                    reachedPercentMilestones.add(m)
                    if (m == 100) {
                        speakImmediate("목표 거리에 도달했습니다! 완주를 축하합니다.")
                    } else {
                        val remaining = targetKm - currentKm
                        val text = "전체 코스의 $m 퍼센트를 완료했습니다. 남은 거리는 ${String.format(Locale.getDefault(), "%.1f", remaining)} 킬로미터입니다."
                        speakImmediate(text)
                    }
                    break
                }
            }
        }
    }

    fun saveRun(durationMillis: Long) {
        viewModelScope.launch {
            val runEntity = RunEntity(
                timestamp = System.currentTimeMillis(),
                avgPace = _averagePace.value,
                distanceMeter = _distance.value,
                durationMillis = durationMillis,
                elevationGain = _elevationGain.value,
                pathPointsJson = Gson().toJson(_pathPoints.value)
            )
            val runId = runDao.insertRun(runEntity)
            _lastSavedRunId.value = runId

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
                    // Firestore batch는 500개 제한 — 청크로 분할 처리
                    points.chunked(400).forEach { chunk ->
                        val pointsBatch = db.batch()
                        chunk.forEach { point ->
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
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager?.shutdown()
        predictor?.close()
    }
}
