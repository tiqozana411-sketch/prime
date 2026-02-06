package com.prime.swarm

import com.prime.core.TaskResult
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import android.util.Log

/**
 * Queen Agent - 女王蜂
 * 职责：主控调度、任务分解、结果汇总
 */
class QueenAgent(
    private val taskDecomposer: TaskDecomposer,
    private val scheduler: SwarmScheduler,
    private val performanceMonitor: PerformanceMonitor
) : SwarmAgent("queen", AgentType.QUEEN) {
    
    private val TAG = "QueenAgent"
    private val executionResults = ConcurrentHashMap<String, SwarmTaskResult>()
    
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
     */
    suspend fun executeSwarmTask(
        description: String,
        context: Map<String, Any>
    ): SwarmExecutionResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        isActive.set(true)
        
        try {
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
            
            Log.d(TAG, "开始智能调度: ${subTasks.size}个子任务")
            val executionPlan = scheduler.schedule(subTasks)
            val results = executeWithSwarm(executionPlan)
            
            val successCount = results.count { it.success }
            val failedCount = results.size - successCount
            val metrics = performanceMonitor.getMetrics()
            
            Log.d(TAG, "蜂群任务完成: 成功$successCount 失败$failedCount")
            
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
            isActive.set(false)
        }
    }
    
    private suspend fun executeWithSwarm(plan: ExecutionPlan): List<SwarmTaskResult> = coroutineScope {
        val results = mutableListOf<SwarmTaskResult>()
        
        for (stage in plan.stages) {
            val stageResults = stage.tasks.map { task ->
                async { executeSubTask(task, plan.workers) }
            }.awaitAll()
            
            results.addAll(stageResults)
            
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
    
    private suspend fun executeSubTask(task: SubTask, workers: List<WorkerAgent>): SwarmTaskResult {
        val startTime = System.currentTimeMillis()
        
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
        
        var lastError: String? = null
        repeat(task.retryCount) { attempt ->
            try {
                val result = withTimeout(task.timeout) { worker.execute(task) }
                
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
                delay(1000L * (attempt + 1))
            }
        }
        
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