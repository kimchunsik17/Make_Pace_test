package com.example.makepacetestver.data

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    fun saveProfile(researchId: String, age: Int, height: Float, weight: Float, gender: String) {
        prefs.edit().apply {
            putString("researchId", researchId)
            putInt("age", age)
            putFloat("height", height)
            putFloat("weight", weight)
            putString("gender", gender)
            apply()
        }
    }

    fun getResearchId(): String? = prefs.getString("researchId", null)
    fun getAge(): Int = prefs.getInt("age", 0)
    fun getHeight(): Float = prefs.getFloat("height", 0f)
    fun getWeight(): Float = prefs.getFloat("weight", 0f)
    fun getGender(): String = prefs.getString("gender", "unspecified") ?: "unspecified"

    fun saveLastStrategyId(strategyId: String) {
        prefs.edit().putString("last_strategy_id", strategyId).apply()
    }

    fun getLastStrategyId(): String? = prefs.getString("last_strategy_id", null)

    fun clear() {
        prefs.edit().clear().apply()
    }
}
