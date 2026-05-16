package com.example.makepacetestver.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.makepacetestver.data.PaceStrategy
import com.example.makepacetestver.databinding.ItemStrategyCardBinding

class PaceStrategyAdapter(
    private val strategies: List<PaceStrategy>,
    private val onSelect: (PaceStrategy) -> Unit
) : RecyclerView.Adapter<PaceStrategyAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStrategyCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val strategy = strategies[position]
        holder.bind(strategy)
    }

    override fun getItemCount() = strategies.size

    inner class ViewHolder(private val binding: ItemStrategyCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(strategy: PaceStrategy) {
            binding.tvStrategyTitle.text = strategy.title
            binding.tvPaceDesc.text = strategy.paceDescription
            binding.tvEffect.text = "효과: ${strategy.effect}"
            binding.tvTarget.text = "대상: ${strategy.target}"
            binding.layoutBackground.setBackgroundColor(Color.parseColor(strategy.colorHex))
            
            binding.btnSelect.setOnClickListener { onSelect(strategy) }
        }
    }
}
