package com.project.doctorpay.ui.hospitalList

import android.location.Location
import android.util.Log
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
        Log.d("HospitalAdapter", "Updating user location to: ${location.latitude}, ${location.longitude}")
        userLocation = location
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: HospitalViewHolder, position: Int) {
        val hospital = getItem(position)
        Log.d("HospitalAdapter", "Binding hospital: ${hospital.name}")
        Log.d("HospitalAdapter", "User location: $userLocation")
        Log.d("HospitalAdapter", "Hospital location: (${hospital.latitude}, ${hospital.longitude})")

        val distance = calculateDistance(hospital)
        Log.d("HospitalAdapter", "Calculated distance: $distance")

        holder.bind(hospital, distance)
        holder.itemView.setOnClickListener { onItemClick(hospital) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HospitalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.comp_list_item, parent, false)
        return HospitalViewHolder(view)
    }

    private fun calculateDistance(hospital: HospitalInfo): String {
        return userLocation?.let { currentLocation ->
            val results = FloatArray(1)
            try {
                Location.distanceBetween(
                    currentLocation.latitude,
                    currentLocation.longitude,
                    hospital.latitude,
                    hospital.longitude,
                    results
                )

                when {
                    results[0] < 1000 -> "${results[0].roundToInt()}m"
                    else -> String.format("%.1fkm", results[0] / 1000)
                }
            } catch (e: Exception) {
                Log.e("HospitalAdapter", "Error calculating distance for ${hospital.name}", e)
                "거리 계산 오류"
            }
        } ?: run {
            Log.d("HospitalAdapter", "User location is null")
            "거리 정보 없음"
        }
    }

    class HospitalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameTextView: TextView = view.findViewById(R.id.item_hospitalName)
        private val addressTextView: TextView = view.findViewById(R.id.item_hospitalAddress)
        private val departmentTextView: TextView = view.findViewById(R.id.item_department)
        private val phoneNumberTextView: TextView = view.findViewById(R.id.item_hospitalNum)
        private val stateTextView: TextView = view.findViewById(R.id.tvState)
        private val distanceTextView: TextView = view.findViewById(R.id.item_hospitalDistance)

        fun bind(hospital: HospitalInfo, distance: String) {
            nameTextView.text = hospital.name
            addressTextView.text = hospital.address
            departmentTextView.text = hospital.departments.joinToString(", ")
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