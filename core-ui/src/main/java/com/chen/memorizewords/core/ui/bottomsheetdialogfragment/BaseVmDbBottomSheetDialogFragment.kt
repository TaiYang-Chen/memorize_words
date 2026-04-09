package com.chen.memorizewords.core.ui.bottomsheetdialogfragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import com.chen.memorizewords.core.ui.ext.inflateBindingWithGeneric
import com.chen.memorizewords.core.ui.vm.BaseViewModel


/**
 * 作者　: hegaojian
 * 时间　: 2019/12/12
 * 描述　: ViewModelFragment基类，自动把ViewModel注入Fragment和Databind注入进来了
 * 需要使用Databind的清继承它
 */
abstract class BaseVmDbBottomSheetDialogFragment<VM : BaseViewModel, DB : ViewDataBinding> : BaseVmBottomSheetDialogFragment<VM>() {

    override fun setLayout() = 0

    //该类绑定的ViewDataBinding
    private var _binding: DB? = null
    val databind: DB get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = inflateBindingWithGeneric(inflater, container, false)
        return  _binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}