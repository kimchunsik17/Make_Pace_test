package com.example.makepacetestver.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.os.Bundle as AndroidBundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.makepacetestver.R
import com.example.makepacetestver.data.db.AppDatabase
import com.example.makepacetestver.data.db.RunEntity
import com.example.makepacetestver.databinding.FragmentHomeBinding
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
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
        "훈련 강도를 높일 때는 일주일 거리를 10% 이상 늘리지 마세요.",
        "자세가 흐트러지면 부상 위험이 높아집니다. 시선은 정면을 향하세요."
    )

    // 요일 레이블 (월~일)
    private val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGreeting()
        setupTipsViewPager()
        updateWeatherInfo()
        setupCalendar()
        loadAllStats()
        setupPrButtons()

        // 빠른 달리기 시작 — 바텀 네비 탭 선택 방식으로 이동 (백스택 꼬임 방지)
        binding.btnQuickStart.setOnClickListener {
            (requireActivity() as? com.example.makepacetestver.MainActivity)?.selectBottomNavTab(R.id.nav_run)
        }

        // 최근 러닝 카드 클릭 → 상세 기록 (runId는 loadAllStats에서 설정)
        binding.btnCloseCalendar.setOnClickListener {
            hideCalendarAnalysis()
        }
    }

    // ─────────────────────────────────────
    // 시간대별 인사말
    // ─────────────────────────────────────
    private fun setupGreeting() {
        val name = FirebaseAuth.getInstance().currentUser?.displayName?.let { "$it 님" } ?: "러너"
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val (greeting, sub) = when (hour) {
            in 5..11  -> Pair("좋은 아침이에요 🌅", "오늘 아침 달리기 어때요, $name?")
            in 12..16 -> Pair("좋은 오후에요 ☀️", "잠깐 달려볼까요, $name?")
            in 17..20 -> Pair("좋은 저녁이에요 🌆", "저녁 러닝 어때요, $name?")
            else       -> Pair("좋은 밤이에요 🌙", "오늘 하루도 수고했어요, $name!")
        }
        binding.tvGreeting.text = greeting
        binding.tvGreetingSub.text = sub
    }

    // ─────────────────────────────────────
    // 팁 ViewPager2 세팅
    // ─────────────────────────────────────
    private fun setupTipsViewPager() {
        binding.vpTips.adapter = RunningTipAdapter(runningTips)
    }

    // ─────────────────────────────────────
    // 날씨 (더미)
    // ─────────────────────────────────────
    private fun updateWeatherInfo() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            binding.tvTemperature.text = "권한 필요"
            return
        }
        val fusedClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                val dummyTemp = (15..28).random()
                binding.tvTemperature.text = "${dummyTemp}°C"
                binding.ivWeatherIcon.setImageResource(android.R.drawable.ic_menu_report_image)
            } else {
                binding.tvTemperature.text = "--°C"
            }
        }
    }

    // ─────────────────────────────────────
    // 캘린더 설정 (기존 유지)
    // ─────────────────────────────────────
    private fun setupCalendar() {
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth, 0, 0, 0)
            val startOfDay = calendar.timeInMillis
            calendar.set(year, month, dayOfMonth, 23, 59, 59)
            val endOfDay = calendar.timeInMillis

            val runDao = AppDatabase.getDatabase(requireContext()).getRunDao()
            viewLifecycleOwner.lifecycleScope.launch {
                val allRuns = runDao.getAllRuns().first()
                val dayRuns = allRuns.filter { it.timestamp in startOfDay..endOfDay }
                updateSelectedDayUi(year, month, dayOfMonth, dayRuns)
            }
        }
    }

    private fun updateSelectedDayUi(year: Int, month: Int, day: Int, dayRuns: List<RunEntity>) {
        binding.tvSelectedDate.text = "${year}. ${month + 1}. ${day}"
        if (dayRuns.isNotEmpty()) {
            val totalDistance = dayRuns.sumOf { it.distanceMeter.toDouble() }.toFloat()
            binding.tvSelectedDateDistance.text = String.format(Locale.getDefault(), "%.2f km", totalDistance / 1000f)
            binding.tvSelectedDateDesc.text = "총 ${dayRuns.size}회의 활동이 있습니다."
            binding.tvSelectedDateDesc.setTextColor(Color.WHITE)
        } else {
            binding.tvSelectedDateDistance.text = "0.00 km"
            binding.tvSelectedDateDesc.text = "해당 날짜의 활동 기록이 없습니다."
            binding.tvSelectedDateDesc.setTextColor(Color.LTGRAY)
        }
    }

    private fun hideCalendarAnalysis() {
        binding.calendarOverlay.animate()
            .alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(250)
            .withEndAction { binding.calendarOverlay.visibility = View.GONE }
            .start()
    }

    // ─────────────────────────────────────
    // 모든 통계 로드 (한 번에)
    // ─────────────────────────────────────
    private fun loadAllStats() {
        val runDao = AppDatabase.getDatabase(requireContext()).getRunDao()
        viewLifecycleOwner.lifecycleScope.launch {
            val allRuns = runDao.getAllRuns().first()
            updateWeeklyStats(allRuns)
            updateStreakCard(allRuns)
            buildWeeklyBarChart(allRuns)
            updateRecentRun(allRuns)
            updatePRCard(allRuns)
        }
    }

    // ─────────────────────────────────────
    // 주간 거리 + 비교
    // ─────────────────────────────────────
    private fun updateWeeklyStats(runs: List<RunEntity>) {
        val weekStart = thisWeekStartMillis()
        val lastWeekStart = weekStart - 7 * 86_400_000L
        val lastWeekEnd = weekStart - 1

        val thisWeekKm = runs.filter { it.timestamp >= weekStart }
            .sumOf { it.distanceMeter.toDouble() }.toFloat() / 1000f
        val lastWeekKm = runs.filter { it.timestamp in lastWeekStart..lastWeekEnd }
            .sumOf { it.distanceMeter.toDouble() }.toFloat() / 1000f

        binding.tvWeeklyDistance.text = String.format(Locale.getDefault(), "%.2f km", thisWeekKm)

        val diff = thisWeekKm - lastWeekKm
        binding.tvWeeklyComparison.text = when {
            lastWeekKm == 0f && thisWeekKm > 0f -> "이번 주 러닝 시작! 💪"
            diff > 0.05f -> "+${String.format("%.1f", diff)}km 지난주보다 더 달렸어요 ↑"
            diff < -0.05f -> "${String.format("%.1f", -diff)}km 지난주보다 덜 달렸어요 ↓"
            else -> "지난주와 비슷한 페이스예요 🔄"
        }
    }

    // ─────────────────────────────────────
    // 연속 러닝 스트릭
    // ─────────────────────────────────────
    private fun updateStreakCard(runs: List<RunEntity>) {
        val streak = calculateStreak(runs)
        binding.tvStreakDays.text = "${streak}일"
    }

    private fun calculateStreak(runs: List<RunEntity>): Int {
        if (runs.isEmpty()) return 0
        val sdf = SimpleDateFormat("yyyyDDD", Locale.getDefault()) // year + day of year
        val runDays = runs.map { sdf.format(Date(it.timestamp)) }.toSet()

        var streak = 0
        val cal = Calendar.getInstance()
        while (true) {
            val key = sdf.format(cal.time)
            if (!runDays.contains(key)) break
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    // ─────────────────────────────────────
    // 요일별 바 차트
    // ─────────────────────────────────────
    private fun buildWeeklyBarChart(runs: List<RunEntity>) {
        val dailyKm = FloatArray(7) // 0=월, 6=일
        val weekStart = thisWeekStartMillis()

        for (run in runs) {
            if (run.timestamp < weekStart) continue
            val cal = Calendar.getInstance().apply { timeInMillis = run.timestamp }
            // Calendar.DAY_OF_WEEK: 1=일, 2=월, ... 7=토
            val idx = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY    -> 0
                Calendar.TUESDAY   -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY  -> 3
                Calendar.FRIDAY    -> 4
                Calendar.SATURDAY  -> 5
                Calendar.SUNDAY    -> 6
                else               -> -1
            }
            if (idx >= 0) dailyKm[idx] += run.distanceMeter / 1000f
        }

        val weekRuns = runs.filter { it.timestamp >= weekStart }
        val weekRunCount = weekRuns.size
        binding.tvWeekRunCount.text = "총 ${weekRunCount}회"

        // 주간 총 시간
        val totalMs = weekRuns.sumOf { it.durationMillis }
        binding.tvWeekTotalTime.text = if (totalMs > 0) formatDuration(totalMs) else "--"

        // 주간 평균 페이스
        val validPaces = weekRuns.map { parsePaceToSeconds(it.avgPace) }.filter { it in 60..1200 }
        if (validPaces.isNotEmpty()) {
            val avgSec = validPaces.average().toInt()
            binding.tvWeekAvgPace.text = "${avgSec / 60}:${String.format("%02d", avgSec % 60)}"
        } else {
            binding.tvWeekAvgPace.text = "--"
        }

        // 베스트 요일
        val bestIdx = dailyKm.indices.maxByOrNull { dailyKm[it] }
        binding.tvWeekBestDay.text = if (bestIdx != null && dailyKm[bestIdx] > 0f)
            dayLabels[bestIdx] + "요일" else "--"

        val maxKm = dailyKm.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val barContainer = binding.llWeeklyBars
        barContainer.removeAllViews()

        val todayIdx = run {
            val cal = Calendar.getInstance()
            when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY    -> 0
                Calendar.TUESDAY   -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY  -> 3
                Calendar.FRIDAY    -> 4
                Calendar.SATURDAY  -> 5
                Calendar.SUNDAY    -> 6
                else               -> -1
            }
        }

        val dp = resources.displayMetrics.density
        val labelHeightPx = (20 * dp).toInt()   // 레이블용 고정 높이
        val containerHeightPx = (130 * dp).toInt()  // llWeeklyBars layout_height="130dp"
        val barAreaPx = containerHeightPx - labelHeightPx

        for (i in 0..6) {
            val fraction = if (maxKm > 0f) (dailyKm[i] / maxKm).coerceIn(0f, 1f) else 0f
            val isToday = (i == todayIdx)

            // 컬럼 LinearLayout (세로)
            val col = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            }

            // 바 View
            val barHeightPx = ((barAreaPx * fraction).toInt()).coerceAtLeast((3 * dp).toInt())
            val barColor = when {
                isToday -> 0xFF0091EA.toInt()   // sea_blue
                fraction > 0f -> 0xFF81D4FA.toInt() // sea_blue_light
                else -> 0xFF2A2A2A.toInt()
            }
            val barView = View(requireContext()).apply {
                val shape = GradientDrawable().apply {
                    setColor(barColor)
                    cornerRadii = floatArrayOf(4f * dp, 4f * dp, 4f * dp, 4f * dp, 0f, 0f, 0f, 0f) // top rounded
                }
                background = shape
                layoutParams = LinearLayout.LayoutParams(
                    (20 * dp).toInt(), barHeightPx
                ).apply {
                    setMargins(0, 0, 0, (4 * dp).toInt())
                }
            }

            // 레이블 TextView
            val label = TextView(requireContext()).apply {
                text = dayLabels[i]
                textSize = 11f
                setTextColor(if (isToday) 0xFF0091EA.toInt() else 0xFFADADAD.toInt())
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, labelHeightPx
                )
            }

            col.addView(barView)
            col.addView(label)
            barContainer.addView(col)
        }
    }

    // ─────────────────────────────────────
    // 최근 러닝 카드
    // ─────────────────────────────────────
    private fun updateRecentRun(runs: List<RunEntity>) {
        if (runs.isEmpty()) {
            binding.tvNoRunYet.visibility = View.VISIBLE
            binding.tvLastRunDate.text = "아직 기록이 없어요"
            binding.tvLastRunDist.text = "0.00 km"
            binding.tvLastRunPace.text = "--:-- /km"
            binding.tvLastRunDuration.text = "--:--"
            binding.cardRecentRun.setOnClickListener(null)
            return
        }

        val lastRun = runs.maxByOrNull { it.timestamp }!!
        binding.tvNoRunYet.visibility = View.GONE

        val sdf = SimpleDateFormat("M월 d일 (E)", Locale.KOREAN)
        binding.tvLastRunDate.text = sdf.format(Date(lastRun.timestamp))
        binding.tvLastRunDist.text = String.format("%.2f km", lastRun.distanceMeter / 1000f)
        binding.tvLastRunPace.text = if (lastRun.avgPace.isNotEmpty() && lastRun.avgPace != "--:--")
            "${lastRun.avgPace} /km" else "--:-- /km"
        binding.tvLastRunDuration.text = formatDuration(lastRun.durationMillis)

        binding.cardRecentRun.setOnClickListener {
            val bundle = AndroidBundle().apply { putLong("runId", lastRun.id) }
            findNavController().navigate(R.id.runDetailFragment, bundle)
        }
    }

    // ─────────────────────────────────────
    // PR 버튼 세팅
    // ─────────────────────────────────────
    private var allRunsCache: List<RunEntity> = emptyList()

    private fun setupPrButtons() {
        data class PrCategory(val label: String, val minM: Float, val maxM: Float)
        val categories = listOf(
            PrCategory("3K",  2500f,  4999f),
            PrCategory("5K",  4500f,  8999f),
            PrCategory("10K", 8000f, 18999f),
            PrCategory("하프", 18000f, 30000f),
            PrCategory("풀",  38000f, Float.MAX_VALUE)
        )
        val buttons = listOf(
            binding.btnPr3k, binding.btnPr5k, binding.btnPr10k,
            binding.btnPrHalf, binding.btnPrFull
        )

        fun selectCategory(index: Int) {
            // 버튼 색 초기화
            buttons.forEachIndexed { i, btn ->
                val selected = i == index
                btn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (selected) 0xFF0091EA.toInt() else 0xFF2A2A2A.toInt()
                )
                btn.setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFFADADAD.toInt())
            }

            val cat = categories[index]
            val filtered = allRunsCache.filter {
                it.distanceMeter >= cat.minM && it.distanceMeter <= cat.maxM
            }

            binding.tvPrCategoryLabel.text = cat.label

            if (filtered.isEmpty()) {
                binding.tvPrNoData.visibility = View.VISIBLE
                binding.prDataLayout.visibility = View.GONE
            } else {
                binding.tvPrNoData.visibility = View.GONE
                binding.prDataLayout.visibility = View.VISIBLE

                // 최단 시간 (가장 빠른 완주)
                val bestRun = filtered.minByOrNull { it.durationMillis }!!
                binding.tvPrBestTime.text = formatDuration(bestRun.durationMillis)
                binding.tvPrBestPace.text = if (bestRun.avgPace.isNotEmpty()) bestRun.avgPace else "--:--"
                binding.tvPrBestDist.text = String.format("%.2f km", bestRun.distanceMeter / 1000f)

                val sdf = java.text.SimpleDateFormat("yy.MM.dd", java.util.Locale.getDefault())
                binding.tvPrBestDate.text = sdf.format(java.util.Date(bestRun.timestamp))
            }
        }

        buttons.forEachIndexed { i, btn ->
            btn.setOnClickListener { selectCategory(i) }
        }

        // 초기: 3K 선택
        selectCategory(0)
    }

    private fun updatePRCard(runs: List<RunEntity>) {
        allRunsCache = runs
        // 버튼은 setupPrButtons()에서 처리 — 여기선 캐시만 업데이트하면 됨
        // 초기 선택(3K) 재적용
        binding.btnPr3k.performClick()
    }

    // ─────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────
    private fun thisWeekStartMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun parsePaceToSeconds(pace: String): Int {
        val normalized = pace.replace(';', ':').trim()
        val parts = normalized.split(":")
        if (parts.size != 2) return Int.MAX_VALUE
        val min = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
        val sec = parts[1].toIntOrNull() ?: return Int.MAX_VALUE
        return min * 60 + sec
    }

    private fun formatDuration(millis: Long): String {
        val totalSec = millis / 1000
        val hours = totalSec / 3600
        val mins  = (totalSec % 3600) / 60
        val secs  = totalSec % 60
        return if (hours > 0) String.format("%d:%02d:%02d", hours, mins, secs)
        else String.format("%d:%02d", mins, secs)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
