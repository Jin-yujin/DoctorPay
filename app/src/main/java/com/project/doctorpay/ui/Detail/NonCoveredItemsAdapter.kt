package com.project.doctorpay.ui.Detail

import NonPaymentItem
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.doctorpay.databinding.ItemNonCoveredFullBinding
import java.text.NumberFormat
import java.util.Locale

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
                // 기본 정보
                categoryTitleTextView.text = item.npayKorNm ?: "항목명 없음"
                itemNameTextView.text = item.itemNm?.takeIf { it != item.npayKorNm } ?: ""
                amountTextView.text = formatAmount(item.curAmt)
                dateTextView.text = formatDate(item.adtFrDd)

                // 상세 정보 컨테이너
                detailsContainer.isVisible = isExpanded

                if (isExpanded) {
                    // 1. 기본 분류 정보
                    categoryCodeTextView.text = buildString {
                        append("분류코드: ${item.clCd ?: "없음"}")
                        if (item.clCdNm != null) {
                            append(" (${item.clCdNm})")
                        }
                    }

                    // 2. 상세 코드 정보
                    itemCodeTextView.text = buildString {
                        append("항목코드: ${item.itemCd ?: "없음"}")
                        if (item.itemNm != null && item.itemNm != item.npayKorNm) {
                            append(" (${item.itemNm})")
                        }
                    }

                    // 3. 병원 정보
                    hospitalInfoTextView.text = buildString {
                        append("의료기관: ${item.yadmNm ?: "정보없음"}")
                        append("\n식별코드: ${item.ykiho ?: "정보없음"}")
                    }

                    // 4. 금액 상세 정보
                    priceDetailsTextView.text = buildString {
                        val amount = item.curAmt?.toIntOrNull()
                        if (amount != null) {
                            append("기준금액: ${formatAmount(item.curAmt)}")
                            // 참고용 일일 금액 계산
                            append("\n일일 기준: ${formatAmount((amount / 30.0).toInt().toString())}")
                        }
                    }

                    // 5. 유효기간 정보
                    validPeriodTextView.text = buildString {
                        append("적용시작일: ${formatDate(item.adtFrDd)}")
                        append("\n적용종료일: ${formatDate(item.adtEndDd)}")
                    }

                    // 6. 특이사항 및 참고사항
                    notesTextView.text = buildString {
                        append("특이사항: ${item.spcmfyCatn?.takeIf { it.isNotBlank() } ?: "없음"}")
                        // 위치 정보가 있는 경우 표시
                        if (item.latitude != null && item.longitude != null) {
                            append("\n위치: ${item.latitude}, ${item.longitude}")
                        }
                    }

                    // 애니메이션 효과
                    detailsContainer.alpha = 0f
                    detailsContainer.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                }

                root.setOnClickListener { onClick() }
            }
        }

        private fun formatAmount(amount: String?): String {
            return amount?.let { amt ->
                if (amt.isNotEmpty()) {
                    amt.toDoubleOrNull()?.let { value ->
                        NumberFormat.getCurrencyInstance(Locale.KOREA).format(value)
                    } ?: "금액 정보 없음"
                } else "금액 정보 없음"
            } ?: "금액 정보 없음"
        }

        private fun formatDate(dateStr: String?): String {
            return dateStr?.let { date ->
                try {
                    if (date.length >= 8) {
                        "${date.substring(0, 4)}년 ${date.substring(4, 6)}월 ${date.substring(6, 8)}일"
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