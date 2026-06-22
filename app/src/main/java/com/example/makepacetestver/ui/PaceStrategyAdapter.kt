package com.example.makepacetestver.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.makepacetestver.data.PaceStrategy
import com.example.makepacetestver.databinding.ItemStrategyCardBinding

class PaceStrategyAdapter(
    private val strategies: List<PaceStrategy>,
    private val onSelect: (PaceStrategy) -> Unit
) : RecyclerView.Adapter<PaceStrategyAdapter.ViewHolder>() {

    private var expandedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStrategyCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val strategy = strategies[position]
        holder.bind(strategy, position == expandedPosition)
    }

    override fun getItemCount() = strategies.size

    private fun parsePaceStr(s: String): Int {
        val parts = s.split(":")
        if (parts.size != 2) return 0
        val min = parts[0].toIntOrNull() ?: return 0
        val sec = parts[1].toIntOrNull() ?: return 0
        if (min !in 1..20 || sec !in 0..59) return 0
        return min * 60 + sec
    }

    inner class ViewHolder(private val binding: ItemStrategyCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(strategy: PaceStrategy, isExpanded: Boolean) {
            binding.tvStrategyTitle.text = strategy.title
            binding.tvShortTarget.text = "추천 대상 : ${strategy.targetOrCaution}"
            binding.tvPaceDesc.text = strategy.paceDescription
            binding.tvEffect.text = strategy.effect
            
            // 다크 모드에서도 가독성이 좋은 배경 적용
            binding.layoutBackground.setBackgroundColor(Color.parseColor(strategy.colorHex))
            
            // 확장/축소 상태 반영
            binding.layoutExpanded.visibility = if (isExpanded) View.VISIBLE else View.GONE
            
            // 카드 전체 클릭 시 확장/축소 토글
            binding.root.setOnClickListener {
                val currentPosition = adapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener

                val prevExpanded = expandedPosition
                expandedPosition = if (isExpanded) -1 else currentPosition
                
                notifyItemChanged(prevExpanded)
                notifyItemChanged(expandedPosition)
            }

            binding.btnSelect.setOnClickListener {
                val distanceStr = binding.etTargetDistance.text.toString()
                val distance = distanceStr.toFloatOrNull()
                if (distance == null || distance <= 0f) {
                    Toast.makeText(binding.root.context, "목표 거리를 입력해주세요. (예: 5.0)", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val paceStr = binding.etTargetPace.text.toString().trim().replace(';', ':')
                val paceSec = if (paceStr.isNotEmpty()) parsePaceStr(paceStr) else 0
                if (paceStr.isNotEmpty() && paceSec <= 0) {
                    Toast.makeText(binding.root.context, "페이스 형식이 올바르지 않아요. (예: 6:00)", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                strategy.customTargetDistanceKm = distance
                strategy.customTargetPaceSec = if (paceSec > 0) paceSec else null
                onSelect(strategy)
            }
        }
    }
}
