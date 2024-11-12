package com.project.doctorpay

import android.app.Application
import com.kakao.sdk.common.KakaoSdk
import com.google.firebase.Firebase
import com.google.firebase.initialize

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Firebase.initialize(this)
        KakaoSdk.init(this, com.project.doctorpay.BuildConfig.KAKAO_NATIVE_APP_KEY)
    }
}