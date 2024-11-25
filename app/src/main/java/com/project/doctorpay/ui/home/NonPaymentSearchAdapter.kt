package com.project.doctorpay.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.doctorpay.R
import NonPaymentItem
import java.text.NumberFormat
import java.util.Locale

class NonPaymentSearchAdapter(
    private val onItemClick: (NonPaymentItem) -> Unit
) : ListAdapter<NonPaymentItem, NonPaymentSearchAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_non_payment_search, parent, false)
        return ViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        view: View,
        private val onItemClick: (NonPaymentItem) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val tvItemName: TextView = view.findViewById(R.id.tvItemName)
        private val tvHospitalName: TextView = view.findViewById(R.id.tvHospitalName)
        private val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        private val tvDate: TextView = view.findViewById(R.id.tvDate)

        fun bind(item: NonPaymentItem) {
            tvItemName.text = item.npayKorNm ?: item.itemNm
            tvHospitalName.text = item.yadmNm

            // 가격 포맷팅
            tvPrice.text = item.curAmt?.let { amt ->
                try {
                    NumberFormat.getNumberInstance(Locale.KOREA)
                        .format(amt.toLong()) + "원"
                } catch (e: NumberFormatException) {
                    amt + "원"
                }
            } ?: "가격 정보 없음"

            // 날짜 포맷팅
            tvDate.text = item.adtFrDd?.let { date ->
                try {
                    "기준일자: ${date.substring(0,4)}.${date.substring(4,6)}.${date.substring(6,8)}"
                } catch (e: Exception) {
                    "기준일자: $date"
                }
            } ?: "날짜 정보 없음"

            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<NonPaymentItem>() {
        override fun areItemsTheSame(oldItem: NonPaymentItem, newItem: NonPaymentItem): Boolean {
            return oldItem.itemCd == newItem.itemCd && oldItem.yadmNm == newItem.yadmNm
        }

        override fun areContentsTheSame(oldItem: NonPaymentItem, newItem: NonPaymentItem): Boolean {
            return oldItem == newItem
        }
    }
}