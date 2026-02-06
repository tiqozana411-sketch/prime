package com.prime.vision

import android.graphics.Bitmap
import com.prime.core.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * ROOT截图引擎
 * 使用screencap命令实现100ms快速截图
 */
object ScreenCapture {
    
    private val tempFile = File("/sdcard/PRIME/temp_screenshot.png")
    
    init {
        tempFile.parentFile?.mkdirs()
    }
    
    /**
     * 截取全屏
     */
    suspend fun captureScreen(): Bitmap? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            // 使用screencap命令截图
            val result = RootManager.exec("screencap -p ${tempFile.absolutePath}")
            
            if (!result.success || !tempFile.exists()) {
                Timber.e("截图失败: ${result.error}")
                return@withContext null
            }
            
            // 读取图片
            val bitmap = android.graphics.BitmapFactory.decodeFile(tempFile.absolutePath)
            
            val elapsed = System.currentTimeMillis() - startTime
            Timber.d("✅ 截图完成: ${elapsed}ms")
            
            bitmap
        } catch (e: Exception) {
            Timber.e(e, "截图异常")
            null
        } finally {
            // 清理临时文件
            tempFile.delete()
        }
    }
    
    /**
     * 截取指定区域
     */
    suspend fun captureRegion(x: Int, y: Int, width: Int, height: Int): Bitmap? {
        val fullScreen = captureScreen() ?: return null
        
        return try {
            Bitmap.createBitmap(fullScreen, x, y, width, height)
        } catch (e: Exception) {
            Timber.e(e, "区域截图失败")
            null
        } finally {
            fullScreen.recycle()
        }
    }
}