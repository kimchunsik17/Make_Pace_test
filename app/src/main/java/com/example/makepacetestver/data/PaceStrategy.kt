package com.example.makepacetestver.data

data class PaceStrategy(
    val id: String,
    val title: String,
    val paceDescription: String,
    val effect: String,
    val targetOrCaution: String,
    val colorHex: String,
    val basePaceMinutes: Int,
    val tolerancePercentage: Float = 0.1f,
    var customTargetDistanceKm: Float? = null // 사용자가 입력한 목표 거리 저장용
)

object StrategyProvider {
    val strategies = listOf(
        PaceStrategy(
            "zone2",
            "존 2 러닝 (Zone 2 Training)",
            "옆 사람과 편안하게 대화할 수 있는 수준 (최대 심박수의 60~70%). 약간 숨이 차지만 콧노래를 부를 수 있는 정도입니다.",
            "탄수화물보다 지방을 주 에너지원으로 사용하기 때문에 체지방 감량에 가장 효과적입니다. 또한 모세혈관과 미토콘드리아를 발달시켜 부상 없이 '기초 심폐지구력(유산소 베이스)'을 탄탄하게 만들어 줍니다.",
            "다이어트 목적, 러닝 입문자, 또는 고강도 훈련 후 회복이 필요한 러너.",
            "#81D4FA", // Lightest
            7
        ),
        PaceStrategy(
            "progressive",
            "프로그레시브 런 (Progressive Run / 빌드업)",
            "아주 느린 워밍업 페이스로 시작해, 1~2km마다 10~15초씩 속도를 조금씩 높여 마지막 1~2km에서는 자신의 최고 속도에 가깝게 질주하며 마무리합니다.",
            "근육과 관절이 서서히 예열되므로 부상 방지에 탁월하며, 후반부에 지쳐있을 때 페이스를 끌어올리는 멘탈과 스퍼트 능력을 기를 수 있습니다.",
            "부상 방지를 원하는 러너 및 후반 스퍼트 훈련이 필요한 러너.",
            "#4FC3F7", // Light Blue
            6
        ),
        PaceStrategy(
            "fartlek",
            "파틀렉 (Fartlek)",
            "정해진 시간이나 거리 없이 본인의 기분, 지형(오르막, 내리막, 커브 등), 주변 지물에 맞춰 속도를 마음대로 조절합니다.",
            "인터벌 트레이닝의 효과를 가져가면서도 훈련의 지루함을 없애고 실전 러닝 감각을 키우는 데 좋습니다.",
            "형식에 얽매이지 않고 자유롭게 달리고 싶은 모든 러너.",
            "#03A9F4", // Medium Blue
            6
        ),
        PaceStrategy(
            "tempo",
            "템포 런 (Tempo Run)",
            "'약간 힘들다'고 느껴지며, 한두 단어로 짧은 대답만 가능한 속도 (최대 심박수의 80~85%). 보통 20~40분 정도 지속합니다.",
            "근육에 피로 물질(젖산)이 쌓이는 시점인 '젖산 역치'를 늦춰줍니다. 즉, 더 빠른 속도로 더 오래 달릴 수 있는 근지구력과 스태미나를 길러줍니다.",
            "10km나 하프 마라톤 등 대회 기록 단축을 목표로 하는 러너.",
            "#0288D1", // Darker Blue
            5
        ),
        PaceStrategy(
            "interval",
            "인터벌 트레이닝 (Interval Training)",
            "전력 질주(최대 심박수의 80~95%) 1~2분 후, 가벼운 조깅이나 걷기(휴식) 1~2분을 한 세트로 묶어 5~8회 반복합니다.",
            "심폐지구력의 지표인 최대산소섭취량(VO2 Max)을 획기적으로 높여줍니다. 짧은 시간 안에 엄청난 칼로리를 소모하며, 운동이 끝난 후에도 몸이 회복하면서 계속해서 칼로리를 태우는 애프터번(EPOC) 효과를 누릴 수 있습니다.",
            "부상 위험이 높으므로 충분한 웜업이 필수이며, 주 1~2회만 실시하는 것이 좋습니다.",
            "#01579B", // Deepest
            4
        )
    )
}
