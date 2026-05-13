package com.example.makepacetestver

import android.content.res.ColorStateList
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.makepacetestver.data.db.AppDatabase
import com.example.makepacetestver.databinding.ActivityMainBinding
import com.example.makepacetestver.service.TrackingService
import com.example.makepacetestver.ui.RunningViewModel
import com.example.makepacetestver.ui.RunningViewModelFactory
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: RunningViewModel
    private var map: GoogleMap? = null
    private var isTracking = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineLocationGranted) {
            map?.let {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                    == PackageManager.PERMISSION_GRANTED) {
                    it.isMyLocationEnabled = true
                }
            }
            // 만약 시작 버튼을 눌러서 요청된 것이라면 추적 시작
            if (isWaitingForPermissions) {
                isWaitingForPermissions = false
                checkBackgroundLocationPermission()
            }
        } else {
            Toast.makeText(this, "정확한 위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private var isWaitingForPermissions = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val runDao = AppDatabase.getDatabase(this).getRunDao()
        val factory = RunningViewModelFactory(runDao)
        viewModel = ViewModelProvider(this, factory)[RunningViewModel::class.java]

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        // 앱 시작 시 권한 체크 실행
        checkPermissionsOnStart()

        binding.btnStartStop.setOnClickListener {
            toggleTracking()
        }

        observeViewModel()
    }

    private fun toggleTracking() {
        if (isTracking) {
            stopTracking()
        } else {
            checkPermissionsAndStart()
        }
    }

    private fun checkPermissionsOnStart() {
        val foregroundPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            foregroundPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = foregroundPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            showPermissionRationaleDialog(missingPermissions.toTypedArray())
        }
    }

    private fun showPermissionRationaleDialog(permissions: Array<String>) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("위치 권한 필요")
            .setMessage("러닝 경로를 기록하고 정확한 페이스를 측정하기 위해 위치 권한이 필요합니다. '정확한 위치' 및 '앱 사용 중에만 허용'을 선택해 주세요.")
            .setPositiveButton("확인") { _, _ ->
                requestPermissionLauncher.launch(permissions)
            }
            .setCancelable(false)
            .show()
    }

    private fun checkPermissionsAndStart() {
        val foregroundPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val missingForeground = foregroundPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
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
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                showBackgroundPermissionDialog()
            } else {
                startTracking()
            }
        } else {
            startTracking()
        }
    }

    private fun showBackgroundPermissionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("백그라운드 위치 권한 필요")
            .setMessage("앱이 화면에서 사라져도 러닝 기록을 유지하려면 위치 권한을 '항상 허용'으로 설정해야 합니다.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    requestBackgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
            .setNegativeButton("그냥 시작") { _, _ ->
                startTracking()
            }
            .show()
    }

    private val requestBackgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "백그라운드 위치 권한이 거부되었습니다. 기록이 중단될 수 있습니다.", Toast.LENGTH_SHORT).show()
        }
        startTracking()
    }

    private fun startTracking() {
        isTracking = true
        binding.btnStartStop.text = "중지"
        binding.btnStartStop.backgroundTintList = ColorStateList.valueOf(Color.RED)
        
        val intent = Intent(this, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
        }
        startService(intent)
    }

    private fun stopTracking() {
        isTracking = false
        binding.btnStartStop.text = "시작"
        binding.btnStartStop.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#CEFF00"))
        
        val intent = Intent(this, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        }
        startService(intent)
        
        // 여기에 저장 로직 추가 가능
        // viewModel.saveRun(duration)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.distance.collectLatest { distance ->
                binding.tvDistance.text = String.format("%.2f", distance / 1000f)
            }
        }

        lifecycleScope.launch {
            viewModel.currentPace.collectLatest { pace ->
                binding.tvPace.text = pace
            }
        }

        lifecycleScope.launch {
            viewModel.pathPoints.collectLatest { points ->
                if (points.isNotEmpty()) {
                    val polylineOptions = PolylineOptions()
                        .color(Color.parseColor("#CEFF00"))
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
        // map?.setMapStyle(...) // 다크 스타일 생략 (JSON 필요)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            map?.isMyLocationEnabled = true
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

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }
}
