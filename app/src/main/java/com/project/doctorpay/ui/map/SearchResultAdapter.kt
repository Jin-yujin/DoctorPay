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
) : ListAdapter<SearchResultAdapter.SearchResult, SearchResultAdapter.ViewHolder>(SearchResultDiffCallback()) {

    data class SearchResult(
        val address: String,
        val latitude: Double,
        val longitude: Double
    ) {
        val location: LatLng get() = LatLng(latitude, longitude)
    }

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

        fun bind(item: SearchResult) {
            binding.addressText.text = item.address
        }
    }

    private class SearchResultDiffCallback : DiffUtil.ItemCallback<SearchResult>() {
        override fun areItemsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean {
            return oldItem.address == newItem.address &&
                    oldItem.latitude == newItem.latitude &&
                    oldItem.longitude == newItem.longitude
        }

        override fun areContentsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean {
            return oldItem == newItem
        }
    }
}