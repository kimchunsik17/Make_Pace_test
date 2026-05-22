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

class RunHistoryAdapter(private val onClick: (RunEntity) -> Unit) :
    ListAdapter<RunEntity, RunHistoryAdapter.RunViewHolder>(RunDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunViewHolder {
        val binding = ItemRunHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RunViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RunViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RunViewHolder(private val binding: ItemRunHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(run: RunEntity) {
            val df = SimpleDateFormat("yyyy. MM. dd HH:mm", Locale.getDefault())
            binding.tvHistoryDate.text = df.format(Date(run.timestamp))
            binding.tvHistoryDistance.text = String.format(Locale.getDefault(), "%.2f km", run.distanceMeter / 1000f)
            binding.tvHistoryPace.text = run.avgPace
            
            binding.root.setOnClickListener { onClick(run) }
        }
    }

    class RunDiffCallback : DiffUtil.ItemCallback<RunEntity>() {
        override fun areItemsTheSame(oldItem: RunEntity, newItem: RunEntity): Boolean =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: RunEntity, newItem: RunEntity): Boolean =
            oldItem == newItem
    }
}
