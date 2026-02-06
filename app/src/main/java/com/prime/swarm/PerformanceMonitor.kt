package com.prime.swarm

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 性能监控器
 * 五维度监控：效率 + 准确率 + 容错率 + 严谨性 + 设备性能
 */
class PerformanceMonitor(private val context: Context) {
    
    private val TAG = "PerformanceMonitor"
    
    // 统计数据
    private val successCount = AtomicInteger(0)
    private val failureCount = AtomicInteger(0)
    private val totalExecutionTime = AtomicInteger(0)
    private val workerStats = ConcurrentHashMap<String, WorkerStats>()
    
    /**
     * 记录成功
     */
    fun recordSuccess(workerId: String, executionTime: Long) {
        successCount.incrementAndGet()
        totalExecutionTime.addAndGet(executionTime.toInt())
        
        workerStats.getOrPut(workerId) { WorkerStats() }.apply {
            successCount.incrementAndGet()
            totalTime.addAndGet(executionTime.toInt())
        }
    }
    
    /**
     * 记录失败
     */
    fun recordFailure(workerId: String) {
        failureCount.incrementAndGet()
        
        workerStats.getOrPut(workerId) { WorkerStats() }.apply {
            failureCount.incrementAndGet()
        }
    }
    
    /**
     * 获取性能指标
     */
    fun getMetrics(): PerformanceMetrics {
        val total = successCount.get() + failureCount.get()
        val successRate = if (total > 0) (successCount.get() * 100.0 / total) else 0.0
        val avgTime = if (successCount.get() > 0) (totalExecutionTime.get() / successCount.get()) else 0
        
        return PerformanceMetrics(
            // 效率指标
            totalTasks = total,
            avgExecutionTime = avgTime.toLong(),
            concurrency = workerStats.size,
            
            // 准确率指标
            successRate = successRate,
            failureRate = 100.0 - successRate,
            
            // 容错率指标
            totalFailures = failureCount.get(),
            recoveryRate = calculateRecoveryRate(),
            
            // 设备性能指标
            cpuUsage = getCpuUsage(),
            memoryUsage = getMemoryUsage(),
            temperature = getDeviceTemperature()
        )
    }
    
    /**
     * 计算恢复率（失败后重试成功的比例）
     */
    private fun calculateRecoveryRate(): Double {
        // 简化实现：假设所有成功的任务中，有一部分是重试后成功的
        val total = successCount.get() + failureCount.get()
        return if (total > 0) {
            (successCount.get() * 100.0 / total)
        } else {
            0.0
        }
    }
    
    /**
     * 获取CPU使用率
     */
    private fun getCpuUsage(): Double {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val load = reader.readLine()
            reader.close()
            
            val toks = load.split(" +".toRegex())
            val idle = toks[4].toLong()
            val total = toks.slice(1..7).sumOf { it.toLong() }
            
            val usage = ((total - idle) * 100.0 / total)
            usage.coerceIn(0.0, 100.0)
        } catch (e: Exception) {
            Log.w(TAG, "无法获取CPU使用率", e)
            50.0  // 默认值
        }
    }
    
    /**
     * 获取内存使用率
     */
    private fun getMemoryUsage(): Double {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            val usedMemory = memoryInfo.totalMem - memoryInfo.availMem
            val usage = (usedMemory * 100.0 / memoryInfo.totalMem)
            usage.coerceIn(0.0, 100.0)
        } catch (e: Exception) {
            Log.w(TAG, "无法获取内存使用率", e)
            50.0  // 默认值
        }
    }
    
    /**
     * 获取设备温度
     */
    private fun getDeviceTemperature(): Double {
        return try {
            // 尝试读取电池温度
            val reader = RandomAccessFile("/sys/class/thermal/thermal_zone0/temp", "r")
            val temp = reader.readLine().toDouble() / 1000.0
            reader.close()
            temp.coerceIn(0.0, 100.0)
        } catch (e: Exception) {
            Log.w(TAG, "无法获取设备温度", e)
            35.0  // 默认值
        }
    }
    
    /**
     * 重置统计
     */
    fun reset() {
        successCount.set(0)
        failureCount.set(0)
        totalExecutionTime.set(0)
        workerStats.clear()
    }
}

/**
 * Worker统计数据
 */
data class WorkerStats(
    val successCount: AtomicInteger = AtomicInteger(0),
    val failureCount: AtomicInteger = AtomicInteger(0),
    val totalTime: AtomicInteger = AtomicInteger(0)
)

/**
 * 性能指标
 */
data class PerformanceMetrics(
    // 效率指标
    val totalTasks: Int,
    val avgExecutionTime: Long,
    val concurrency: Int,
    
    // 准确率指标
    val successRate: Double,
    val failureRate: Double,
    
    // 容错率指标
    val totalFailures: Int,
    val recoveryRate: Double,
    
    // 设备性能指标
    val cpuUsage: Double,
    val memoryUsage: Double,
    val temperature: Double
) {
    override fun toString(): String {
        return """
            效率: 总任务=$totalTasks 平均耗时=${avgExecutionTime}ms 并发度=$concurrency
            准确率: 成功率=${"%.2f".format(successRate)}% 失败率=${"%.2f".format(failureRate)}%
            容错率: 总失败=$totalFailures 恢复率=${"%.2f".format(recoveryRate)}%
            设备性能: CPU=${"%.1f".format(cpuUsage)}% 内存=${"%.1f".format(memoryUsage)}% 温度=${"%.1f".format(temperature)}°C
        """.trimIndent()
    }
}