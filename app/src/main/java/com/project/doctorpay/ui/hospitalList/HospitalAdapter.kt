package com.project.doctorpay.ui.hospitalList

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.project.doctorpay.DB.HospitalInfo
import com.project.doctorpay.R

class HospitalAdapter(
    private val hospitals: List<HospitalInfo>,
    private val onItemClick: (HospitalInfo) -> Unit
) : RecyclerView.Adapter<HospitalAdapter.HospitalViewHolder>() {

    class HospitalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.item_hospitalName)
        val addressTextView: TextView = view.findViewById(R.id.item_hospitalAddress)
        val departmentTextView: TextView = view.findViewById(R.id.item_department)
        val timeTextView: TextView = view.findViewById(R.id.item_hospitalTime)
        val phoneNumberTextView: TextView = view.findViewById(R.id.item_hospitalNum)
        val stateTextView: TextView = view.findViewById(R.id.tvState)
        val distanceTextView: TextView = view.findViewById(R.id.item_hospitalDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HospitalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.comp_list_item, parent, false)
        return HospitalViewHolder(view)
    }

    override fun onBindViewHolder(holder: HospitalViewHolder, position: Int) {
        val hospital = hospitals[position]
        holder.nameTextView.text = hospital.name
        holder.addressTextView.text = hospital.address
        holder.departmentTextView.text = hospital.department
        holder.timeTextView.text = hospital.time
        holder.phoneNumberTextView.text = hospital.phoneNumber
        holder.stateTextView.text = hospital.state

        // Distance calculation should be done separately and stored in HospitalInfo
        // For now, we'll use a placeholder
        holder.distanceTextView.text = "000m"

        holder.itemView.setOnClickListener {
            onItemClick(hospital)
        }
    }

    override fun getItemCount() = hospitals.size
}