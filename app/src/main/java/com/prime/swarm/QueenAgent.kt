package com.prime.swarm

import com.prime.core.TaskResult
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Log

/**
 * Queen Agent - 女王蜂
 * 职责：主控调度、任务分解、结果汇总
 * * 基于周易因果推理：观察任务 → 推理分解 → 验证结果 → 修正策略
 */
class QueenAgent(
    private val taskDecomposer: TaskDecomposer,
    private val scheduler: SwarmScheduler,
    private val performanceMonitor: PerformanceMonitor
) : SwarmAgent("queen", AgentType.QUEEN) {
    
    private val TAG = "QueenAgent"
    private val executionResults = ConcurrentHashMap<String, SwarmTaskResult>()
    
    // 修复方案：定义一个专用的原子布尔值，避免与协程的 isActive 冲突
    private val isWorking = AtomicBoolean(false)
    
    override suspend fun execute(task: SubTask): TaskResult {
        return TaskResult(false, "Queen不直接执行任务")
    }
    
    override fun canHandle(task: SubTask): Boolean = false
    
    override fun getCapability(): AgentCapability {
        return AgentCapability(
            complexity = 10,
            speed = 10,
            accuracy = 10,
            reliability = 10
        )
    }
    
    /**
     * 执行蜂群任务
     * 五维度平衡：效率 + 准确率 + 容错率 + 严谨性 + 设备性能
     */
    suspend fun executeSwarmTask(
        description: String,
        context: Map<String, Any>
    ): SwarmExecutionResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        
        // 修改点 1：使用自定义的原子变量
        isWorking.set(true)
        
        try {
            // 1. 任务分解（字节级数据筛选）
            Log.d(TAG, "开始任务分解: $description")
            val subTasks = taskDecomposer.decompose(description, context)
            
            if (subTasks.isEmpty()) {
                return@withContext SwarmExecutionResult(
                    success = false,
                    totalTasks = 0,
                    completedTasks = 0,
                    failedTasks = 0,
                    executionTime = System.currentTimeMillis() - startTime,
                    error = "任务分解失败"
                )
            }
            
            // 2. 智能调度（周易因果推理）
            Log.d(TAG, "开始智能调度: ${subTasks.size}个子任务")
            val executionPlan = scheduler.schedule(subTasks)
            
            // 3. 并发执行（严谨多维验证）
            val results = executeWithSwarm(executionPlan)
            
            // 4. 结果汇总
            val successCount = results.count { it.success }
            val failedCount = results.size - successCount
            
            // 5. 性能统计
            val metrics = performanceMonitor.getMetrics()
            
            Log.d(TAG, "蜂群任务完成: 成功$successCount 失败$failedCount")
            Log.d(TAG, "性能指标: $metrics")
            
            SwarmExecutionResult(
                success = failedCount == 0,
                totalTasks = subTasks.size,
                completedTasks = successCount,
                failedTasks = failedCount,
                executionTime = System.currentTimeMillis() - startTime,
                results = results,
                metrics = metrics
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "蜂群任务执行失败", e)
            SwarmExecutionResult(
                success = false,
                totalTasks = 0,
                completedTasks = 0,
                failedTasks = 0,
                executionTime = System.currentTimeMillis() - startTime,
                error = e.message
            )
        } finally {
            // 修改点 2：使用自定义的原子变量
            isWorking.set(false)
        }
    }
    
    /**
     * 使用蜂群并发执行任务
     */
    private suspend fun executeWithSwarm(
        plan: ExecutionPlan
    ): List<SwarmTaskResult> = coroutineScope {
        val results = mutableListOf<SwarmTaskResult>()
        
        // 按阶段执行（处理依赖关系）
        for (stage in plan.stages) {
            val stageResults = stage.tasks.map { task ->
                async {
                    executeSubTask(task, plan.workers)
                }
            }.awaitAll()
            
            results.addAll(stageResults)
            
            // 如果有任务失败且是关键任务，停止执行
            val criticalFailed = stageResults.any { 
                !it.success && stage.tasks.find { t -> t.id == it.taskId }?.priority ?: 0 >= 8
            }
            if (criticalFailed) {
                Log.w(TAG, "关键任务失败，停止执行")
                break
            }
        }
        
        results
    }
    
    /**
     * 执行单个子任务（带容错机制）
     */
    private suspend fun executeSubTask(
        task: SubTask,
        workers: List<WorkerAgent>
    ): SwarmTaskResult {
        val startTime = System.currentTimeMillis()
        
        // 1. 选择合适的Worker
        val worker = selectWorker(task, workers)
        if (worker == null) {
            return SwarmTaskResult(
                taskId = task.id,
                success = false,
                result = null,
                executedBy = "none",
                executionTime = System.currentTimeMillis() - startTime,
                error = "没有可用的Worker"
            )
        }
        
        // 2. 执行任务（带超时和重试）
        var lastError: String? = null
        repeat(task.retryCount) { attempt ->
            try {
                val result = withTimeout(task.timeout) {
                    worker.execute(task)
                }
                
                if (result.success) {
                    performanceMonitor.recordSuccess(worker.id, System.currentTimeMillis() - startTime)
                    return SwarmTaskResult(
                        taskId = task.id,
                        success = true,
                        result = result,
                        executedBy = worker.id,
                        executionTime = System.currentTimeMillis() - startTime
                    )
                } else {
                    lastError = result.message
                }
            } catch (e: TimeoutCancellationException) {
                lastError = "任务超时"
                Log.w(TAG, "任务${task.id}超时，尝试${attempt + 1}/${task.retryCount}")
            } catch (e: Exception) {
                lastError = e.message
                Log.w(TAG, "任务${task.id}失败，尝试${attempt + 1}/${task.retryCount}", e)
            }
            
            if (attempt < task.retryCount - 1) {
                delay(1000L * (attempt + 1)) // 指数退避
            }
        }
        
        // 3. 所有重试都失败
        performanceMonitor.recordFailure(worker.id)
        return SwarmTaskResult(
            taskId = task.id,
            success = false,
            result = null,
            executedBy = worker.id,
            executionTime = System.currentTimeMillis() - startTime,
            error = lastError
        )
    }
    
    /**
     * 选择最合适的Worker
     * 基于：能力匹配 + 负载均衡
     */
    private fun selectWorker(task: SubTask, workers: List<WorkerAgent>): WorkerAgent? {
        return workers
            .filter { it.canHandle(task) && !it.isAgentActive() } // 这里假设父类有检查状态的方法
            .minByOrNull { it.taskCount.get() }
    }

    // 辅助方法：检查当前 Queen 是否正在忙碌
    fun isBusy(): Boolean = isWorking.get()
}

