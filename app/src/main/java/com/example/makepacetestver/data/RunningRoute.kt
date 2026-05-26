package com.example.makepacetestver.data

data class RunningRoute(
    val id: String = "",
    val title: String = "",
    val pros: String = "",
    val cons: String = "",
    val distanceKm: Double = 0.0,
    val avgPace: String = "",                   // 기록자의 평균 페이스 (참고용)
    val durationMillis: Long = 0L,              // 기록 시간
    val crowdingLevel: String = "중",           // 상, 중, 하
    val recommendedTimes: List<String> = emptyList(), // 새벽, 오전, 오후, 저녁, 야간
    val tags: List<String> = emptyList(),       // 바닷길, 산길, 공원, 강변, 도심, 야경
    val likeCount: Int = 0,
    val likedBy: List<String> = emptyList(),
    val authorId: String = "",
    val authorName: String = "",
    val createdAt: Long = 0L,
    // 실제 GPS 경로 포인트 (다운샘플링된 좌표 목록)
    val routePoints: List<Map<String, Double>> = emptyList()
)
