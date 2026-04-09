package com.chen.memorizewords.domain.repository.user

class LogoutDataLossRiskException(
    message: String = "未同步的本地数据可能丢失，请先同步或确认强制退出后再继续。"
) : Exception(message)
