package com.example.makepacetestver.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.makepacetestver.data.db.AppDatabase
import com.example.makepacetestver.data.db.RunEntity
import com.example.makepacetestver.databinding.FragmentHomeBinding
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
        "주 1회는 완전한 휴식을 가져 근육 회복을 돕는 것이 좋습니다."
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadWeeklyStats()
        displayRandomTip()

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

    private fun loadWeeklyStats() {
        val runDao = AppDatabase.getDatabase(requireContext()).getRunDao()
        viewLifecycleOwner.lifecycleScope.launch {
            val allRuns = runDao.getAllRuns().first()
            calculateAndDisplayStats(allRuns)
        }
    }

    private fun calculateAndDisplayStats(runs: List<RunEntity>) {
        // 이번 주 시작 (월요일 기준)
        val thisWeekStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis

        // 지난 주 시작 및 종료
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

    private fun displayRandomTip() {
        val randomTip = runningTips.random()
        binding.tvTipContent.text = randomTip
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
