package com.example.makepacetestver.ui

import android.content.Intent
import android.graphics.Color
import android.location.Location
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
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
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
        binding.btnShareToClub.setOnClickListener { shareToClub() }

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
        if (points.size < 2) {
            binding.paceChart.visibility = View.GONE
            binding.tvChartEmpty.visibility = View.VISIBLE
            return
        }

        // ── 1. GPS 포인트를 1km 구간 버킷으로 그루핑 ──────────────────
        data class KmBucket(
            val actualPaces: MutableList<Float> = mutableListOf(),
            val predictedPaces: MutableList<Float> = mutableListOf()
        )

        val buckets = mutableMapOf<Int, KmBucket>()
        var cumulativeDistanceM = 0.0

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]

            // 두 GPS 포인트 간 거리 (m)
            val dist = FloatArray(1)
            Location.distanceBetween(
                prev.latitude, prev.longitude,
                curr.latitude, curr.longitude,
                dist
            )
            cumulativeDistanceM += dist[0]

            val kmIdx = (cumulativeDistanceM / 1000).toInt()
            val bucket = buckets.getOrPut(kmIdx) { KmBucket() }

            // 실제 페이스: 순간 속도 → 분/km 변환 (2분~15분 범위만 유효)
            if (curr.instantaneousSpeed > 0.5f) {
                val paceSec = 1000f / curr.instantaneousSpeed
                if (paceSec in 120f..900f) {
                    bucket.actualPaces.add(paceSec / 60f)
                }
            }

            // AI 예측 페이스
            curr.predictedPace?.let { p ->
                if (p in 2f..15f) bucket.predictedPaces.add(p)
            }
        }

        if (buckets.isEmpty()) {
            binding.paceChart.visibility = View.GONE
            binding.tvChartEmpty.visibility = View.VISIBLE
            return
        }

        // ── 2. 버킷별 평균 → Entry 리스트 생성 ───────────────────────
        val actualEntries   = mutableListOf<Entry>()
        val predictedEntries = mutableListOf<Entry>()
        val xLabels         = mutableListOf<String>()

        buckets.keys.sorted().forEachIndexed { idx, kmIdx ->
            val bucket = buckets[kmIdx]!!
            xLabels.add("${kmIdx + 1}km")

            if (bucket.actualPaces.isNotEmpty()) {
                actualEntries.add(Entry(idx.toFloat(), bucket.actualPaces.average().toFloat()))
            }
            if (bucket.predictedPaces.isNotEmpty()) {
                predictedEntries.add(Entry(idx.toFloat(), bucket.predictedPaces.average().toFloat()))
            }
        }

        // ── 3. 데이터셋 스타일링 ──────────────────────────────────────
        val colorActual    = Color.parseColor("#0091EA")   // sea_blue
        val colorPredicted = Color.parseColor("#81D4FA")   // sea_blue_light
        val dark           = Color.parseColor("#1E1E1E")

        val actualDataSet = LineDataSet(actualEntries, "실제 페이스").apply {
            color = colorActual
            lineWidth = 2.5f
            setDrawCircles(true)
            circleRadius = 4f
            setCircleColor(colorActual)
            circleHoleColor = dark
            circleHoleRadius = 2f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillAlpha = 25
            fillColor = colorActual
            setDrawValues(false)
        }

        val predictedDataSet = LineDataSet(predictedEntries, "AI 예측 페이스").apply {
            color = colorPredicted
            lineWidth = 2f
            setDrawCircles(false)
            enableDashedLine(14f, 6f, 0f)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawValues(false)
        }

        // ── 4. Y축: float → "M:SS" 포매터 ────────────────────────────
        val paceFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val totalSec = (value * 60).toInt()
                return "%d'%02d\"".format(totalSec / 60, totalSec % 60)
            }
        }

        // ── 5. 차트 최종 설정 ─────────────────────────────────────────
        binding.paceChart.apply {
            visibility = View.VISIBLE
            data = LineData(actualDataSet, predictedDataSet)
            description.isEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setNoDataText("페이스 데이터가 없습니다")

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#ADADAD")
                textSize = 11f
                valueFormatter = IndexAxisValueFormatter(xLabels)
                granularity = 1f
                labelCount = xLabels.size.coerceAtMost(8)
            }

            axisRight.isEnabled = false
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#1AFFFFFF")
                textColor = Color.parseColor("#ADADAD")
                textSize = 11f
                valueFormatter = paceFormatter
                setLabelCount(5, false)
                // 페이스: 낮을수록(빠를수록) 위에 표시
                isInverted = true
            }

            legend.apply {
                textColor = Color.parseColor("#ADADAD")
                textSize = 12f
            }

            animateX(900)
            invalidate()
        }
    }

    private fun shareToClub() {
        val run = runEntity ?: return
        val distanceKm = run.distanceMeter / 1000.0
        val sheet = ShareToClubBottomSheet.newInstance(
            runId = run.id,
            distanceKm = distanceKm,
            avgPace = run.avgPace,
            durationMillis = run.durationMillis
        )
        sheet.show(parentFragmentManager, "share_to_club")
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
        _binding?.detailMapView?.onLowMemory()
    }
}
