package com.project.doctorpay.ui.map

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.databinding.ItemSearchResultBinding

class SearchResultAdapter(
    private val onItemClick: (LatLng) -> Unit
) : ListAdapter<MapSearchComponent.SearchResult, SearchResultAdapter.ViewHolder>(SearchResultDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position).location)
                }
            }
        }

        fun bind(item: MapSearchComponent.SearchResult) {
            binding.addressText.text = item.address
        }
    }

    private class SearchResultDiffCallback : DiffUtil.ItemCallback<MapSearchComponent.SearchResult>() {
        override fun areItemsTheSame(
            oldItem: MapSearchComponent.SearchResult,
            newItem: MapSearchComponent.SearchResult
        ): Boolean {
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(
            oldItem: MapSearchComponent.SearchResult,
            newItem: MapSearchComponent.SearchResult
        ): Boolean {
            return oldItem == newItem
        }
    }
}