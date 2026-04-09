package com.chen.memorizewords.core.common.resource

interface ResourceProvider {
    fun getString(resId: Int, vararg formatArgs: Any): String
}
