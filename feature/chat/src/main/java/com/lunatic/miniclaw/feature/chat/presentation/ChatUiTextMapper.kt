package com.lunatic.miniclaw.feature.chat.presentation

import com.lunatic.miniclaw.domain.chat.model.MessageStatus
import com.lunatic.miniclaw.domain.model.model.ModelAvailability
import com.lunatic.miniclaw.domain.model.model.ModelProviderId

internal fun MessageStatus.toStatusText(): String? {
    return when (this) {
        MessageStatus.SENDING -> "发送中"
        MessageStatus.SENT -> null
        MessageStatus.SEND_FAILED -> "发送失败"
        MessageStatus.THINKING -> "思考中"
        MessageStatus.STREAMING -> "回复中"
        MessageStatus.COMPLETED -> null
        MessageStatus.FAILED -> "回复失败"
        MessageStatus.STOPPED -> "已停止"
    }
}

internal fun ModelProviderId?.toUiLabel(): String {
    return when (this) {
        ModelProviderId.MINIMAX -> "MiniMax"
        null -> "模型"
    }
}

internal fun ModelAvailability.toUiText(): String {
    return when (this) {
        ModelAvailability.Available -> "已连接"
        ModelAvailability.NotConfigured -> "未配置"
        ModelAvailability.Validating -> "校验中"
        ModelAvailability.AuthFailed -> "鉴权失败"
        ModelAvailability.NetworkUnavailable -> "网络异常"
        ModelAvailability.ServiceUnavailable -> "服务异常"
        ModelAvailability.Unknown -> "未知"
    }
}
