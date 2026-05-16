package com.example.makepacetestver.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return TextView(context).apply {
            text = "홈 화면"
            gravity = Gravity.CENTER
            textSize = 24f
        }
    }
}

class PlanFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return TextView(context).apply {
            text = "플랜 화면"
            gravity = Gravity.CENTER
            textSize = 24f
        }
    }
}

class ClubFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return TextView(context).apply {
            text = "클럽 화면"
            gravity = Gravity.CENTER
            textSize = 24f
        }
    }
}
