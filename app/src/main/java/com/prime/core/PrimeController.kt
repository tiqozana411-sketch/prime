package com.prime.core

import android.content.Context
import com.prime.ai.AIDecisionEngine
import com.prime.ai.AIExecutor
import com.prime.tools.UIAutomation
import com.prime.vision.VisionEngine
import com.prime.swarm.SwarmManager
import timber.log.Timber

/**
 * PRIME主控制器
 * 核心思想：整合所有模块，提供统一接口
 */
class PrimeController private constructor(
    private val context: Context
) {
    
    // 自适应参数管理
    private val adaptiveParams = AdaptiveParamsManager()
    
    // 智能策略
    private val retryStrategy = SmartRetryStrategy(adaptiveParams)
    private val fallbackStrategy = SmartFallbackStrategy(adaptiveParams)
    
    // AI模块
    private val aiExecutor = AIExecutor(UIAutomation, VisionEngine)
    
    // 蜂群模块
    private var swarmManager: SwarmManager? = null
    
    private var isInitialized = false
    
    companion object {
        @Volatile
        private var instance: PrimeController? = null
        
        fun getInstance(context: Context): PrimeController {
            return instance ?: synchronized(this) {
                instance ?: PrimeController(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    /**
     * 初始化PRIME
     * @param aiConfigPath AI配置文件路径（可选）
     */
    suspend fun initialize(aiConfigPath: String? = null): Boolean {
        if (isInitialized) return true
        
        Timber.i("🚀 PRIME初始化开始")
        
        return try {
            // 初始化AI引擎（支持自定义配置）
            AIDecisionEngine.initialize(aiConfigPath)
            
            // 初始化视觉引擎
            VisionEngine.initialize("/sdcard/PRIME/models/ocr")
            
            // 初始化蜂群系统
            swarmManager = SwarmManager(context, aiExecutor, VisionEngine)
            
            isInitialized = true
            
            // 打印AI Provider信息
            val providerInfo = AIDecisionEngine.getProviderInfo()
            Timber.i("✅ PRIME初始化成功")
            Timber.i("   AI Provider: ${providerInfo["currentProvider"]}")
            Timber.i("   Fallback: ${providerInfo["fallbackProvider"]}")
            
            true
        } catch (e: Exception) {
            Timber.e(e, "❌ PRIME初始化失败")
            false
        }
    }
    
    /**
     * 执行任务（智能模式）
     */
    suspend fun executeTask(task: String): TaskResult {
        if (!isInitialized) {
            return TaskResult.failure("PRIME未初始化")
        }
        
        Timber.i("📋 执行任务: $task")
        
        return retryStrategy.executeWithRetry("task") { attempt ->
            // AI决策
            val context = captureContext()
            val decision = AIDecisionEngine.makeDecision(context, task)
            
            if (!decision.success) {
                throw Exception("AI决策失败")
            }
            
            // 执行步骤
            val success = aiExecutor.execute(decision.steps)
            
            if (success) {
                TaskResult.success("任务完成")
            } else {
                throw Exception("执行失败")
            }
        } ?: TaskResult.failure("任务失败")
    }
    
    /**
     * 执行任务（蜂群模式）
     * 使用多Agent并发执行，提升效率
     */
    suspend fun executeTaskWithSwarm(
        task: String,
        context: Map<String, Any> = emptyMap()
    ): TaskResult {
        if (!isInitialized) {
            return TaskResult.failure("PRIME未初始化")
        }
        
        val manager = swarmManager ?: return TaskResult.failure("蜂群系统未初始化")
        
        Timber.i("🐝 执行蜂群任务: $task")
        
        return try {
            val result = manager.executeTask(task, context)
            
            if (result.success) {
                Timber.i("✅ 蜂群任务完成: ${result.completedTasks}/${result.totalTasks} 耗时${result.executionTime}ms")
                TaskResult.success(
                    "蜂群任务完成: ${result.completedTasks}/${result.totalTasks}",
                    data = result
                )
            } else {
                Timber.w("⚠️ 蜂群任务失败: ${result.error}")
                TaskResult.failure("蜂群任务失败: ${result.error}")
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ 蜂群任务异常")
            TaskResult.failure("蜂群任务异常: ${e.message}")
        }
    }
    
/**
     * 捕获当前上下文（优化版 - 删除重复代码）
     */
    private suspend fun captureContext(): String {
        return try {
            Timber.d("📸 捕获屏幕上下文")
            
            // 1. 截图
            val screenshot = VisionEngine.captureScreen()
            if (screenshot == null) {
                Timber.w("⚠️ 截图失败")
                return "无法获取屏幕内容"
            }
            
            // 2. OCR识别文本
            val ocrResult = VisionEngine.performOCR(screenshot)
            
            // 3. 过滤低置信度文本，按位置排序
            val textBlocks = ocrResult.textBlocks
                .filter { it.confidence > 0.6f }
                .sortedBy { it.box.top }
            
            // 4. 图像分析
            val imageInfo = "屏幕尺寸: ${screenshot.width}x${screenshot.height}"
            
            // 5. 构建结构化上下文
            val context = buildString {
                appendLine("=== 屏幕上下文 ===")
                appendLine(imageInfo)
                appendLine("\n可见文本 (${textBlocks.size}个):")
                
                textBlocks.forEachIndexed { index, block ->
                    val confidence = "%.2f".format(block.confidence * 100)
                    val position = "(${block.box.centerX},${block.box.centerY})"
                    appendLine("  [$index] ${block.text} - 置信度:${confidence}% 位置:$position")
                }
            }
            
            Timber.d("✅ 上下文捕获完成: ${textBlocks.size}个高质量文本")
            screenshot.recycle() // 释放内存
            context
            
        } catch (e: Exception) {
            Timber.e(e, "❌ 上下文捕获失败")
            "上下文捕获失败: ${e.message}"
        }
    }
    
    /**
     * 获取统计数据
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "initialized" to isInitialized,
            "successRate" to adaptiveParams.getSuccessRate("task"),
            "avgDuration" to adaptiveParams.getTimeout("task"),
            "aiProvider" to AIDecisionEngine.getProviderInfo(),
            "swarmMetrics" to if (isInitialized) swarmManager?.getMetrics() else null,
            "swarmAgents" to if (isInitialized) swarmManager?.getAgentStatus() else null
        )
    }
    
    /**
     * 清理资源
     */
    suspend fun cleanup() {
        Timber.i("🧹 PRIME清理资源")
        
        try {
            // 清理AI引擎
            AIDecisionEngine.cleanup()
            
            // 清理视觉引擎（如果有资源）
            // VisionEngine目前没有需要清理的资源
            
            isInitialized = false
            Timber.i("✅ PRIME资源清理完成")
        } catch (e: Exception) {
            Timber.e(e, "❌ PRIME资源清理失败")
        }
    }
}

/**
 * 任务结果
 */
data class TaskResult(
    val success: Boolean,
    val message: String,
    val data: Any? = null,
    val error: Throwable? = null
) {
    companion object {
        fun success(message: String, data: Any? = null) = 
            TaskResult(true, message, data, null)
        
        fun failure(message: String, error: Throwable? = null) = 
            TaskResult(false, message, null, error)
    }
}