package com.project.doctorpay

import NonPaymentItem
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.doctorpay.databinding.ItemNonPaymentBinding

class NonPaymentAdapter : ListAdapter<NonPaymentItem, NonPaymentAdapter.ViewHolder>(NonPaymentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemNonPaymentBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemNonPaymentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NonPaymentItem) {
            binding.hospitalNameTextView.text = item.yadmNm
            binding.itemNameTextView.text = item.itemNm
            binding.priceRangeTextView.text = "${item.cntrImpAmtMin ?: "N/A"} - ${item.cntrImpAmtMax ?: "N/A"}원"
        }
    }

    private class NonPaymentDiffCallback : DiffUtil.ItemCallback<NonPaymentItem>() {
        override fun areItemsTheSame(oldItem: NonPaymentItem, newItem: NonPaymentItem): Boolean {
            return oldItem.yadmNm == newItem.yadmNm && oldItem.itemNm == newItem.itemNm
        }

        override fun areContentsTheSame(oldItem: NonPaymentItem, newItem: NonPaymentItem): Boolean {
            return oldItem == newItem
        }
    }
}