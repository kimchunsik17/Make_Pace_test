package com.example.makepacetestver.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

import com.example.makepacetestver.R
import androidx.core.content.ContextCompat

class ClubFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return TextView(context).apply {
            text = "클럽 화면"
            gravity = Gravity.CENTER
            textSize = 24f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setBackgroundColor(ContextCompat.getColor(context, R.color.dark_background))
        }
    }
}
