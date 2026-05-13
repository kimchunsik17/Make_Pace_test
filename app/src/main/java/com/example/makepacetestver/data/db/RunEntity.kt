package com.example.makepacetestver.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "running_table")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,          // 시작 시간
    val avgPace: String,          // 평균 페이스
    val distanceMeter: Float,     // 총 거리
    val durationMillis: Long,     // 소요 시간
    val pathPointsJson: String    // 좌표 리스트 (JSON 변환 저장)
)
