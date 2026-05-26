package com.example.makepacetestver.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.makepacetestver.data.db.RunDao

class RunningViewModelFactory(private val runDao: RunDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RunningViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RunningViewModel(runDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
