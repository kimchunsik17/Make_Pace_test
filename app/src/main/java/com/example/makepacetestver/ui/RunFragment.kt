package com.example.makepacetestver.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.makepacetestver.data.db.AppDatabase
import com.example.makepacetestver.databinding.FragmentRunBinding
import com.example.makepacetestver.service.TrackingService
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RunFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentRunBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: RunningViewModel
    private var map: GoogleMap? = null
    private var isTracking = false
    private var isWaitingForPermissions = false

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
                startTracking()
            }
        } else {
            startTracking()
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
                startTracking()
            }
            .show()
    }

    private val requestBackgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(requireContext(), "백그라운드 위치 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
        }
        startTracking()
    }

    private fun startTracking() {
        isTracking = true
        binding.btnStartStop.text = "중지"
        binding.btnStartStop.backgroundTintList = ColorStateList.valueOf(Color.RED)
        
        binding.headerLayout.visibility = View.GONE
        binding.tvGoalSetting.visibility = View.GONE
        binding.trackingStatsLayout.visibility = View.VISIBLE
        
        val intent = Intent(requireContext(), TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
        }
        requireActivity().startService(intent)
    }

    private fun stopTracking() {
        isTracking = false
        binding.btnStartStop.text = "시작"
        binding.btnStartStop.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFEB3B"))
        
        binding.headerLayout.visibility = View.VISIBLE
        binding.tvGoalSetting.visibility = View.VISIBLE
        binding.trackingStatsLayout.visibility = View.GONE
        
        val intent = Intent(requireContext(), TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        }
        requireActivity().startService(intent)
        
        viewModel.saveRun(0L) // 실제 타이머 로직 필요
        Toast.makeText(requireContext(), "러닝 기록이 저장되었습니다.", Toast.LENGTH_SHORT).show()
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
            viewModel.elevationGain.collectLatest { elevation ->
                binding.tvElevation.text = String.format("%.0fm", elevation)
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
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) 
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
