package com.prime.remote

import android.content.Context
import com.prime.vision.ScreenCapture
import com.prime.tools.UIAutomation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 远程控制管理器
 * 整合屏幕流传输和命令处理
 */
class RemoteControlManager(
    private val context: Context,
    private val screenCapture: ScreenCapture,
    private val uiAutomation: UIAutomation
) {
    
    private val webSocketServer = RemoteWebSocketServer(port = 8765)
    private val screenStream = RemoteScreenStream(screenCapture, webSocketServer)
    private val commandHandler = RemoteCommandHandler(uiAutomation)
    
    private var isRunning = false
    
    /**
     * 启动远程控制
     */
    fun start() {
        if (isRunning) {
            Timber.w("⚠️ 远程控制已在运行")
            return
        }
        
        // 启动WebSocket服务器
        webSocketServer.start { command ->
            handleRemoteCommand(command)
        }
        
        // 启动屏幕流传输
        screenStream.startStream(
            fps = 30,
            quality = 80,
            scale = 0.5f
        )
        
        isRunning = true
        Timber.i("✅ 远程控制已启动")
        Timber.i("📱 访问地址: http://<手机IP>:8765")
    }
    
    /**
     * 停止远程控制
     */
    fun stop() {
        screenStream.stopStream()
        webSocketServer.stop()
        isRunning = false
        Timber.i("❌ 远程控制已停止")
    }
    
    /**
     * 处理远程命令
     */
    private fun handleRemoteCommand(command: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = commandHandler.handleCommand(command)
            
            if (result.success) {
                Timber.i("✅ 命令执行成功: ${result.action}")
            } else {
                Timber.w("⚠️ 命令执行失败: ${result.error}")
            }
        }
    }
    
    /**
     * 更新流配置
     */
    fun updateStreamConfig(fps: Int? = null, quality: Int? = null, scale: Float? = null) {
        screenStream.updateConfig(fps, quality, scale)
    }
    
    /**
     * 获取状态
     */
    fun getStatus(): RemoteControlStatus {
        return RemoteControlStatus(
            isRunning = isRunning,
            isStreaming = screenStream.isStreaming(),
            clientCount = webSocketServer.getClientCount()
        )
    }
}

data class RemoteControlStatus(
    val isRunning: Boolean,
    val isStreaming: Boolean,
    val clientCount: Int
)