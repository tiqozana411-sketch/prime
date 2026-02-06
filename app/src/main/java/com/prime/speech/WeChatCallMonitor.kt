package com.prime.speech

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 微信/QQ通话监控器
 * 通过无障碍服务监听微信/QQ通话状态
 */
class WeChatCallMonitor(
    private val context: Context,
    private val onCallStateChanged: (CallType, Boolean) -> Unit
) {
    
    private val wechatPackage = "com.tencent.mm"
    private val qqPackage = "com.tencent.mobileqq"
    
    private var isWeChatCalling = false
    private var isQQCalling = false
    
    /**
     * 处理无障碍事件
     */
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.packageName?.toString()) {
            wechatPackage -> handleWeChatEvent(event)
            qqPackage -> handleQQEvent(event)
        }
    }
    
    /**
     * 处理微信事件
     */
    private fun handleWeChatEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className?.toString() ?: return
                
                // 检测微信语音/视频通话界面
                when {
                    className.contains("VoipActivity") || 
                    className.contains("VideoActivity") -> {
                        if (!isWeChatCalling) {
                            isWeChatCalling = true
                            Timber.i("📞 检测到微信通话")
                            onCallStateChanged(CallType.WECHAT, true)
                        }
                    }
                    
                    else -> {
                        if (isWeChatCalling) {
                            isWeChatCalling = false
                            Timber.i("📞 微信通话结束")
                            onCallStateChanged(CallType.WECHAT, false)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 处理QQ事件
     */
    private fun handleQQEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className?.toString() ?: return
                
                // 检测QQ语音/视频通话界面
                when {
                    className.contains("VoiceCallActivity") || 
                    className.contains("VideoCallActivity") -> {
                        if (!isQQCalling) {
                            isQQCalling = true
                            Timber.i("📞 检测到QQ通话")
                            onCallStateChanged(CallType.QQ, true)
                        }
                    }
                    
                    else -> {
                        if (isQQCalling) {
                            isQQCalling = false
                            Timber.i("📞 QQ通话结束")
                            onCallStateChanged(CallType.QQ, false)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 重置状态
     */
    fun reset() {
        isWeChatCalling = false
        isQQCalling = false
    }
}