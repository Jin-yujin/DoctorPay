package com.project.doctorpay.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.doctorpay.NonPaymentAdapter
import com.project.doctorpay.R
import com.project.doctorpay.api.MainViewModel
import com.project.doctorpay.databinding.FragmentHomeBinding
import com.project.doctorpay.ui.hospitalList.HospitalListFragment

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private val adapter = NonPaymentAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupRecyclerView()
        setupSearchButton()
        setupCategoryButtons()
        observeViewModel()

        viewModel.fetchNonPaymentItems()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupSearchButton() {
        binding.searchButton.setOnClickListener {
            val searchQuery = binding.searchEditText.text.toString()
            viewModel.fetchNonPaymentItems(searchQuery)
        }
    }

    private fun setupCategoryButtons() {
        val buttons = listOf(
            binding.btnInternalMedicine,
            binding.btnGeneralSurgery,
            binding.btnDentistry,
            binding.btnENT,
            binding.btnOrthopedics,
            binding.btnObGyn,
            binding.btnUrology,
            binding.btnProctology,
            binding.btnPlasticSurgery
        )

        buttons.forEach { button ->
            button.setOnClickListener {
                navigateToHospitalList(button.id)
            }
        }
    }

    private fun navigateToHospitalList(categoryId: Int) {
        val hospitalListFragment = HospitalListFragment.newInstance(categoryId)
        parentFragmentManager.beginTransaction()
            .replace(R.id.lyFrameLayout_home, hospitalListFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun observeViewModel() {
        viewModel.nonPaymentItems.observe(viewLifecycleOwner) { items ->
            adapter.setItems(items)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}