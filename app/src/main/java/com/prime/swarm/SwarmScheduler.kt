package com.prime.swarm

import android.util.Log

/**
 * 蜂群调度器
 * 基于周易因果推理：评估任务 → 匹配Worker → 负载均衡
 */
class SwarmScheduler(
    private val workers: List<WorkerAgent>,
    private val performanceMonitor: PerformanceMonitor
) {
    
    private val TAG = "SwarmScheduler"
    
    /**
     * 生成执行计划
     */
    fun schedule(tasks: List<SubTask>): ExecutionPlan {
        Log.d(TAG, "开始调度: ${tasks.size}个任务")
        
        // 1. 分析依赖关系，生成执行阶段
        val stages = buildExecutionStages(tasks)
        
        // 2. 根据设备性能调整Worker数量
        val activeWorkers = adjustWorkerCount(workers)
        
        Log.d(TAG, "调度完成: ${stages.size}个阶段, ${activeWorkers.size}个Worker")
        
        return ExecutionPlan(
            stages = stages,
            workers = activeWorkers
        )
    }
    
    /**
     * 构建执行阶段（处理依赖关系）
     */
    private fun buildExecutionStages(tasks: List<SubTask>): List<ExecutionStage> {
        val stages = mutableListOf<ExecutionStage>()
        val completed = mutableSetOf<String>()
        val remaining = tasks.toMutableList()
        
        while (remaining.isNotEmpty()) {
            // 找出所有依赖已满足的任务
            val ready = remaining.filter { task ->
                task.dependencies.all { it in completed }
            }
            
            if (ready.isEmpty()) {
                // 存在循环依赖或无法满足的依赖
                Log.w(TAG, "检测到依赖问题，强制执行剩余任务")
                stages.add(ExecutionStage(remaining.toList()))
                break
            }
            
            // 创建新阶段
            stages.add(ExecutionStage(ready))
            
            // 更新状态
            completed.addAll(ready.map { it.id })
            remaining.removeAll(ready)
        }
        
        return stages
    }
    
    /**
     * 根据设备性能调整Worker数量
     * 五维度平衡：设备性能自适应
     */
    private fun adjustWorkerCount(workers: List<WorkerAgent>): List<WorkerAgent> {
        val metrics = performanceMonitor.getMetrics()
        
        val maxWorkers = when {
            metrics.cpuUsage > 80 || metrics.memoryUsage > 80 || metrics.temperature > 45 -> {
                Log.w(TAG, "设备性能较低，限制Worker数量为1")
                1  // 极低端
            }
            metrics.cpuUsage > 60 || metrics.memoryUsage > 60 || metrics.temperature > 40 -> {
                Log.d(TAG, "设备性能中等，限制Worker数量为2")
                2  // 低端
            }
            metrics.cpuUsage > 40 || metrics.memoryUsage > 40 -> {
                Log.d(TAG, "设备性能良好，限制Worker数量为3")
                3  // 中等
            }
            else -> {
                Log.d(TAG, "设备性能优秀，使用全部Worker")
                5  // 高性能
            }
        }
        
        return workers.take(maxWorkers)
    }
}

/**
 * 执行计划
 */
data class ExecutionPlan(
    val stages: List<ExecutionStage>,
    val workers: List<WorkerAgent>
)

/**
 * 执行阶段（同一阶段的任务可以并发执行）
 */
data class ExecutionStage(
    val tasks: List<SubTask>
)
