package com.project.doctorpay.ui.Detail

import NonPaymentItem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.doctorpay.R
import com.project.doctorpay.databinding.ItemNonCoveredFullBinding

class NonCoveredItemsAdapter : ListAdapter<NonPaymentItem, NonCoveredItemsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNonCoveredFullBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    class ViewHolder(
        private val binding: ItemNonCoveredFullBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NonPaymentItem) {
            binding.apply {
                // 주요 항목명 표시
                categoryTitleTextView.text = item.npayKorNm ?: "항목명 없음"

                // 상세 설명 표시 (있는 경우)
                itemNameTextView.text = item.itemNm?.takeIf { it != item.npayKorNm } ?: ""

                // 금액 표시
                amountTextView.text = item.curAmt?.let { amt ->
                    if (amt.isNotEmpty()) {
                        amt.toIntOrNull()?.let { value ->
                            formatAmount(value)
                        } ?: "금액 정보 없음"
                    } else {
                        "금액 정보 없음"
                    }
                } ?: "금액 정보 없음"

                // 기준일자 표시
                dateTextView.text = item.adtFrDd?.let { date ->
                    try {
                        if (date.length >= 8) {
                            val year = date.substring(0, 4)
                            val month = date.substring(4, 6)
                            val day = date.substring(6, 8)
                            "$year.$month.$day"
                        } else {
                            date
                        }
                    } catch (e: Exception) {
                        date
                    }
                } ?: "날짜 정보 없음"
            }
        }

        private fun formatAmount(amount: Int): String {
            return String.format("%,d원", amount)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<NonPaymentItem>() {
        override fun areItemsTheSame(oldItem: NonPaymentItem, newItem: NonPaymentItem): Boolean {
            return oldItem.itemCd == newItem.itemCd &&
                    oldItem.npayKorNm == newItem.npayKorNm
        }

        override fun areContentsTheSame(oldItem: NonPaymentItem, newItem: NonPaymentItem): Boolean {
            return oldItem == newItem
        }
    }
}