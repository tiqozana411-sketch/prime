package com.prime.swarm

import android.content.Context
import com.prime.ai.AIExecutor
import com.prime.vision.VisionEngine
import android.util.Log

/**
 * 蜂群管理器
 * 统一管理所有Agent，提供蜂群功能的入口
 */
class SwarmManager(
    private val context: Context,
    private val executor: AIExecutor,
    private val visionEngine: VisionEngine
) {
    
    private val TAG = "SwarmManager"
    
    // 核心组件
    private val performanceMonitor = PerformanceMonitor(context)
    private val taskDecomposer = TaskDecomposer()
    
    // Agent池
    private val workers = createWorkers()
    private val scheduler = SwarmScheduler(workers, performanceMonitor)
    private val queen = QueenAgent(taskDecomposer, scheduler, performanceMonitor)
    private val guard = GuardAgent("guard_1")
    private val scout = ScoutAgent("scout_1", visionEngine)
    
    /**
     * 创建Worker池（优化版：根据设备性能动态调整）
     */
    private fun createWorkers(): List<WorkerAgent> {
        val metrics = performanceMonitor.getMetrics()
        
        // 根据设备性能决定Worker数量
        val workerCount = when {
            metrics.memoryUsage > 80 || metrics.cpuUsage > 80 -> 2  // 低端设备
            metrics.memoryUsage > 60 || metrics.cpuUsage > 60 -> 3  // 中端设备
            else -> 5  // 高端设备
        }
        
        Log.d(TAG, "创建${workerCount}个Worker (CPU:${metrics.cpuUsage}% MEM:${metrics.memoryUsage}%)")
        
        return (1..workerCount).map { i ->
            // 能力递减：第一个Worker最强
            val baseCapability = 10 - i
            WorkerAgent("worker_$i", executor, AgentCapability(
                complexity = (baseCapability - 2).coerceAtLeast(5),
                speed = (baseCapability - 1).coerceAtLeast(6),
                accuracy = (baseCapability - 1).coerceAtLeast(6),
                reliability = (baseCapability - 1).coerceAtLeast(6)
            ))
        }
    }
    
    /**
     * 执行蜂群任务
     * 
     * @param description 任务描述
     * @param context 任务上下文
     * @param useSwarm 是否使用蜂群模式（默认true）
     * @return 执行结果
     */
    suspend fun executeTask(
        description: String,
        context: Map<String, Any> = emptyMap(),
        useSwarm: Boolean = true
    ): SwarmExecutionResult {
        Log.d(TAG, "收到任务: $description (蜂群模式: $useSwarm)")
        
        return if (useSwarm) {
            // 使用蜂群并发执行
            queen.executeSwarmTask(description, context)
        } else {
            // 降级到单Agent执行
            executeSingleAgent(description, context)
        }
    }
    
    /**
     * 单Agent执行（降级模式 - 优化异常处理和日志）
     */
    private suspend fun executeSingleAgent(
        description: String,
        context: Map<String, Any>
    ): SwarmExecutionResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // 分解任务
            val subTasks = taskDecomposer.decompose(description, context)
            Log.d(TAG, "任务分解完成: ${subTasks.size}个子任务")
            
            // 使用第一个Worker串行执行
            val worker = workers.firstOrNull() 
                ?: throw IllegalStateException("没有可用的Worker")
            
            val results = mutableListOf<SwarmTaskResult>()
            
            for ((index, task) in subTasks.withIndex()) {
                val taskStartTime = System.currentTimeMillis()
                Log.d(TAG, "执行子任务 ${index + 1}/${subTasks.size}: ${task.description}")
                
                val result = try {
                    worker.execute(task)
                } catch (e: Exception) {
                    Log.e(TAG, "子任务执行异常: ${task.description}", e)
                    TaskResult(false, "执行异常: ${e.message}", error = e)
                }
                
                results.add(SwarmTaskResult(
                    taskId = task.id,
                    success = result.success,
                    result = result,
                    executedBy = worker.id,
                    executionTime = System.currentTimeMillis() - taskStartTime
                ))
                
                // 关键任务失败，停止执行
                if (!result.success && task.priority >= 8) {
                    Log.w(TAG, "关键任务失败，停止执行: ${task.description}")
                    break
                }
            }
            
            val successCount = results.count { it.success }
            val executionTime = System.currentTimeMillis() - startTime
            
            Log.i(TAG, "单Agent执行完成: $successCount/${subTasks.size} 耗时${executionTime}ms")
            
            SwarmExecutionResult(
                success = successCount == subTasks.size,
                totalTasks = subTasks.size,
                completedTasks = successCount,
                failedTasks = subTasks.size - successCount,
                executionTime = executionTime,
                results = results
            )
            
} catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "单Agent执行失败", e)
            
            SwarmExecutionResult(
                success = false,
                totalTasks = 0,
                completedTasks = 0,
                failedTasks = 0,
                executionTime = executionTime,
                error = "执行失败: ${e.javaClass.simpleName} - ${e.message}\n${e.stackTraceToString()}"
            )
        }
    }
    
    /**
     * 探索屏幕环境
     */
    suspend fun exploreScreen(): ScreenEnvironment {
        return scout.exploreScreen()
    }
    
    /**
     * 门控检查
     */
    fun checkTask(task: SubTask, context: Map<String, Any>): GateCheckResult {
        return guard.checkBeforeExecution(task, context)
    }
    
    /**
     * 获取性能指标
     */
    fun getMetrics(): PerformanceMetrics {
        return performanceMonitor.getMetrics()
    }
    
    /**
     * 重置统计
     */
    fun resetMetrics() {
        performanceMonitor.reset()
    }
    
    /**
     * 获取Agent状态
     */
    fun getAgentStatus(): Map<String, Any> {
        return mapOf(
            "queen" to mapOf(
                "id" to queen.id,
                "type" to queen.type.name,
                "active" to queen.isActive.get()
            ),
            "workers" to workers.map { worker ->
                mapOf(
                    "id" to worker.id,
                    "active" to worker.isActive.get(),
                    "taskCount" to worker.taskCount.get(),
                    "capability" to worker.getCapability()
                )
            },
            "scout" to mapOf(
                "id" to scout.id,
                "type" to scout.type.name,
                "active" to scout.isActive.get()
            ),
            "guard" to mapOf(
                "id" to guard.id,
                "type" to guard.type.name,
                "active" to guard.isActive.get()
            ),
            "totalAgents" to (1 + workers.size + 1 + 1)
        )
    }
    
    /**
     * 重置统计（别名方法）
     */
    fun reset() = resetMetrics()
}