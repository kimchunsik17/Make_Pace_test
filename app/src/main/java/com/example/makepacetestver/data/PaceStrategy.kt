package com.example.makepacetestver.data

data class PaceStrategy(
    val id: String,
    val title: String,
    val paceDescription: String,
    val effect: String,
    val target: String,
    val colorHex: String,
    val basePaceMinutes: Int,
    val tolerancePercentage: Float = 0.1f
)

object StrategyProvider {
    val strategies = listOf(
        PaceStrategy(
            "recovery", "리커버리 런", "가장 편안한 페이스",
            "지구력 증진 및 회복", "모든 러너", "#81D4FA", 7 // 바다 느낌 라이트 블루
        ),
        PaceStrategy(
            "tempo", "템포 런", "약간 숨이 찬 페이스",
            "심폐 지구력 강화", "중급자 이상", "#0288D1", 5 // 미디엄 블루
        ),
        PaceStrategy(
            "speed", "스피드 런", "전력 질주에 가까운 페이스",
            "속도 및 근력 향상", "상급자", "#01579B", 4 // 다크 블루
        )
    )
}
