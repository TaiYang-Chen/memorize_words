package com.chen.memorizewords.core.ui.fragment

import androidx.databinding.ViewDataBinding
import com.chen.memorizewords.core.ui.vm.BaseViewModel

abstract class BaseFragment<VM : BaseViewModel, DB : ViewDataBinding> : BaseVmDbFragment<VM, DB>() {

    override fun createObserver() {}

    override fun initData() {}
}