package com.example.makepacetestver.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.makepacetestver.data.db.AppDatabase
import com.example.makepacetestver.data.db.RunEntity
import com.example.makepacetestver.databinding.FragmentRunDetailBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class RunDetailFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentRunDetailBinding? = null
    private val binding get() = _binding!!
    private var runEntity: RunEntity? = null
    private var map: GoogleMap? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRunDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val runId = arguments?.getLong("runId") ?: -1L
        
        binding.detailMapView.onCreate(savedInstanceState)
        binding.detailMapView.getMapAsync(this)

        if (runId != -1L) {
            viewLifecycleOwner.lifecycleScope.launch {
                val run = AppDatabase.getDatabase(requireContext()).getRunDao().getRunById(runId)
                run?.let {
                    runEntity = it
                    displayRunData(it)
                    updateMapIfReady()
                }
            }
        }
    }

    private fun displayRunData(run: RunEntity) {
        val df = SimpleDateFormat("yyyy. MM. dd HH:mm", Locale.getDefault())
        binding.tvDetailDate.text = df.format(Date(run.timestamp))
        binding.tvDetailDistance.text = String.format(Locale.getDefault(), "%.2f km", run.distanceMeter / 1000f)
        binding.tvDetailPace.text = run.avgPace
        
        val seconds = (run.durationMillis / 1000) % 60
        val minutes = (run.durationMillis / (1000 * 60)) % 60
        val hours = (run.durationMillis / (1000 * 60 * 60))
        binding.tvDetailDuration.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        binding.tvDetailElevation.text = String.format(Locale.getDefault(), "%.0fm", run.elevationGain)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        updateMapIfReady()
    }

    private fun updateMapIfReady() {
        val currentMap = map ?: return
        val currentRun = runEntity ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val points = db.getRunDao().getPointsForRun(currentRun.id)
            
            if (points.isNotEmpty()) {
                val latLngPoints = points.map { LatLng(it.latitude, it.longitude) }
                val polylineOptions = PolylineOptions()
                    .color(Color.parseColor("#CEFF00"))
                    .width(12f)
                    .jointType(JointType.ROUND)
                    .addAll(latLngPoints)

                currentMap.addPolyline(polylineOptions)

                val boundsBuilder = LatLngBounds.Builder()
                for (latLng in latLngPoints) {
                    boundsBuilder.include(latLng)
                }
                val bounds = boundsBuilder.build()
                currentMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.detailMapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.detailMapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.detailMapView.onDestroy()
        _binding = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.detailMapView.onLowMemory()
    }
}
