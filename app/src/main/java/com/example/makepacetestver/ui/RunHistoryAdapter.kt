package com.example.makepacetestver.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.makepacetestver.data.db.RunEntity
import com.example.makepacetestver.databinding.ItemRunHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class RunHistoryAdapter(private val onItemClick: (RunEntity) -> Unit) : ListAdapter<RunEntity, RunHistoryAdapter.RunViewHolder>(RunDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunViewHolder {
        val binding = ItemRunHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RunViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: RunViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RunViewHolder(
        private val binding: ItemRunHistoryBinding,
        private val onItemClick: (RunEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(run: RunEntity) {
            binding.root.setOnClickListener { onItemClick(run) }
            val df = SimpleDateFormat("yyyy. MM. dd HH:mm", Locale.getDefault())
            binding.tvDate.text = df.format(Date(run.timestamp))
            binding.tvDistance.text = String.format("%.2f km", run.distanceMeter / 1000f)
            binding.tvPace.text = "페이스: ${run.avgPace}"
            
            val seconds = (run.durationMillis / 1000) % 60
            val minutes = (run.durationMillis / (1000 * 60)) % 60
            val hours = (run.durationMillis / (1000 * 60 * 60))
            binding.tvDuration.text = String.format("시간: %02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    class RunDiffCallback : DiffUtil.ItemCallback<RunEntity>() {
        override fun areItemsTheSame(oldItem: RunEntity, newItem: RunEntity): Boolean {
            return oldItem.id == newItem.id
        }
        override fun areContentsTheSame(oldItem: RunEntity, newItem: RunEntity): Boolean {
            return oldItem == newItem
        }
    }
}
