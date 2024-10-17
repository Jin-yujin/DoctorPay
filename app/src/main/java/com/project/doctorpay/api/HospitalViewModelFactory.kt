package com.project.doctorpay.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class HospitalViewModelFactory(private val api: HealthInsuranceApi) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HospitalViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HospitalViewModel(api) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}