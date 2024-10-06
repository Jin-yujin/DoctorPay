package com.project.doctorpay.ui.hospitalList

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.doctorpay.DB.HospitalInfo
import com.project.doctorpay.R

class HospitalAdapter(
    private val onItemClick: (HospitalInfo) -> Unit
) : ListAdapter<HospitalInfo, HospitalAdapter.HospitalViewHolder>(HospitalDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HospitalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.comp_list_item, parent, false)
        return HospitalViewHolder(view)
    }

    override fun onBindViewHolder(holder: HospitalViewHolder, position: Int) {
        val hospital = getItem(position)
        holder.bind(hospital)
        holder.itemView.setOnClickListener { onItemClick(hospital) }
    }

    class HospitalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameTextView: TextView = view.findViewById(R.id.item_hospitalName)
        private val addressTextView: TextView = view.findViewById(R.id.item_hospitalAddress)
        private val departmentTextView: TextView = view.findViewById(R.id.item_department)
        private val timeTextView: TextView = view.findViewById(R.id.item_hospitalTime)
        private val phoneNumberTextView: TextView = view.findViewById(R.id.item_hospitalNum)
        private val stateTextView: TextView = view.findViewById(R.id.tvState)
        private val distanceTextView: TextView = view.findViewById(R.id.item_hospitalDistance)

        fun bind(hospital: HospitalInfo) {
            nameTextView.text = hospital.name
            addressTextView.text = hospital.address
            departmentTextView.text = hospital.department
            timeTextView.text = hospital.time
            phoneNumberTextView.text = hospital.phoneNumber
            stateTextView.text = hospital.state
            distanceTextView.text = "거리 정보 준비 중"  // 향후 실제 거리로 대체될 예정
        }
    }

    private class HospitalDiffCallback : DiffUtil.ItemCallback<HospitalInfo>() {
        override fun areItemsTheSame(oldItem: HospitalInfo, newItem: HospitalInfo): Boolean {
            return oldItem.name == newItem.name && oldItem.address == newItem.address
        }

        override fun areContentsTheSame(oldItem: HospitalInfo, newItem: HospitalInfo): Boolean {
            return oldItem == newItem
        }
    }
}