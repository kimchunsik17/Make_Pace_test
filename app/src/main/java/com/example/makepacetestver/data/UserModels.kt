package com.example.makepacetestver.data

// 개인 식별 정보 (보안 구역 저장용)
data class UserProfile(
    val uid: String,
    val email: String,
    val displayName: String?,
    val researchId: String // 익명화된 매칭용 ID
)

// 머신러닝용 익명 데이터 (공용 구역 저장용)
data class AnonymousResearchData(
    val researchId: String,
    val age: Int,
    val gender: String,
    val height: Float,
    val weight: Float
)
