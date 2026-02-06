package com.prime.remote

import android.graphics.Bitmap
import com.prime.vision.ScreenCapture
import com.prime.tools.UIAutomation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * 远程控制服务器
 * 提供像素级远程控制能力
 */
object RemoteControlServer {
    
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val clients = mutableListOf<Socket>()
    
    /**
     * 启动服务器
     */
    suspend fun start(port: Int = 8888): Boolean = withContext(Dispatchers.IO) {
        if (isRunning) {
            Timber.w("服务器已在运行")
            return@withContext false
        }
        
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            
            Timber.i("✅ 远程控制服务器启动: 端口$port")
            
            // 监听客户端连接
            while (isRunning) {
                try {
                    val client = serverSocket?.accept()
                    if (client != null) {
                        clients.add(client)
                        Timber.i("客户端连接: ${client.inetAddress}")
                        handleClient(client)
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        Timber.e(e, "接受连接失败")
                    }
                }
            }
            
            true
        } catch (e: Exception) {
            Timber.e(e, "服务器启动失败")
            isRunning = false
            false
        }
    }
    
    /**
     * 停止服务器
     */
    fun stop() {
        isRunning = false
        clients.forEach { it.close() }
        clients.clear()
        serverSocket?.close()
        serverSocket = null
        Timber.i("远程控制服务器已停止")
    }
    
    /**
     * 处理客户端请求
     */
    private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
        try {
            val input = client.getInputStream()
            val output = client.getOutputStream()
            
            while (isRunning && !client.isClosed) {
                // 读取命令
                val command = input.read()
                
                when (command) {
                    CMD_SCREENSHOT -> {
                        // 发送截图
                        val screenshot = ScreenCapture.captureScreen()
                        if (screenshot != null) {
                            val bytes = bitmapToBytes(screenshot)
                            output.write(bytes.size)
                            output.write(bytes)
                            output.flush()
                            screenshot.recycle()
                        }
                    }
                    
                    CMD_CLICK -> {
                        // 执行点击
                        val x = input.read()
                        val y = input.read()
                        UIAutomation.click(x, y)
                    }
                    
                    CMD_SWIPE -> {
                        // 执行滑动
                        val x1 = input.read()
                        val y1 = input.read()
                        val x2 = input.read()
                        val y2 = input.read()
                        UIAutomation.swipe(x1, y1, x2, y2)
                    }
                    
                    CMD_INPUT -> {
                        // 输入文本
                        val length = input.read()
                        val textBytes = ByteArray(length)
                        input.read(textBytes)
                        val text = String(textBytes)
                        UIAutomation.inputText(text)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "处理客户端请求失败")
        } finally {
            client.close()
            clients.remove(client)
        }
    }
    
    private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return stream.toByteArray()
    }
    
    // 常量定义（object 内不能用 companion）
    private const val CMD_SCREENSHOT = 1
    private const val CMD_CLICK = 2
    private const val CMD_SWIPE = 3
    private const val CMD_INPUT = 4
}