package com.example.makepacetestver.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.makepacetestver.databinding.ItemRunningTipBinding

class RunningTipAdapter(private val tips: List<String>) : RecyclerView.Adapter<RunningTipAdapter.TipViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TipViewHolder {
        val binding = ItemRunningTipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TipViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TipViewHolder, position: Int) {
        holder.bind(tips[position])
    }

    override fun getItemCount(): Int = tips.size

    class TipViewHolder(private val binding: ItemRunningTipBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tip: String) {
            binding.tvTipText.text = tip
        }
    }
}
