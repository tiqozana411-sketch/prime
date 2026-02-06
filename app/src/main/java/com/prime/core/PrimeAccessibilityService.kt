package com.prime.core

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.prime.speech.CallAudioInterceptor
import com.prime.speech.CallType
import com.prime.speech.WeChatCallMonitor
import com.prime.speech.WhisperSTT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * PRIME无障碍服务
 * 
 * 职责：
 * 1. 监听UI事件
 * 2. 获取UI树
 * 3. 执行UI操作（点击、输入、滑动）
 * 4. 监听微信/QQ通话，启动语音指令识别
 * 
 * Day 1: 基础框架
 * Day 2-3: 完整实现
 */
class PrimeAccessibilityService : AccessibilityService() {
    
    companion object {
        private var instance: PrimeAccessibilityService? = null
        
        fun getInstance(): PrimeAccessibilityService? = instance
        
        /**
         * 检查服务是否正在运行
         */
        fun isRunning(): Boolean = instance != null
        
        /**
         * 检查无障碍服务是否已启用（系统级检查）
         */
        fun isEnabled(context: android.content.Context): Boolean {
            return try {
                val enabledServices = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                val packageName = context.packageName
                enabledServices?.contains(packageName) == true
            } catch (e: Exception) {
                Timber.e(e, "❌ 检查无障碍服务状态失败")
                false
            }
        }
        
        /**
         * 安全获取实例（带状态校验）
         */
        fun getInstanceSafe(): PrimeAccessibilityService? {
            val inst = instance
            if (inst == null) {
                Timber.w("⚠️ 无障碍服务未运行")
                return null
            }
            return inst
        }
    }
    
    private var wechatCallMonitor: WeChatCallMonitor? = null
    private var callAudioInterceptor: CallAudioInterceptor? = null
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Timber.i("✅ 无障碍服务已连接")
        
        // 初始化微信/QQ通话监控
        initializeCallMonitor()
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // 运行时状态校验
        if (!isServiceHealthy()) {
            Timber.w("⚠️ 服务状态异常，跳过事件处理")
            return
        }
        
        // 传递给微信/QQ通话监控器
        wechatCallMonitor?.onAccessibilityEvent(event)
        
        // Day 2-3 实现其他事件处理
    }
    
    /**
     * 检查服务健康状态
     */
    private fun isServiceHealthy(): Boolean {
        // 检查服务是否仍然连接
        if (serviceInfo == null) {
            Timber.w("⚠️ ServiceInfo为空，服务可能已断开")
            return false
        }
        
        // 检查rootInActiveWindow是否可用
        if (rootInActiveWindow == null) {
            // 这是正常情况（某些界面没有窗口），不记录警告
            return false
        }
        
        return true
    }
    
    override fun onInterrupt() {
        Timber.w("无障碍服务被中断")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 停止通话监控
        callAudioInterceptor?.stopIntercepting()
        callAudioInterceptor = null
        wechatCallMonitor = null
        
        instance = null
        Timber.i("无障碍服务已销毁")
    }
    
    /**
     * 初始化通话监控
     */
    private fun initializeCallMonitor() {
        wechatCallMonitor = WeChatCallMonitor(
            context = applicationContext,
            onCallStateChanged = { callType, isActive ->
                if (isActive) {
                    startVoiceCommandRecognition(callType)
                } else {
                    stopVoiceCommandRecognition()
                }
            }
        )
        
        Timber.i("✅ 通话监控已初始化")
    }
    
    /**
     * 启动语音指令识别
     */
    private fun startVoiceCommandRecognition(callType: CallType) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 初始化STT引擎
                val sttEngine = WhisperSTT(applicationContext)
                if (!sttEngine.initialize()) {
                    Timber.w("⚠️ STT引擎初始化失败")
                    return@launch
                }
                
                // 创建音频拦截器
                callAudioInterceptor = CallAudioInterceptor(
                    context = applicationContext,
                    sttEngine = sttEngine,
                    onCommandReceived = { command ->
                        handleVoiceCommand(command)
                    }
                )
                
                // 开始拦截
                callAudioInterceptor?.startIntercepting(callType)
                
                Timber.i("✅ 语音指令识别已启动: $callType")
                
            } catch (e: Exception) {
                Timber.e(e, "❌ 启动语音识别失败")
            }
        }
    }
    
    /**
     * 停止语音指令识别
     */
    private fun stopVoiceCommandRecognition() {
        callAudioInterceptor?.stopIntercepting()
        callAudioInterceptor = null
        Timber.i("🛑 语音指令识别已停止")
    }
    
    /**
     * 处理语音指令
     */
    private fun handleVoiceCommand(command: com.prime.speech.VoiceCommand) {
        // 运行时状态校验
        if (!isServiceHealthy()) {
            Timber.w("⚠️ 服务状态异常，无法执行语音指令")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prime = PrimeController.getInstance(applicationContext)
                
                Timber.i("🎯 执行语音指令: $command")
                
                when (command) {
                    is com.prime.speech.VoiceCommand.OpenApp -> {
                        prime.executeTask("打开${command.appName}")
                    }
                    
                    is com.prime.speech.VoiceCommand.Screenshot -> {
                        prime.executeTask("截图")
                    }
                    
                    is com.prime.speech.VoiceCommand.SendMessage -> {
                        prime.executeTask("发送消息给${command.contact}: ${command.message}")
                    }
                    
                    is com.prime.speech.VoiceCommand.Search -> {
                        prime.executeTask("搜索${command.keyword}")
                    }
                    
                    com.prime.speech.VoiceCommand.PressBack -> {
                        prime.executeTask("返回")
                    }
                    
                    com.prime.speech.VoiceCommand.PressHome -> {
                        prime.executeTask("回到主页")
                    }
                    
                    else -> {
                        Timber.w("⚠️ 未处理的指令: $command")
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "❌ 执行语音指令失败")
            }
        }
    }
}