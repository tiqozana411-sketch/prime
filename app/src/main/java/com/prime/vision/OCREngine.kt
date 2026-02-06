package com.prime.vision

import android.graphics.Bitmap
import com.benjaminwan.ocrlibrary.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * OCR识别引擎
 * 基于RapidOCR（PaddleOCR Android封装）
 */
object OCREngine {
    
    private var ocrEngine: OcrEngine? = null
    private var isInitialized = false
    
    /**
     * 初始化OCR引擎
     */
    suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true
        
        try {
            ocrEngine = OcrEngine(modelPath)
            isInitialized = true
            Timber.i("✅ OCR引擎初始化成功")
            true
        } catch (e: Exception) {
            Timber.e(e, "❌ OCR引擎初始化失败")
            false
        }
    }
    
    /**
     * 识别图片中的文字
     */
    suspend fun recognize(bitmap: Bitmap): OCRResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        if (!isInitialized || ocrEngine == null) {
            return@withContext OCRResult(false, emptyList(), "OCR引擎未初始化")
        }
        
        try {
            val engine = ocrEngine ?: return@withContext OCRResult(
                false, 
                emptyList(), 
                "OCR引擎未初始化"
            )
            
            val result = engine.detect(bitmap)
            val elapsed = System.currentTimeMillis() - startTime
            
            val textBlocks = result.textBlocks.map { block ->
                TextBlock(
                    text = block.text,
                    confidence = block.confidence,
                    box = BoundingBox(
                        left = block.boxPoint[0].x.toInt(),
                        top = block.boxPoint[0].y.toInt(),
                        right = block.boxPoint[2].x.toInt(),
                        bottom = block.boxPoint[2].y.toInt()
                    )
                )
            }
            
            Timber.d("✅ OCR识别完成: ${textBlocks.size}个文本块, ${elapsed}ms")
            
            OCRResult(true, textBlocks, null)
        } catch (e: Exception) {
            Timber.e(e, "OCR识别失败")
            OCRResult(false, emptyList(), e.message)
        }
    }
    
    /**
     * 查找包含指定文本的区域
     */
    suspend fun findText(bitmap: Bitmap, targetText: String): TextBlock? {
        val result = recognize(bitmap)
        if (!result.success) return null
        
        return result.textBlocks.firstOrNull { 
            it.text.contains(targetText, ignoreCase = true) 
        }
    }
}

/**
 * OCR识别结果
 */
data class OCRResult(
    val success: Boolean,
    val textBlocks: List<TextBlock>,
    val error: String?
) {
    /**
     * 获取所有文本（用于AIExecutor）
     */
    val text: String
        get() = textBlocks.joinToString("\n") { it.text }
}

/**
 * 文本块
 */
data class TextBlock(
    val text: String,
    val confidence: Float,
    val box: BoundingBox
)

/**
 * 边界框
 */
data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}