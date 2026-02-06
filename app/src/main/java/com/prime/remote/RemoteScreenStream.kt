package com.prime.remote

import android.graphics.Bitmap
import com.prime.vision.ScreenCapture
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 远程屏幕流传输
 * 实时传输屏幕画面
 */
class RemoteScreenStream(
    private val screenCapture: ScreenCapture,
    private val webSocketServer: RemoteWebSocketServer
) {
    
    private var streamJob: Job? = null
    private val isStreaming = AtomicBoolean(false)
    
    private var fps = 30 // 帧率
    private var quality = 80 // JPEG质量（1-100）
    private var scale = 0.5f // 缩放比例（减少带宽）
    
    /**
     * 开始流传输
     */
    fun startStream(fps: Int = 30, quality: Int = 80, scale: Float = 0.5f) {
        if (isStreaming.get()) {
            Timber.w("⚠️ 流传输已在运行")
            return
        }
        
        this.fps = fps
        this.quality = quality
        this.scale = scale
        
        isStreaming.set(true)
        
        streamJob = CoroutineScope(Dispatchers.IO).launch {
            val frameInterval = 1000L / fps
            
            Timber.i("📡 开始流传输: ${fps}fps, 质量${quality}, 缩放${scale}")
            
            while (isStreaming.get()) {
                try {
                    val startTime = System.currentTimeMillis()
                    
                    // 截图
                    val bitmap = screenCapture.captureScreen()
                    if (bitmap == null) {
                        delay(frameInterval)
                        continue
                    }
                    
                    // 缩放
                    val scaledBitmap = if (scale < 1.0f) {
                        Bitmap.createScaledBitmap(
                            bitmap,
                            (bitmap.width * scale).toInt(),
                            (bitmap.height * scale).toInt(),
                            true
                        )
                    } else {
                        bitmap
                    }
                    
                    // 压缩为JPEG
                    val outputStream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                    val imageData = outputStream.toByteArray()
                    
                    // 发送到客户端
                    webSocketServer.broadcastFrame(imageData)
                    
                    // 清理
                    bitmap.recycle()
                    if (scaledBitmap != bitmap) {
                        scaledBitmap.recycle()
                    }
                    
                    // 控制帧率
                    val elapsed = System.currentTimeMillis() - startTime
                    val sleepTime = frameInterval - elapsed
                    if (sleepTime > 0) {
                        delay(sleepTime)
                    }
                    
                } catch (e: Exception) {
                    Timber.e(e, "❌ 流传输错误")
                    delay(frameInterval)
                }
            }
            
            Timber.i("📡 流传输已停止")
        }
    }
    
    /**
     * 停止流传输
     */
    fun stopStream() {
        isStreaming.set(false)
        streamJob?.cancel()
        streamJob = null
    }
    
    /**
     * 是否正在流传输
     */
    fun isStreaming() = isStreaming.get()
    
    /**
     * 更新配置
     */
    fun updateConfig(fps: Int? = null, quality: Int? = null, scale: Float? = null) {
        fps?.let { this.fps = it }
        quality?.let { this.quality = it }
        scale?.let { this.scale = it }
        
        Timber.i("⚙️ 流配置更新: ${this.fps}fps, 质量${this.quality}, 缩放${this.scale}")
    }
}