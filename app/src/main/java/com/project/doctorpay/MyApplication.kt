package com.project.doctorpay

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.kakao.sdk.common.KakaoSdk

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Firebase.initialize(this)
        KakaoSdk.init(this, "56d42cbadaff4473519f8adf8e6317e8")
    }
}