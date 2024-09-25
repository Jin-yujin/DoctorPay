package com.project.doctorpay

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project.doctorpay.databinding.ItemNonPaymentBinding
import com.project.doctorpay.api.Item

class NonPaymentAdapter : RecyclerView.Adapter<NonPaymentAdapter.ViewHolder>() {
    private var items: List<Item> = emptyList()

    fun setItems(newItems: List<Item>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemNonPaymentBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class ViewHolder(private val binding: ItemNonPaymentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.hospitalNameTextView.text = item.hospitalName
            binding.itemNameTextView.text = item.itemName
            binding.priceRangeTextView.text = "${item.minPrice} - ${item.maxPrice}원"
        }
    }
}