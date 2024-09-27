package com.project.doctorpay

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.doctorpay.NonPaymentAdapter
import com.project.doctorpay.api.MainViewModel
import com.project.doctorpay.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private val adapter = NonPaymentAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if user is logged in
        // 임시 로그인 기능 없앨 때 주석 풀기
//        if (!isLoggedIn()) {
//            // If not logged in, redirect to LoginActivity
//            val intent = Intent(this, LoginActivity::class.java)
//            startActivity(intent)
//            finish()
//            return
//        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupRecyclerView()
        setupSearchButton()
        observeViewModel()

        viewModel.fetchNonPaymentItems()
    }

    private fun isLoggedIn(): Boolean {
        // TODO: Implement actual login check
        // For now, always return false to show login screen
        return false
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupSearchButton() {
        binding.searchButton.setOnClickListener {
            val searchQuery = binding.searchEditText.text.toString()
            viewModel.fetchNonPaymentItems(searchQuery)
        }
    }

    private fun observeViewModel() {
        viewModel.nonPaymentItems.observe(this) { items ->
            adapter.setItems(items)
        }
    }
}