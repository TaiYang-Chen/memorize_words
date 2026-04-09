package com.chen.memorizewords.core.ui.activity

import android.view.View
import androidx.viewbinding.ViewBinding
import com.chen.memorizewords.core.ui.ext.inflateBindingWithGeneric
import com.chen.memorizewords.core.ui.vm.BaseViewModel

abstract class BaseVmDbActivity<VM : BaseViewModel, DB : ViewBinding> : BaseVmActivity<VM>() {

    final override fun layoutId(): Int {
        error("BaseVmDbActivity must use ViewBinding")
    }

    lateinit var databind: DB

    override fun initDataBind(): View? {
        databind = inflateBindingWithGeneric(layoutInflater)
        return databind.root
    }

    open fun navControllerId() = 0
}
