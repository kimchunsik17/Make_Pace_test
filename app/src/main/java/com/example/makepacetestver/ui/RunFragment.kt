package com.example.makepacetestver.ui

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.makepacetestver.MainActivity
import com.example.makepacetestver.R
import com.example.makepacetestver.data.db.AppDatabase
import com.example.makepacetestver.databinding.FragmentRunBinding
import com.example.makepacetestver.service.TrackingService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class RunFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentRunBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: RunningViewModel
    private var map: GoogleMap? = null
    private var isTracking = false
    private var isCountingDown = false
    private var isCooldown = false
    private var isWaitingForPermissions = false
    private var longPressAnimator: ValueAnimator? = null
    
    private var countdownJob: Job? = null
    private var clickCountDuringCountdown = 0
    private var lastClickTime = 0L

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineLocationGranted) {
            map?.let {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) 
                    == PackageManager.PERMISSION_GRANTED) {
                    it.isMyLocationEnabled = true
                }
            }
            if (isWaitingForPermissions) {
                isWaitingForPermissions = false
                checkBackgroundLocationPermission()
            }
        } else {
            Toast.makeText(requireContext(), "정확한 위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRunBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val runDao = AppDatabase.getDatabase(requireContext()).getRunDao()
        val factory = RunningViewModelFactory(runDao)
        viewModel = ViewModelProvider(requireActivity(), factory)[RunningViewModel::class.java]

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        // 탭 레이아웃 설정
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        isPlannedMode = false
                        viewModel.setPlannedRunning(false)
                        binding.tvGoalSetting.text = "자유 달리기"
                    }
                    1 -> {
                        isPlannedMode = true
                        viewModel.setPlannedRunning(true)
                        updateGoalSettingLabel()
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        // 목표 설정 버튼
        binding.tvGoalSetting.setOnClickListener {
            if (isPlannedMode) showGoalSettingDialog()
        }

        // 머신러닝 예측기 및 보이스 매니저 초기화
        viewModel.initVoiceManager(requireContext())
        viewModel.initPredictor(requireContext())

        setupStopButtonLogic()
        observeViewModel()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupStopButtonLogic() {
        binding.btnStartStop.setOnTouchListener { _, event ->
            if (isCountingDown) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    handleCountdownClick()
                }
                return@setOnTouchListener true
            }

            if (!isTracking) {
                if (event.action == MotionEvent.ACTION_UP) {
                    if (isCooldown) {
                        Toast.makeText(requireContext(), "잠시 후 다시 시작할 수 있습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        checkPermissionsAndStart()
                    }
                }
                return@setOnTouchListener true
            }

            // 트래킹 중일 때 (일시정지/종료 로직)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isLongPressing = false
                    startLongPressAnimation()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = event.eventTime - event.downTime
                    if (!isLongPressing && duration < 500) {
                        viewModel.togglePause()
                    }
                    stopLongPressAnimation()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    stopLongPressAnimation()
                    true
                }
                else -> false
            }
        }
    }

    private var isLongPressing = false
    private var goalDistanceM = 0f
    private var goalPaceSec = 0
    private var isPlannedMode = false

    private fun handleCountdownClick() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < 500) {
            clickCountDuringCountdown++
        } else {
            clickCountDuringCountdown = 1
        }
        lastClickTime = currentTime

        if (clickCountDuringCountdown >= 4) {
            cancelCountdown()
        }
    }

    private fun startCountdown() {
        viewModel.resetRunData()
        map?.clear()

        isCountingDown = true
        clickCountDuringCountdown = 0
        binding.tvCountdown.visibility = View.VISIBLE
        binding.controllerLayout.visibility = View.GONE
        
        countdownJob = lifecycleScope.launch {
            for (i in 3 downTo 1) {
                binding.tvCountdown.text = i.toString()
                delay(1000)
            }
            binding.tvCountdown.visibility = View.GONE
            isCountingDown = false
            startTracking()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        isCountingDown = false
        binding.tvCountdown.visibility = View.GONE
        binding.controllerLayout.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "시작이 취소되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun startLongPressAnimation() {
        binding.waveView.visibility = View.VISIBLE
        longPressAnimator?.cancel()
        longPressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                binding.waveView.setProgress(progress)
                if (progress >= 1f) {
                    isLongPressing = true
                    finishRun()
                }
            }
            start()
        }
    }

    private fun stopLongPressAnimation() {
        longPressAnimator?.cancel()
        binding.waveView.setProgress(0f)
        binding.waveView.visibility = View.GONE
    }

    private fun finishRun() {
        stopLongPressAnimation()
        stopTrackingAndShowSummary()
    }

    private fun showPauseSummary() {
        binding.trackingStatsLayout.visibility = View.GONE
        binding.pauseSummaryLayout.visibility = View.VISIBLE
        binding.tvSummaryLabel.text = "일시정지"
        binding.pauseControlButtons.visibility = View.VISIBLE
        binding.btnSummaryDone.visibility = View.GONE
        binding.btnSummaryShareToClub.visibility = View.GONE

        // 현재 스탯 채우기
        binding.tvSummaryDistance.text = binding.tvDistance.text
        binding.tvSummaryTime.text = binding.tvTimer.text
        binding.tvSummaryPace.text = binding.tvAveragePace.text
        binding.tvSummaryElevation.text = binding.tvElevation.text

        // ▶ 재개
        binding.btnSummaryResume.setOnClickListener {
            viewModel.togglePause()
        }

        // ■ 종료
        binding.btnSummaryStop.setOnClickListener {
            stopTrackingAndShowSummary()
        }
    }

    private fun hidePauseSummary() {
        binding.pauseSummaryLayout.visibility = View.GONE
        binding.trackingStatsLayout.visibility = View.VISIBLE
    }

    private fun stopTrackingAndShowSummary() {
        isTracking = false
        viewModel.setTrackingActive(false)
        (requireActivity() as? MainActivity)?.setBottomNavVisibility(true)
        isCooldown = true
        lifecycleScope.launch {
            delay(3000)
            isCooldown = false
        }

        val duration = viewModel.stopTimer()
        val intent = Intent(requireContext(), TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        }
        requireActivity().startService(intent)
        viewModel.saveRun(duration)

        // 클럽 루트 완주 기록 (댓글 권한용)
        val routeId = viewModel.currentClubRouteId
        if (routeId.isNotEmpty()) {
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("running_routes").document(routeId)
                    .update("ranBy", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
            }
        }

        // 완료 요약 화면으로 전환
        showFinishSummary(duration)
    }

    private fun showFinishSummary(durationMillis: Long) {
        binding.trackingStatsLayout.visibility = View.GONE
        binding.controllerLayout.visibility = View.GONE
        binding.headerLayout.visibility = View.GONE
        binding.pauseSummaryLayout.visibility = View.VISIBLE

        binding.tvSummaryLabel.text = "러닝 완료 🎉"
        binding.pauseControlButtons.visibility = View.GONE
        binding.btnSummaryDone.visibility = View.VISIBLE
        binding.btnSummaryShareToClub.visibility = View.VISIBLE

        // 최종 스탯 채우기
        binding.tvSummaryDistance.text = binding.tvDistance.text
        binding.tvSummaryTime.text = binding.tvTimer.text
        binding.tvSummaryPace.text = binding.tvAveragePace.text
        binding.tvSummaryElevation.text = binding.tvElevation.text

        // 클럽 공유 버튼 - lastSavedRunId 관찰해서 연결
        binding.btnSummaryShareToClub.setOnClickListener {
            val runId = viewModel.lastSavedRunId.value
            val distanceKm = viewModel.distance.value / 1000.0
            val avgPace = viewModel.averagePace.value
            val sheet = ShareToClubBottomSheet.newInstance(runId, distanceKm, avgPace, durationMillis)
            sheet.show(parentFragmentManager, "share_to_club")
        }

        // 확인 버튼 - 러닝 화면 초기화
        binding.btnSummaryDone.setOnClickListener {
            binding.pauseSummaryLayout.visibility = View.GONE
            binding.goalProgressLayout.visibility = View.GONE
            binding.headerLayout.visibility = View.VISIBLE
            binding.tvGoalSetting.visibility = View.VISIBLE
            binding.controllerLayout.visibility = View.VISIBLE
            binding.btnStartStop.text = "RUN"
            binding.btnContainer.background.setTint(Color.parseColor("#0091EA"))
            viewModel.clearClubRoute()
            map?.clear()
        }
    }

    // ─────────────────────────────────────
    // 목표 설정 다이얼로그
    // ─────────────────────────────────────
    private fun showGoalSettingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_goal_setting, null)

        val btn3k    = dialogView.findViewById<android.widget.Button>(R.id.btnGoal3k)
        val btn5k    = dialogView.findViewById<android.widget.Button>(R.id.btnGoal5k)
        val btn10k   = dialogView.findViewById<android.widget.Button>(R.id.btnGoal10k)
        val btnHalf  = dialogView.findViewById<android.widget.Button>(R.id.btnGoalHalf)
        val btnFull  = dialogView.findViewById<android.widget.Button>(R.id.btnGoalFull)
        val etCustom = dialogView.findViewById<android.widget.EditText>(R.id.etGoalDistanceCustom)
        val etPace   = dialogView.findViewById<android.widget.EditText>(R.id.etGoalPace)
        val tvError  = dialogView.findViewById<android.widget.TextView>(R.id.tvGoalPaceError)
        val tvSummary = dialogView.findViewById<android.widget.TextView>(R.id.tvGoalSummary)
        val btnConfirm = dialogView.findViewById<android.widget.Button>(R.id.btnGoalConfirm)

        // 거리 프리셋 (km)
        val presets = mapOf(btn3k to 3.0f, btn5k to 5.0f, btn10k to 10.0f, btnHalf to 21.1f, btnFull to 42.2f)
        var selectedDistKm = 3.0f

        fun updateSummary() {
            val paceStr = etPace.text.toString().trim()
            tvSummary.visibility = View.VISIBLE
            tvSummary.text = "${String.format("%.1f", selectedDistKm)} km · ${paceStr.ifEmpty { "--:--" }} /km 로 달리기"
        }

        fun selectPreset(btn: android.widget.Button, km: Float) {
            presets.forEach { (b, _) ->
                b.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF2A2A2A.toInt())
                b.setTextColor(0xFFADADAD.toInt())
            }
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF0091EA.toInt())
            btn.setTextColor(0xFFFFFFFF.toInt())
            selectedDistKm = km
            etCustom.text.clear()
            updateSummary()
        }

        // 초기 선택: 현재 목표 or 3K
        selectPreset(btn3k, 3.0f)
        if (goalPaceSec > 0) {
            etPace.setText("${goalPaceSec / 60}:${String.format("%02d", goalPaceSec % 60)}")
        }

        presets.forEach { (btn, km) -> btn.setOnClickListener { selectPreset(btn, km) } }

        etCustom.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val v = s?.toString()?.toFloatOrNull() ?: return
                if (v > 0) {
                    selectedDistKm = v
                    presets.forEach { (b, _) ->
                        b.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF2A2A2A.toInt())
                        b.setTextColor(0xFFADADAD.toInt())
                    }
                    updateSummary()
                }
            }
        })
        etPace.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { updateSummary() }
        })

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnConfirm.setOnClickListener {
            val paceStr = etPace.text.toString().trim().replace(';', ':')
            val paceSec = parsePaceStr(paceStr)
            if (paceSec <= 0) {
                tvError.text = "⚠ 페이스 형식이 올바르지 않아요 (예: 6:00)"
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            tvError.visibility = View.GONE
            goalDistanceM = selectedDistKm * 1000f
            goalPaceSec = paceSec
            viewModel.setGoalPace(paceSec, selectedDistKm)
            updateGoalSettingLabel()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateGoalSettingLabel() {
        if (goalDistanceM > 0f && goalPaceSec > 0) {
            val km = goalDistanceM / 1000f
            val paceStr = "${goalPaceSec / 60}:${String.format("%02d", goalPaceSec % 60)}"
            binding.tvGoalSetting.text = "🎯 ${String.format("%.1f", km)}km · $paceStr /km"
        } else {
            binding.tvGoalSetting.text = "🎯 목표 설정하기"
        }
    }

    private fun parsePaceStr(s: String): Int {
        val parts = s.replace(';', ':').split(":")
        if (parts.size != 2) return 0
        val min = parts[0].toIntOrNull() ?: return 0
        val sec = parts[1].toIntOrNull() ?: return 0
        if (min !in 1..20 || sec !in 0..59) return 0
        return min * 60 + sec
    }

    private fun checkPermissionsAndStart() {
        val foregroundPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val missingForeground = foregroundPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingForeground.isEmpty()) {
            checkBackgroundLocationPermission()
        } else {
            isWaitingForPermissions = true
            requestPermissionLauncher.launch(missingForeground.toTypedArray())
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                showBackgroundPermissionDialog()
            } else {
                startCountdown()
            }
        } else {
            startCountdown()
        }
    }

    private fun showBackgroundPermissionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("백그라운드 위치 권한 필요")
            .setMessage("앱이 화면에서 사라져도 러닝 기록을 유지하려면 위치 권한을 '항상 허용'으로 설정해야 합니다.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    requestBackgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
            .setNegativeButton("그냥 시작") { _, _ ->
                startCountdown()
            }
            .show()
    }

    private val requestBackgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(requireContext(), "백그라운드 위치 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
        }
        startCountdown()
    }

    private fun startTracking() {
        isTracking = true
        viewModel.setTrackingActive(true)
        
        // 현재 선택된 탭에 따라 플랜 모드 설정
        val isPlanned = binding.tabLayout.selectedTabPosition == 1
        viewModel.setPlannedRunning(isPlanned)
        
        if (isPlanned) {
            viewModel.speakImmediate("러닝 가이드를 시작합니다. 페이스에 맞춰 달려주세요.")
        } else {
            viewModel.speakImmediate("자유 러닝을 시작합니다.")
        }

        (requireActivity() as? MainActivity)?.setBottomNavVisibility(false)
        binding.controllerLayout.visibility = View.VISIBLE
        binding.btnStartStop.text = "PAUSE"
        binding.btnContainer.background.setTint(Color.parseColor("#EEEEEE")) // 일시정지 가능 상태색 (연한 회색)
        
        binding.headerLayout.visibility = View.GONE
        binding.tvGoalSetting.visibility = View.GONE
        binding.trackingStatsLayout.visibility = View.VISIBLE
        // Planned 모드 + 목표 설정 시 진행 현황 표시
        if (isPlannedMode && goalDistanceM > 0f) {
            binding.goalProgressLayout.visibility = View.VISIBLE
            binding.tvGoalLabel.text = "목표 ${String.format("%.1f", goalDistanceM / 1000f)}km"
        }
        
        viewModel.startTimer()

        val intent = Intent(requireContext(), TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
        }
        requireActivity().startService(intent)
    }

    private fun stopTracking() {
        // 하위호환 유지용 - 내부에서 새 흐름 호출
        stopTrackingAndShowSummary()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isPaused.collectLatest { paused ->
                if (isTracking) {
                    if (paused) {
                        showPauseSummary()
                    } else {
                        hidePauseSummary()
                        binding.btnStartStop.text = "PAUSE"
                        binding.btnContainer.background.setTint(Color.parseColor("#EEEEEE"))
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.distance.collectLatest { distance ->
                _binding?.let { b ->
                    b.tvDistance.text = String.format(Locale.getDefault(), "%.2f", distance / 1000f)
                    // 목표 진행 업데이트
                    if (isPlannedMode && goalDistanceM > 0f && isTracking) {
                        val remaining = ((goalDistanceM - distance) / 1000f).coerceAtLeast(0f)
                        b.tvGoalRemaining.text = String.format("%.2f", remaining)
                        val progress = ((distance / goalDistanceM) * 100).toInt().coerceIn(0, 100)
                        b.pbGoalProgress.progress = progress
                        // 페이스 상태
                        if (goalPaceSec > 0) {
                            val currentPaceStr = b.tvCurrentPace.text.toString()
                            val currentSec = parsePaceStr(currentPaceStr)
                            b.tvGoalPaceStatus.text = when {
                                currentSec <= 0 -> "페이스 측정 중"
                                currentSec < goalPaceSec - 10 -> "🟢 목표보다 빠름"
                                currentSec > goalPaceSec + 10 -> "🔴 목표보다 느림"
                                else -> "🟡 페이스 유지 중"
                            }
                        }
                        if (remaining <= 0f) {
                            b.tvGoalPaceStatus.text = "🎉 목표 달성!"
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentPace.collectLatest { pace ->
                _binding?.let {
                    it.tvCurrentPace.text = pace
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.averagePace.collectLatest { pace ->
                _binding?.let {
                    it.tvAveragePace.text = pace
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.elevationGain.collectLatest { elevation ->
                _binding?.let {
                    it.tvElevation.text = String.format(Locale.getDefault(), "%.0fm", elevation)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.elapsedTime.collectLatest { time ->
                _binding?.let {
                    it.tvTimer.text = time
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.appropriatePace.collectLatest { pace ->
                _binding?.let {
                    if (pace != null) {
                        val minutes = pace.toInt()
                        val seconds = ((pace - minutes) * 60).toInt()
                        it.tvPredictedPace.text = String.format(Locale.getDefault(), "%d'%02d", minutes, seconds)
                    } else {
                        it.tvPredictedPace.text = "--'--"
                    }
                }
            }
        }

        // 클럽 참조 경로 (회색 점선) - 먼저 그려서 내 경로 위에 안 덮이게
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.clubRoutePoints.collectLatest { clubPoints ->
                if (clubPoints.isNotEmpty()) {
                    val refPolyline = PolylineOptions()
                        .color(Color.parseColor("#80AAAAAA")) // 반투명 회색
                        .width(8f)
                        .pattern(listOf(
                            com.google.android.gms.maps.model.Dash(20f),
                            com.google.android.gms.maps.model.Gap(10f)
                        ))
                        .addAll(clubPoints)
                    map?.addPolyline(refPolyline)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pathPoints.collectLatest { points ->
                if (points.isNotEmpty()) {
                    val polylineOptions = PolylineOptions()
                        .color(Color.parseColor("#0091EA")) // 이동 경로도 바다색 블루로 통일
                        .width(12f)
                        .jointType(JointType.ROUND)
                        .addAll(points)

                    map?.clear()
                    // 클럽 참조 경로 다시 그리기 (clear 후에도 유지)
                    val clubPoints = viewModel.clubRoutePoints.value
                    if (clubPoints.isNotEmpty()) {
                        val refPolyline = PolylineOptions()
                            .color(Color.parseColor("#80AAAAAA"))
                            .width(8f)
                            .pattern(listOf(
                                com.google.android.gms.maps.model.Dash(20f),
                                com.google.android.gms.maps.model.Gap(10f)
                            ))
                            .addAll(clubPoints)
                        map?.addPolyline(refPolyline)
                    }
                    map?.addPolyline(polylineOptions)
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(points.last(), 17f))
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            map?.isMyLocationEnabled = true
            zoomToCurrentLocation()
        }
    }

    private fun zoomToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) return

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val latLng = com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude)
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.mapView.onDestroy()
        _binding = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }
}
