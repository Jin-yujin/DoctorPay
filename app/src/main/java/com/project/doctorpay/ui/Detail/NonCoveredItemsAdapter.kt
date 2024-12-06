package com.project.doctorpay.ui.Detail

import NonPaymentItem
import android.animation.ValueAnimator
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
    private var expandedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNonCoveredFullBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, position == expandedPosition) {
            // Handle expansion/collapse
            val previousExpanded = expandedPosition
            expandedPosition = if (expandedPosition == position) -1 else position

            if (previousExpanded >= 0) {
                notifyItemChanged(previousExpanded)
            }
            notifyItemChanged(position)
        }
    }

    class ViewHolder(
        private val binding: ItemNonCoveredFullBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NonPaymentItem, isExpanded: Boolean, onClick: () -> Unit) {
            binding.apply {
                // Main information
                categoryTitleTextView.text = item.npayKorNm ?: "항목명 없음"
                itemNameTextView.text = item.itemNm?.takeIf { it != item.npayKorNm } ?: ""
                amountTextView.text = formatAmount(item.curAmt)
                dateTextView.text = formatDate(item.adtFrDd)

                detailsContainer.isVisible = isExpanded

                if (isExpanded) {
                    categoryCodeTextView.text = "분류코드: ${item.clCd ?: "없음"}"
                    itemCodeTextView.text = "항목코드: ${item.itemCd ?: "없음"}"
                    validPeriodTextView.text = "유효기간: ${formatDate(item.adtFrDd)} ~ ${formatDate(item.adtEndDd)}"
                    notesTextView.text = "특이사항: ${item.spcmfyCatn?.takeIf { it.isNotBlank() } ?: "없음"}"

                    // Animate expansion
                    detailsContainer.alpha = 0f
                    detailsContainer.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                }

                // Click listener for the whole item
                root.setOnClickListener { onClick() }
            }
        }

        private fun formatAmount(amount: String?): String {
            return amount?.let { amt ->
                if (amt.isNotEmpty()) {
                    amt.toIntOrNull()?.let { value ->
                        String.format("%,d원", value)
                    } ?: "금액 정보 없음"
                } else "금액 정보 없음"
            } ?: "금액 정보 없음"
        }

        private fun formatDate(dateStr: String?): String {
            return dateStr?.let { date ->
                try {
                    if (date.length >= 8) {
                        "${date.substring(0, 4)}.${date.substring(4, 6)}.${date.substring(6, 8)}"
                    } else date
                } catch (e: Exception) {
                    date
                }
            } ?: "날짜 정보 없음"
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<NonPaymentItem>() {
        override fun areItemsTheSame(oldItem: NonPaymentItem, newItem: NonPaymentItem): Boolean {
            return oldItem.itemCd == newItem.itemCd && oldItem.npayKorNm == newItem.npayKorNm
        }

        override fun areContentsTheSame(oldItem: NonPaymentItem, newItem: NonPaymentItem): Boolean {
            return oldItem == newItem
        }
    }
}