/**
 * 蜂群执行结果
 */
data class SwarmExecutionResult(
    val success: Boolean,
    val totalTasks: Int,
    val completedTasks: Int,
    val failedTasks: Int,
    val executionTime: Long,
    val results: List<SwarmTaskResult> = emptyList(),
    val metrics: PerformanceMetrics? = null,
    val error: String? = null
)
            }.awaitAll()
            
            results.addAll(stageResults)
            
            // 如果有任务失败且是关键任务，停止执行
            val criticalFailed = stageResults.any { 
                !it.success && stage.tasks.find { t -> t.id == it.taskId }?.priority ?: 0 >= 8
            }
            if (criticalFailed) {
                Log.w(TAG, "关键任务失败，停止执行")
                break
            }
        }
        
        results
    }
    
    /**
     * 执行单个子任务（带容错机制）
     */
    private suspend fun executeSubTask(
        task: SubTask,
        workers: List<WorkerAgent>
    ): SwarmTaskResult {
        val startTime = System.currentTimeMillis()
        
        // 1. 选择合适的Worker
        val worker = selectWorker(task, workers)
        if (worker == null) {
            return SwarmTaskResult(
                taskId = task.id,
                success = false,
                result = null,
                executedBy = "none",
                executionTime = System.currentTimeMillis() - startTime,
                error = "没有可用的Worker"
            )
        }
        
        // 2. 执行任务（带超时和重试）
        var lastError: String? = null
        repeat(task.retryCount) { attempt ->
            try {
                val result = withTimeout(task.timeout) {
                    worker.execute(task)
                }
                
                if (result.success) {
                    performanceMonitor.recordSuccess(worker.id, System.currentTimeMillis() - startTime)
                    return SwarmTaskResult(
                        taskId = task.id,
                        success = true,
                        result = result,
                        executedBy = worker.id,
                        executionTime = System.currentTimeMillis() - startTime
                    )
                } else {
                    lastError = result.message
                }
            } catch (e: TimeoutCancellationException) {
                lastError = "任务超时"
                Log.w(TAG, "任务${task.id}超时，尝试${attempt + 1}/${task.retryCount}")
            } catch (e: Exception) {
                lastError = e.message
                Log.w(TAG, "任务${task.id}失败，尝试${attempt + 1}/${task.retryCount}", e)
            }
            
            if (attempt < task.retryCount - 1) {
                delay(1000L * (attempt + 1)) // 指数退避
            }
        }
        
        // 3. 所有重试都失败
        performanceMonitor.recordFailure(worker.id)
        return SwarmTaskResult(
            taskId = task.id,
            success = false,
            result = null,
            executedBy = worker.id,
            executionTime = System.currentTimeMillis() - startTime,
            error = lastError
        )
    }
    
    /**
     * 选择最合适的Worker
     * 基于：能力匹配 + 负载均衡
     */
    private fun selectWorker(task: SubTask, workers: List<WorkerAgent>): WorkerAgent? {
        return workers
            .filter { it.canHandle(task) && !it.isActive.get() }
            .minByOrNull { it.taskCount.get() }
    }
}

/**
 * 蜂群执行结果
 */
data class SwarmExecutionResult(
    val success: Boolean,
    val totalTasks: Int,
    val completedTasks: Int,
    val failedTasks: Int,
    val executionTime: Long,
    val results: List<SwarmTaskResult> = emptyList(),
    val metrics: PerformanceMetrics? = null,
    val error: String? = null
)
