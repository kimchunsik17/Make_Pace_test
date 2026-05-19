package com.example.makepacetestver.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.makepacetestver.data.db.AppDatabase
import com.example.makepacetestver.data.db.RunEntity
import com.example.makepacetestver.databinding.FragmentHomeBinding
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val runningTips = listOf(
        "러닝 전 동적 스트레칭은 부상 방지에 효과적입니다.",
        "일관된 페이스 유지가 지구력 향상의 핵심입니다.",
        "러닝화는 보통 500~800km 주행 후 교체하는 것이 좋습니다.",
        "호흡은 코와 입을 모두 사용하여 리듬감 있게 하세요.",
        "무더운 날씨에는 충분한 수분 섭취가 필수입니다.",
        "내리막길에서는 보폭을 줄여 무릎 충격을 완화하세요.",
        "주 1회는 완전한 휴식을 가져 근육 회복을 돕는 것이 좋습니다.",
        "자세가 흐트러지면 부상 위험이 높아집니다. 시선은 정면을 향하세요.",
        "훈련 강도를 높일 때는 일주일 거리를 10% 이상 늘리지 마세요."
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTipsViewPager()
        loadWeeklyStats()
        updateWeatherInfo()

        binding.btnMonthAnalysis.setOnClickListener {
            showCalendarAnalysis("월간 분석")
        }

        binding.btnYearAnalysis.setOnClickListener {
            showCalendarAnalysis("연간 분석")
        }

        binding.btnCloseCalendar.setOnClickListener {
            hideCalendarAnalysis()
        }
    }

    private fun setupTipsViewPager() {
        val adapter = RunningTipAdapter(runningTips)
        binding.vpTips.adapter = adapter
    }

    private fun loadWeeklyStats() {
        val runDao = AppDatabase.getDatabase(requireContext()).getRunDao()
        viewLifecycleOwner.lifecycleScope.launch {
            val allRuns = runDao.getAllRuns().first()
            calculateAndDisplayStats(allRuns)
        }
    }

    private fun calculateAndDisplayStats(runs: List<RunEntity>) {
        val thisWeekStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis

        val lastWeekStart = thisWeekStart - (7 * 24 * 60 * 60 * 1000L)
        val lastWeekEnd = thisWeekStart - 1

        val thisWeekDistance = runs.filter { it.timestamp >= thisWeekStart }
            .sumOf { it.distanceMeter.toDouble() }.toFloat()

        val lastWeekDistance = runs.filter { it.timestamp in lastWeekStart..lastWeekEnd }
            .sumOf { it.distanceMeter.toDouble() }.toFloat()

        binding.tvWeeklyDistance.text = String.format(Locale.getDefault(), "%.2f km", thisWeekDistance / 1000f)

        val diff = (thisWeekDistance - lastWeekDistance) / 1000f
        val locale = Locale.getDefault()
        val comparisonText = when {
            lastWeekDistance == 0f && thisWeekDistance > 0f -> "멋진 시작입니다! 이번 주 러닝을 시작하셨네요."
            diff > 0 -> "지난주보다 ${String.format(locale, "%.1f", diff)}km 더 달리셨습니다. 대단해요!"
            diff < 0 -> "지난주보다 ${String.format(locale, "%.1f", -diff)}km 덜 달리셨습니다. 힘내세요!"
            else -> "지난주와 동일한 거리를 달리고 있습니다. 꾸준함이 정답입니다!"
        }
        binding.tvWeeklyComparison.text = comparisonText
    }

    private fun updateWeatherInfo() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            binding.tvTemperature.text = "위치 권한 필요"
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                // 실제 날씨 API(OpenWeatherMap 등) 연동 지점
                // 현재는 가상 환경이므로 랜덤 더미 데이터를 표시합니다.
                val dummyTemp = (15..28).random()
                binding.tvTemperature.text = "${dummyTemp}°C"
                binding.ivWeatherIcon.setImageResource(android.R.drawable.ic_menu_report_image) // 날씨 아이콘
            } else {
                binding.tvTemperature.text = "날씨 정보 없음"
            }
        }
    }

    private fun showCalendarAnalysis(title: String) {
        binding.tvCalendarTitle.text = title
        binding.calendarOverlay.visibility = View.VISIBLE
        binding.calendarOverlay.alpha = 0f
        binding.calendarOverlay.scaleX = 0.8f
        binding.calendarOverlay.scaleY = 0.8f

        binding.calendarOverlay.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .start()
    }

    private fun hideCalendarAnalysis() {
        binding.calendarOverlay.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(250)
            .withEndAction {
                binding.calendarOverlay.visibility = View.GONE
            }
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
