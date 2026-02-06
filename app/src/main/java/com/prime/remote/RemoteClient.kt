package com.prime.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.OutputStream
import java.net.Socket

/**
 * 远程控制客户端
 * 用于从电脑连接到手机
 */
class RemoteClient(private val host: String, private val port: Int = 8888) {
    
    private var socket: Socket? = null
    private var output: OutputStream? = null
    private var isConnected = false
    
    /**
     * 连接到服务器
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = Socket(host, port)
            output = socket?.getOutputStream()
            isConnected = true
            
            Timber.i("✅ 已连接到远程服务器: $host:$port")
            true
        } catch (e: Exception) {
            Timber.e(e, "❌ 连接失败")
            false
        }
    }
    
    /**
     * 断开连接
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        isConnected = false
        socket?.close()
        Timber.i("🛑 已断开连接")
    }
    
    /**
     * 请求屏幕截图
     */
    suspend fun requestScreen() {
        sendMessage(RemoteMessage(MessageType.REQUEST_SCREEN))
    }
    
    /**
     * 发送点击指令
     */
    suspend fun sendClick(x: Int, y: Int) {
        sendMessage(RemoteMessage(
            type = MessageType.CLICK,
            data = mapOf("x" to x.toString(), "y" to y.toString())
        ))
    }
    
    /**
     * 发送滑动指令
     */
    suspend fun sendSwipe(direction: String) {
        sendMessage(RemoteMessage(
            type = MessageType.SWIPE,
            data = mapOf("direction" to direction)
        ))
    }
    
    /**
     * 发送输入指令
     */
    suspend fun sendInput(text: String) {
        sendMessage(RemoteMessage(
            type = MessageType.INPUT,
            data = mapOf("text" to text)
        ))
    }
    
    /**
     * 发送返回指令
     */
    suspend fun sendBack() {
        sendMessage(RemoteMessage(MessageType.BACK))
    }
    
    /**
     * 发送主页指令
     */
    suspend fun sendHome() {
        sendMessage(RemoteMessage(MessageType.HOME))
    }
    
    /**
     * 发送消息
     */
    private suspend fun sendMessage(message: RemoteMessage) = withContext(Dispatchers.IO) {
        if (!isConnected) {
            Timber.w("⚠️ 未连接到服务器")
            return@withContext
        }
        
        try {
            val json = message.toJson()
            output?.write(json.toByteArray())
            output?.flush()
        } catch (e: Exception) {
            Timber.e(e, "❌ 发送消息失败")
        }
    }
}