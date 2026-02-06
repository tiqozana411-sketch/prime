package com.prime.core

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * 自适应参数管理器
 * 核心思想：根据历史数据动态调整参数
 */
class AdaptiveParamsManager {
    
    private val metrics = ConcurrentHashMap<String, TaskMetrics>()
    
    /**
     * 记录任务执行结果
     */
    fun record(taskType: String, duration: Long, success: Boolean) {
        val metric = metrics.getOrPut(taskType) { TaskMetrics() }
        
        metric.totalCount++
        if (success) metric.successCount++
        metric.totalDuration += duration
        
        // 记录最近10次结果
        metric.recentResults.add(success)
        if (metric.recentResults.size > 10) {
            metric.recentResults.removeAt(0)
        }
        
        // 异常检测
        if (duration > metric.getAvgDuration() * 3) {
            Timber.w("⚠️ ${taskType}耗时异常: ${duration}ms")
        }
    }
    
    /**
     * 获取重试次数（数据驱动）
     */
    fun getRetryCount(taskType: String): Int {
        val successRate = getSuccessRate(taskType)
        return when {
            successRate < 0.7 -> 5  // 成功率低，多重试
            successRate < 0.9 -> 3  // 中等
            else -> 1               // 成功率高，少重试
        }
    }
    
    /**
     * 获取超时时间（数据驱动）
     */
    fun getTimeout(taskType: String): Long {
        val metric = metrics[taskType]
        if (metric == null || metric.totalCount == 0) {
            return 10000 // 默认10秒
        }
        
        val avgDuration = metric.getAvgDuration()
        return (avgDuration * 2).coerceAtLeast(2000) // 至少2秒
    }
    
    /**
     * 获取成功率
     */
    fun getSuccessRate(taskType: String): Float {
        val metric = metrics[taskType] ?: return 1f
        return if (metric.totalCount > 0) {
            metric.successCount.toFloat() / metric.totalCount
        } else 1f
    }
    
    /**
     * 是否应该降级
     */
    fun shouldUseFallback(taskType: String): Boolean {
        val metric = metrics[taskType] ?: return false
        
        // 最近10次中失败超过5次
        val recentFailures = metric.recentResults.count { !it }
        return recentFailures > 5
    }
    
    /**
     * 获取置信度阈值（动态调整）
     */
    fun getConfidenceThreshold(taskType: String, attempt: Int): Float {
        val successRate = getSuccessRate(taskType)
        
        // 成功率高，提高阈值（追求准确）
        // 成功率低，降低阈值（追求成功）
        val baseThreshold = when {
            successRate > 0.9 -> 0.85f
            successRate > 0.8 -> 0.75f
            else -> 0.65f
        }
        
        // 重试次数越多，阈值越低
        return (baseThreshold - attempt * 0.05f).coerceAtLeast(0.5f)
    }
}

/**
 * 任务指标
 */
data class TaskMetrics(
    var totalCount: Int = 0,
    var successCount: Int = 0,
    var totalDuration: Long = 0,
    val recentResults: MutableList<Boolean> = mutableListOf()
) {
    fun getAvgDuration(): Long {
        return if (totalCount > 0) totalDuration / totalCount else 1000
    }
}