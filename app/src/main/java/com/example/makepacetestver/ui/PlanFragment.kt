package com.example.makepacetestver.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.makepacetestver.R
import com.example.makepacetestver.data.StrategyProvider
import com.example.makepacetestver.data.db.AppDatabase
import com.example.makepacetestver.databinding.FragmentPlanBinding

class PlanFragment : Fragment() {

    private var _binding: FragmentPlanBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: RunningViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val runDao = AppDatabase.getDatabase(requireContext()).getRunDao()
        val factory = RunningViewModelFactory(runDao)
        viewModel = ViewModelProvider(requireActivity(), factory)[RunningViewModel::class.java]
        
        // 보이스 매니저 초기화
        viewModel.initVoiceManager(requireContext())

        val adapter = PaceStrategyAdapter(StrategyProvider.strategies) { strategy ->
            viewModel.setStrategy(strategy)
            // 러닝 화면으로 이동
            findNavController().navigate(R.id.nav_run)
        }

        binding.rvStrategies.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStrategies.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
