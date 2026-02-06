package com.prime.vision

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * OCR识别引擎
 * 注意：OCR库暂时禁用，使用桩实现
 * TODO: 恢复 RapidOCR 依赖后启用完整功能
 */
object OCREngine {
    
    private var isInitialized = false
    
    /**
     * 初始化OCR引擎
     */
    suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        Timber.w("⚠️ OCR引擎未启用 (依赖暂时禁用)")
        isInitialized = true
        true
    }
    
    /**
     * 识别图片中的文字（桩实现）
     */
    suspend fun recognize(bitmap: Bitmap): OCRResult = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            return@withContext OCRResult(false, emptyList(), "OCR引擎未初始化")
        }
        
        Timber.w("⚠️ OCR功能暂时禁用，返回空结果")
        OCRResult(true, emptyList(), null)
    }
    
    /**
     * 查找包含指定文本的区域
     */
    suspend fun findText(bitmap: Bitmap, targetText: String): TextBlock? {
        return null
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