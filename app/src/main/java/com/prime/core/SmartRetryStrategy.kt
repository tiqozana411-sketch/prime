package com.prime.core

import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * 智能重试策略
 * 核心思想：根据任务类型和历史数据决定重试策略
 */
class SmartRetryStrategy(
    private val adaptiveParams: AdaptiveParamsManager
) {
    
/**
     * 带重试的执行
     */
    suspend fun <T> executeWithRetry(
        taskType: String,
        action: suspend (attempt: Int) -> T
    ): T? {
        val maxRetries = adaptiveParams.getRetryCount(taskType)
        val startTime = System.currentTimeMillis()
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                Timber.d("执行${taskType}: 第${attempt + 1}/${maxRetries}次")
                
                val result = action(attempt + 1)
                
                // 记录成功
                val duration = System.currentTimeMillis() - startTime
                adaptiveParams.record(taskType, duration, true)
                
                return result
                
            } catch (e: Exception) {
                lastException = e
                Timber.w("第${attempt + 1}次失败: ${e.message}")
                
                if (attempt == maxRetries - 1) {
                    // 最后一次失败，记录并返回null
                    val duration = System.currentTimeMillis() - startTime
                    adaptiveParams.record(taskType, duration, false)
                    Timber.e("所有重试失败: ${e.message}")
                    return null
                }
                
                // 指数退避
                val delayMs = calculateBackoff(attempt)
                Timber.d("等待${delayMs}ms后重试")
                delay(delayMs)
            }
        }
        
        // 理论上不会到这里，但为了安全返回null
        Timber.e("重试逻辑异常: ${lastException?.message}")
        return null
    }
    
    /**
     * 计算退避时间（指数增长）
     */
    private fun calculateBackoff(attempt: Int): Long {
        return (1000 * Math.pow(1.5, attempt.toDouble())).toLong()
            .coerceAtMost(10000) // 最多10秒
    }
}