package com.prime.vision

import android.graphics.Bitmap
import com.prime.core.AdaptiveParamsManager
import com.prime.core.SmartFallbackStrategy
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

/**
 * 混合视觉引擎 v2.0
 * 整合截图、OCR、图像匹配 + 智能降级
 */
object VisionEngine {
    
    private val adaptiveParams = AdaptiveParamsManager()
    private val fallbackStrategy = SmartFallbackStrategy(adaptiveParams)
    
    suspend fun initialize(ocrModelPath: String): Boolean {
        return OCREngine.initialize(ocrModelPath)
    }
    
    /**
     * 智能查找元素（带降级）
     */
    suspend fun findElementSmart(target: String): FindResult? {
        val startTime = System.currentTimeMillis()
        
        return try {
            val result = fallbackStrategy.findElement(target, "vision")
            
            val duration = System.currentTimeMillis() - startTime
            adaptiveParams.record("vision", duration, result != null)
            
            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            adaptiveParams.record("vision", duration, false)
            Timber.e(e, "智能查找失败")
            null
        }
    }
    
    /**
     * 原有方法保持兼容
     */
    suspend fun findElement(query: FindQuery): FindResult? = coroutineScope {
        val startTime = System.currentTimeMillis()
        
        val screenshot = ScreenCapture.captureScreen()
        if (screenshot == null) {
            Timber.e("截图失败")
            return@coroutineScope null
        }
        
        try {
            val ocrTask = async {
                if (query.text != null) {
                    OCREngine.findText(screenshot, query.text)
                } else null
            }
            
            val imageTask = async {
                if (query.templateImage != null) {
                    ImageMatcher.findImage(screenshot, query.templateImage, query.similarity)
                } else null
            }
            
            val ocrResult = ocrTask.await()
            val imageResult = imageTask.await()
            
            val result = when {
                ocrResult != null && imageResult != null -> {
                    if (ocrResult.confidence > imageResult.similarity) {
                        FindResult.fromOCR(ocrResult)
                    } else {
                        FindResult.fromImage(imageResult)
                    }
                }
                ocrResult != null -> FindResult.fromOCR(ocrResult)
                imageResult != null -> FindResult.fromImage(imageResult)
                else -> null
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            
            if (result != null) {
                Timber.i("✅ 元素查找成功: (${result.x}, ${result.y}), ${elapsed}ms")
            } else {
                Timber.w("❌ 元素查找失败: ${elapsed}ms")
            }
            
            result
        } finally {
            screenshot.recycle()
        }
    }
    
suspend fun waitForElement(
        query: FindQuery,
        timeout: Long = 10000,
        interval: Long = 500
    ): FindResult? {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeout) {
            val result = findElement(query)
            if (result != null) {
                return result
            }
            kotlinx.coroutines.delay(interval)
        }
        
        Timber.w("等待元素超时: ${timeout}ms")
        return null
    }
    
    suspend fun waitForElement(text: String, timeout: Long = 10000): FindResult? {
        return waitForElement(FindQuery(text = text), timeout)
    }
    
    /**
     * 多次验证识别（提高准确率）
     */
    suspend fun recognizeWithVerify(target: String, verifyCount: Int = 3): FindResult? {
        val results = mutableListOf<FindResult>()
        
        repeat(verifyCount) {
            val result = findElement(FindQuery(text = target))
            if (result != null) {
                results.add(result)
            }
            kotlinx.coroutines.delay(200)
        }
        
        if (results.isEmpty()) {
            return null
        }
        
        // 取置信度最高的
        return results.maxByOrNull { it.confidence }
    }
    
    /**
     * 查找元素（字符串重载，用于蜂群系统）
     */
    suspend fun findElement(target: String): FindResult? {
        return findElement(FindQuery(text = target))
    }
    
    /**
     * 截图（用于PrimeController）
     */
    suspend fun captureScreen(): Bitmap? {
        return ScreenCapture.captureScreen()
    }
    
    /**
     * 执行OCR识别（用于PrimeController）
     */
    suspend fun performOCR(bitmap: Bitmap?): OCRResult {
        if (bitmap == null) {
            return OCRResult(false, emptyList(), "bitmap为空")
        }
        return OCREngine.recognize(bitmap)
    }
}

data class FindQuery(
    val text: String? = null,
    val templateImage: Bitmap? = null,
    val similarity: Float = 0.8f
)

data class FindResult(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val confidence: Float,
    val method: String
) {
    val centerX: Int get() = x + width / 2
    val centerY: Int get() = y + height / 2
    
    companion object {
        fun fromOCR(textBlock: TextBlock) = FindResult(
            x = textBlock.box.left,
            y = textBlock.box.top,
            width = textBlock.box.width,
            height = textBlock.box.height,
            confidence = textBlock.confidence,
            method = "ocr"
        )
        
        fun fromImage(matchResult: MatchResult) = FindResult(
            x = matchResult.x,
            y = matchResult.y,
            width = matchResult.width,
            height = matchResult.height,
            confidence = matchResult.similarity,
            method = "image"
        )
    }
}