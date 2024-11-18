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
class NonCoveredItemsAdapter : ListAdapter<NonPaymentItem, NonCoveredItemsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_non_covered_full, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvItemName: TextView = view.findViewById(R.id.tvItemName)
        private val tvItemPrice: TextView = view.findViewById(R.id.tvItemPrice)
        private val tvItemCode: TextView = view.findViewById(R.id.tvItemCode)
        private val tvSpecialNote: TextView = view.findViewById(R.id.tvSpecialNote)

        fun bind(item: NonPaymentItem) {
            tvItemName.text = item.npayKorNm ?: "항목명 없음"
            tvItemPrice.text = if (item.curAmt.isNullOrEmpty()) {
                "가격 정보 없음"
            } else {
                "${item.curAmt}원"
            }

            // 항목 코드 표시
            tvItemCode.apply {
                text = "코드: ${item.itemCd ?: "없음"}"
                isVisible = !item.itemCd.isNullOrEmpty()
            }

            // 특이사항 표시
            tvSpecialNote.apply {
                text = item.spcmfyCatn
                isVisible = !item.spcmfyCatn.isNullOrEmpty()
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<NonPaymentItem>() {
        override fun areItemsTheSame(oldItem: NonPaymentItem, newItem: NonPaymentItem): Boolean {
            return oldItem.itemCd == newItem.itemCd
        }

        override fun areContentsTheSame(oldItem: NonPaymentItem, newItem: NonPaymentItem): Boolean {
            return oldItem == newItem
        }
    }
}