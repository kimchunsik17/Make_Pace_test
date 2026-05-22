package com.example.makepacetestver.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.makepacetestver.data.db.AppDatabase
import com.example.makepacetestver.data.db.RunEntity
import com.example.makepacetestver.data.db.RunPointEntity
import com.example.makepacetestver.databinding.FragmentRunDetailBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
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

        binding.btnShare.setOnClickListener { shareRun() }
        binding.btnDelete.setOnClickListener { confirmDelete() }

        if (runId != -1L) {
            viewLifecycleOwner.lifecycleScope.launch {
                val db = AppDatabase.getDatabase(requireContext())
                val run = db.getRunDao().getRunById(runId)
                val points = db.getRunDao().getPointsForRun(runId)
                
                run?.let {
                    runEntity = it
                    displayRunData(it)
                    setupChart(points)
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

    private fun setupChart(points: List<RunPointEntity>) {
        if (points.isEmpty()) return

        val actualEntries = mutableListOf<Entry>()
        val predictedEntries = mutableListOf<Entry>()

        points.forEachIndexed { index, point ->
            // 실제 페이스 (speed -> pace min/km)
            val actualPace = if (point.instantaneousSpeed > 0.1) {
                (1000f / point.instantaneousSpeed) / 60f
            } else 0f
            
            if (actualPace > 0 && actualPace < 25) {
                actualEntries.add(Entry(index.toFloat(), actualPace))
            }

            // AI 예측 페이스
            point.predictedPace?.let { predicted ->
                predictedEntries.add(Entry(index.toFloat(), predicted))
            }
        }

        val actualDataSet = LineDataSet(actualEntries, "Actual Pace").apply {
            color = Color.parseColor("#0091EA")
            setDrawCircles(false)
            lineWidth = 2f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val predictedDataSet = LineDataSet(predictedEntries, "AI Predicted").apply {
            color = Color.parseColor("#80FFFFFF") // Semi-transparent white
            setDrawCircles(false)
            lineWidth = 2f
            enableDashedLine(10f, 10f, 0f)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        binding.paceChart.apply {
            data = LineData(actualDataSet, predictedDataSet)
            description.isEnabled = false
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#ADADAD")
            }
            
            axisRight.isEnabled = false
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#33FFFFFF")
                textColor = Color.parseColor("#ADADAD")
            }
            
            legend.textColor = Color.WHITE

            animateX(1000)
            invalidate()
        }
    }

    private fun shareRun() {
        val run = runEntity ?: return
        val shareText = """
            🏃 Make Pace 러닝 기록
            날짜: ${binding.tvDetailDate.text}
            거리: ${binding.tvDetailDistance.text}
            페이스: ${run.avgPace}
            시간: ${binding.tvDetailDuration.text}
            상승 고도: ${run.elevationGain.toInt()}m
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "러닝 기록 공유"))
    }

    private fun confirmDelete() {
        AlertDialog.Builder(requireContext())
            .setTitle("기록 삭제")
            .setMessage("이 러닝 기록을 정말 삭제할까요?")
            .setPositiveButton("삭제") { _, _ -> deleteRun() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteRun() {
        val runId = runEntity?.id ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).getRunDao().deleteRun(runId)
            Toast.makeText(requireContext(), "기록이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
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
                    .color(Color.parseColor("#0091EA"))
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
