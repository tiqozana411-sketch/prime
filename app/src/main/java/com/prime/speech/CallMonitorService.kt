package com.prime.speech

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telecom.Call
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import com.prime.core.PrimeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 通话监控服务
 * 监听系统电话、微信、QQ通话，自动启动语音指令识别
 */
class CallMonitorService : InCallService() {
    
    private var callAudioInterceptor: CallAudioInterceptor? = null
    private var currentCall: Call? = null
    
    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            when (state) {
                Call.STATE_ACTIVE -> {
                    Timber.i("📞 通话已接通")
                    startVoiceCommandRecognition(CallType.PHONE)
                }
                
                Call.STATE_DISCONNECTED -> {
                    Timber.i("📞 通话已结束")
                    stopVoiceCommandRecognition()
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Timber.i("🎧 通话监控服务启动")
        startForeground()
    }
    
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        call.registerCallback(callCallback)
        Timber.i("📞 检测到新通话")
    }
    
    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        currentCall = null
        stopVoiceCommandRecognition()
        Timber.i("📞 通话已移除")
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }
    
    /**
     * 启动语音指令识别
     */
    private fun startVoiceCommandRecognition(callType: CallType) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prime = PrimeController.getInstance(applicationContext)
                
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
    }
    
    /**
     * 处理语音指令
     */
    private fun handleVoiceCommand(command: VoiceCommand) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prime = PrimeController.getInstance(applicationContext)
                
                Timber.i("🎯 执行语音指令: $command")
                
                when (command) {
                    is VoiceCommand.OpenApp -> {
                        prime.executeTask("打开${command.appName}")
                    }
                    
                    is VoiceCommand.Screenshot -> {
                        prime.executeTask("截图")
                    }
                    
                    is VoiceCommand.SendMessage -> {
                        prime.executeTask("发送消息给${command.contact}: ${command.message}")
                    }
                    
                    is VoiceCommand.Search -> {
                        prime.executeTask("搜索${command.keyword}")
                    }
                    
                    VoiceCommand.PressBack -> {
                        prime.executeTask("返回")
                    }
                    
                    VoiceCommand.PressHome -> {
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
    
    /**
     * 启动前台服务
     */
    private fun startForeground() {
        val channelId = "call_monitor_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "通话监控",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("PRIME通话监控")
            .setContentText("正在监听通话语音指令")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        
        startForeground(1001, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopVoiceCommandRecognition()
        Timber.i("🎧 通话监控服务停止")
    }
}