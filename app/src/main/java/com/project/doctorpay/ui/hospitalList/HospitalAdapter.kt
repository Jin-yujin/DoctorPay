package com.project.doctorpay.ui.hospitalList

import android.location.Location
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.R
import kotlin.math.roundToInt

class HospitalAdapter(
    private val onItemClick: (HospitalInfo) -> Unit
) : ListAdapter<HospitalInfo, HospitalAdapter.HospitalViewHolder>(HospitalDiffCallback()) {

    private var userLocation: LatLng? = null

    fun updateUserLocation(location: LatLng) {
        userLocation = location
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HospitalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.comp_list_item, parent, false)
        return HospitalViewHolder(view)
    }

    override fun onBindViewHolder(holder: HospitalViewHolder, position: Int) {
        val hospital = getItem(position)
        val distance = calculateDistance(hospital)
        holder.bind(hospital, distance)
        holder.itemView.setOnClickListener { onItemClick(hospital) }
    }

    private fun calculateDistance(hospital: HospitalInfo): String {
        return userLocation?.let { currentLocation ->
            val results = FloatArray(1)
            Location.distanceBetween(
                currentLocation.latitude,
                currentLocation.longitude,
                hospital.latitude,
                hospital.longitude,
                results
            )

            // Convert distance to appropriate unit (m or km)
            when {
                results[0] < 1000 -> "${results[0].roundToInt()}m"
                else -> String.format("%.1fkm", results[0] / 1000)
            }
        } ?: "거리 정보 없음"
    }

    class HospitalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameTextView: TextView = view.findViewById(R.id.item_hospitalName)
        private val addressTextView: TextView = view.findViewById(R.id.item_hospitalAddress)
        private val departmentTextView: TextView = view.findViewById(R.id.item_department)
        private val timeTextView: TextView = view.findViewById(R.id.item_hospitalTime)
        private val phoneNumberTextView: TextView = view.findViewById(R.id.item_hospitalNum)
        private val stateTextView: TextView = view.findViewById(R.id.tvState)
        private val distanceTextView: TextView = view.findViewById(R.id.item_hospitalDistance)

        fun bind(hospital: HospitalInfo, distance: String) {
            nameTextView.text = hospital.name
            addressTextView.text = hospital.address
            departmentTextView.text = hospital.departments.joinToString(", ")
            timeTextView.text = hospital.time
            phoneNumberTextView.text = hospital.phoneNumber
            stateTextView.text = hospital.state
            distanceTextView.text = distance
        }
    }

    private class HospitalDiffCallback : DiffUtil.ItemCallback<HospitalInfo>() {
        override fun areItemsTheSame(oldItem: HospitalInfo, newItem: HospitalInfo): Boolean {
            return oldItem.ykiho == newItem.ykiho
        }

        override fun areContentsTheSame(oldItem: HospitalInfo, newItem: HospitalInfo): Boolean {
            return oldItem == newItem
        }
    }
}