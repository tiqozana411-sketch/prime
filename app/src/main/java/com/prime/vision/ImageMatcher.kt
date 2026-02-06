package com.prime.vision

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.abs

/**
 * 图像匹配引擎
 * 基于模板匹配算法
 */
object ImageMatcher {
    
    /**
     * 在大图中查找小图
     */
    suspend fun findImage(
        source: Bitmap,
        template: Bitmap,
        threshold: Float = 0.8f
    ): MatchResult? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            val result = templateMatch(source, template, threshold)
            val elapsed = System.currentTimeMillis() - startTime
            
            if (result != null) {
                Timber.d("✅ 图像匹配成功: 相似度${result.similarity}, ${elapsed}ms")
            } else {
                Timber.d("❌ 图像匹配失败: ${elapsed}ms")
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "图像匹配异常")
            null
        }
    }
    
    /**
     * 模板匹配算法（简化版）
     */
    private fun templateMatch(
        source: Bitmap,
        template: Bitmap,
        threshold: Float
    ): MatchResult? {
        val sourceWidth = source.width
        val sourceHeight = source.height
        val templateWidth = template.width
        val templateHeight = template.height
        
        if (templateWidth > sourceWidth || templateHeight > sourceHeight) {
            return null
        }
        
        var bestMatch: MatchResult? = null
        var bestSimilarity = 0f
        
        // 滑动窗口匹配
        for (y in 0..(sourceHeight - templateHeight)) {
            for (x in 0..(sourceWidth - templateWidth)) {
                val similarity = calculateSimilarity(source, template, x, y)
                
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    bestMatch = MatchResult(
                        x = x,
                        y = y,
                        width = templateWidth,
                        height = templateHeight,
                        similarity = similarity
                    )
                }
                
                // 如果找到足够好的匹配，提前退出
                if (similarity >= 0.95f) {
                    return bestMatch
                }
            }
        }
        
        return if (bestSimilarity >= threshold) bestMatch else null
    }
    
    /**
     * 计算相似度（简化版：像素差异）
     */
    private fun calculateSimilarity(
        source: Bitmap,
        template: Bitmap,
        offsetX: Int,
        offsetY: Int
    ): Float {
        val templateWidth = template.width
        val templateHeight = template.height
        var totalDiff = 0L
        var pixelCount = 0
        
        // 采样计算（每隔2个像素采样一次，提高速度）
        for (y in 0 until templateHeight step 2) {
            for (x in 0 until templateWidth step 2) {
                val sourcePixel = source.getPixel(offsetX + x, offsetY + y)
                val templatePixel = template.getPixel(x, y)
                
                val rDiff = abs(android.graphics.Color.red(sourcePixel) - android.graphics.Color.red(templatePixel))
                val gDiff = abs(android.graphics.Color.green(sourcePixel) - android.graphics.Color.green(templatePixel))
                val bDiff = abs(android.graphics.Color.blue(sourcePixel) - android.graphics.Color.blue(templatePixel))
                
                totalDiff += (rDiff + gDiff + bDiff)
                pixelCount++
            }
        }
        
        val avgDiff = totalDiff.toFloat() / pixelCount
        val maxDiff = 255f * 3 // RGB最大差异
        
        return 1f - (avgDiff / maxDiff)
    }
}

/**
 * 匹配结果
 */
data class MatchResult(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val similarity: Float
) {
    val centerX: Int get() = x + width / 2
    val centerY: Int get() = y + height / 2
}