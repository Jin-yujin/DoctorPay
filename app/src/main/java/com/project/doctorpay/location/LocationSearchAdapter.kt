package com.project.doctorpay.location

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.project.doctorpay.databinding.ItemLocationSearchBinding


class LocationSearchAdapter(
    private val onItemClick: (LocationSearchItem) -> Unit
) : RecyclerView.Adapter<LocationSearchAdapter.ViewHolder>() {

    private var items = listOf<LocationSearchItem>()

    fun submitList(newItems: List<LocationSearchItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLocationSearchBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(
        private val binding: ItemLocationSearchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(items[position])
                }
            }
        }

        fun bind(item: LocationSearchItem) {
            binding.apply {
                tvTitle.text = item.title
                tvAddress.text = item.address
                tvRoadAddress.text = item.roadAddress
                tvRoadAddress.isVisible = !item.roadAddress.isNullOrEmpty()
            }
        }
    }
}


class LocationPreference(context: Context) {
    private val prefs = context.getSharedPreferences("location_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_CURRENT_LOCATION_SET = "current_location_set"
    }

    fun saveLocation(latitude: Double, longitude: Double, address: String) {
        prefs.edit().apply {
            putFloat("latitude", latitude.toFloat())
            putFloat("longitude", longitude.toFloat())
            putString("address", address)
            putBoolean(KEY_FIRST_LAUNCH, false)  // 위치 저장 시 최초 실행 아님으로 설정
            apply()
        }
    }

    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun getAddress(): String {
        return prefs.getString("address", "위치 설정") ?: "위치 설정"
    }

    fun getLocation(): Pair<Double, Double>? {
        val lat = prefs.getFloat("latitude", 0f)
        val lng = prefs.getFloat("longitude", 0f)
        return if (lat != 0f && lng != 0f) {
            Pair(lat.toDouble(), lng.toDouble())
        } else {
            null
        }
    }


    fun isCurrentLocationSet(): Boolean {
        return prefs.getBoolean(KEY_CURRENT_LOCATION_SET, false)
    }

    fun saveCurrentLocationState() {
        prefs.edit().putBoolean(KEY_CURRENT_LOCATION_SET, true).apply()
    }
}