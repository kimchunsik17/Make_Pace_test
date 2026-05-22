package com.example.makepacetestver.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "run_points",
    foreignKeys = [ForeignKey(
        entity = RunEntity::class,
        parentColumns = ["id"],
        childColumns = ["runId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class RunPointEntity(
    @PrimaryKey(autoGenerate = true) val pointId: Long = 0,
    val runId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val instantaneousSpeed: Float,
    val predictedPace: Float? = null // AI 예측값 저장용 추가
)
