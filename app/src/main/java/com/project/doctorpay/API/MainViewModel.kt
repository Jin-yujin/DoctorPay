package com.project.doctorpay.api

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val _nonPaymentItems = MutableLiveData<List<Item>>()
    val nonPaymentItems: LiveData<List<Item>> = _nonPaymentItems

    fun fetchNonPaymentItems(hospitalName: String? = null) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.getNonPaymentItemHospList(
                    serviceKey = "0H0upZmR4M4DyfwLLid%2F7qyTNc%2BVxA0cg0mMk9zOU6V4zdapEmdXA10%2Fz69RvH4ey70OMYofiJ%2FEtqZlT3JC0w%3D%3D",
                    pageNo = 1,
                    numOfRows = 10,
                    hospitalName = hospitalName
                )
                _nonPaymentItems.value = response.body?.items ?: emptyList()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}