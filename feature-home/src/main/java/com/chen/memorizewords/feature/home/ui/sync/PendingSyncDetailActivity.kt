package com.chen.memorizewords.feature.home.ui.sync

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.core.ui.activity.BaseVmDbActivity
import com.chen.memorizewords.feature.home.databinding.FeatureHomeActivityPendingSyncDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PendingSyncDetailActivity :
    BaseVmDbActivity<PendingSyncDetailViewModel, FeatureHomeActivityPendingSyncDetailBinding>() {

    override val viewModel: PendingSyncDetailViewModel by lazy {
        ViewModelProvider(this)[PendingSyncDetailViewModel::class.java]
    }

    private val adapter = PendingSyncAdapter { id ->
        viewModel.onItemClicked(id)
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.rvPendingSyncRecords.layoutManager = LinearLayoutManager(this)
        databind.rvPendingSyncRecords.adapter = adapter
        databind.rvPendingSyncRecords.itemAnimator = null
        databind.btnPendingSyncBack.setOnClickListener {
            viewModel.onBackClicked()
        }
    }

    override fun createObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        databind.tvPendingSyncCount.text = state.titleText
                        databind.tvPendingSyncEmpty.isVisible = state.isEmpty
                        databind.rvPendingSyncRecords.isVisible = !state.isEmpty
                        adapter.submitList(state.items)
                    }
                }
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, PendingSyncDetailActivity::class.java)
        }
    }
}
