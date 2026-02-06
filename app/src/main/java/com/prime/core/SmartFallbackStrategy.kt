package com.prime.core

import android.graphics.Bitmap
import com.prime.vision.FindQuery
import com.prime.vision.FindResult
import com.prime.vision.VisionEngine
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * 智能降级策略
 * 核心思想：多层降级，逐步放宽条件
 */
class SmartFallbackStrategy(
    private val adaptiveParams: AdaptiveParamsManager
) {
    
    /**
     * 智能查找元素（带降级）
     */
    suspend fun findElement(target: String, taskType: String = "find"): FindResult? {
        var attempt = 0
        
        // 策略1: 高精度OCR
        attempt++
        val threshold1 = adaptiveParams.getConfidenceThreshold(taskType, attempt)
        Timber.d("尝试${attempt}: OCR识别 (阈值=${threshold1})")
        
        val result1 = VisionEngine.findElement(FindQuery(text = target))
        if (result1 != null && result1.confidence >= threshold1) {
            Timber.i("✅ 高精度识别成功")
            return result1
        }
        
        delay(200)
        
        // 策略2: 中精度OCR
        attempt++
        val threshold2 = adaptiveParams.getConfidenceThreshold(taskType, attempt)
        Timber.d("尝试${attempt}: OCR识别 (阈值=${threshold2})")
        
        val result2 = VisionEngine.findElement(FindQuery(text = target))
        if (result2 != null && result2.confidence >= threshold2) {
            Timber.i("✅ 中精度识别成功")
            return result2
        }
        
        delay(200)
        
        // 策略3: 低精度OCR
        attempt++
        val threshold3 = adaptiveParams.getConfidenceThreshold(taskType, attempt)
        Timber.d("尝试${attempt}: OCR识别 (阈值=${threshold3})")
        
        val result3 = VisionEngine.findElement(FindQuery(text = target))
        if (result3 != null && result3.confidence >= threshold3) {
            Timber.i("⚠️ 低精度识别成功")
            return result3
        }
        
        // 策略4: 模糊匹配
        Timber.d("尝试${attempt + 1}: 模糊匹配")
        val fuzzyResult = findByFuzzyMatch(target)
        if (fuzzyResult != null) {
            Timber.i("⚠️ 模糊匹配成功")
            return fuzzyResult
        }
        
        Timber.w("❌ 所有策略均失败")
        return null
    }
    
    /**
     * 模糊匹配
     */
    private suspend fun findByFuzzyMatch(target: String): FindResult? {
        // 简化目标文本（去除空格、标点）
        val simplified = target.replace(Regex("[\\s\\p{Punct}]"), "")
        
        val result = VisionEngine.findElement(FindQuery(text = simplified))
        return result
    }
}