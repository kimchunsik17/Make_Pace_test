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
        stopTracking()
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
        binding.controllerLayout.visibility = View.VISIBLE
        binding.btnStartStop.text = "일시정지"
        binding.btnContainer.background.setTint(Color.parseColor("#EEEEEE")) // 일시정지 가능 상태색 (연한 회색)
        
        binding.headerLayout.visibility = View.GONE
        binding.tvGoalSetting.visibility = View.GONE
        binding.trackingStatsLayout.visibility = View.VISIBLE
        
        viewModel.startTimer()

        val intent = Intent(requireContext(), TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
        }
        requireActivity().startService(intent)
    }

    private fun stopTracking() {
        isTracking = false
        isCooldown = true
        lifecycleScope.launch {
            delay(3000)
            isCooldown = false
        }

        binding.btnStartStop.text = "시작"
        binding.btnContainer.background.setTint(Color.parseColor("#0091EA")) // 바다색 블루
        
        binding.headerLayout.visibility = View.VISIBLE
        binding.tvGoalSetting.visibility = View.VISIBLE
        binding.trackingStatsLayout.visibility = View.GONE
        
        val duration = viewModel.stopTimer()

        val intent = Intent(requireContext(), TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        }
        requireActivity().startService(intent)
        
        viewModel.saveRun(duration)
        Toast.makeText(requireContext(), "러닝 기록이 저장되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isPaused.collectLatest { paused ->
                if (isTracking) {
                    binding.btnStartStop.text = if (paused) "재개" else "일시정지"
                    binding.btnContainer.background.setTint(
                        if (paused) Color.parseColor("#0091EA") else Color.parseColor("#EEEEEE")
                    )
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.distance.collectLatest { distance ->
                _binding?.let {
                    it.tvDistance.text = String.format(Locale.getDefault(), "%.2f", distance / 1000f)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentPace.collectLatest { pace ->
                _binding?.let {
                    it.tvPace.text = pace
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
            viewModel.pathPoints.collectLatest { points ->
                if (points.isNotEmpty()) {
                    val polylineOptions = PolylineOptions()
                        .color(Color.parseColor("#0091EA")) // 이동 경로도 바다색 블루로 통일
                        .width(12f)
                        .jointType(JointType.ROUND)
                        .addAll(points)

                    map?.clear()
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
