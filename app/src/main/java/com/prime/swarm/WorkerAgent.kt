package com.prime.swarm

import com.prime.ai.AIExecutor
import com.prime.core.TaskResult
import kotlinx.coroutines.*
import android.util.Log

/**
 * Worker Agent - 工蜂
 * 职责：执行具体任务
 * 
 * 基于字节级数据筛选：精准执行每个操作
 */
class WorkerAgent(
    id: String,
    private val executor: AIExecutor,
    private val capability: AgentCapability
) : SwarmAgent(id, AgentType.WORKER) {
    
    private val TAG = "WorkerAgent"
    
    override suspend fun execute(task: SubTask): TaskResult {
        isActive.set(true)
        taskCount.incrementAndGet()
        
        return try {
            Log.d(TAG, "[$id] 开始执行任务: ${task.description}")
            
            val result = when (task.type) {
                TaskType.CLICK -> executeClick(task)
                TaskType.INPUT -> executeInput(task)
                TaskType.SCROLL -> executeScroll(task)
                TaskType.WAIT -> executeWait(task)
                TaskType.VERIFY -> executeVerify(task)
                TaskType.ANALYZE -> executeAnalyze(task)
                TaskType.COMPOSITE -> executeComposite(task)
            }
            
            Log.d(TAG, "[$id] 任务完成: ${result.success}")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "[$id] 任务执行失败", e)
            TaskResult(false, e.message ?: "未知错误")
        } finally {
            isActive.set(false)
        }
    }
    
    override fun canHandle(task: SubTask): Boolean {
        // 检查能力是否匹配
        return task.complexity <= capability.complexity
    }
    
    override fun getCapability(): AgentCapability = capability
    
/**
     * 执行点击操作
     */
    private suspend fun executeClick(task: SubTask): TaskResult {
        val target = task.data["target"] as? String
        
        if (target.isNullOrBlank()) {
            return TaskResult(false, "target参数无效")
        }
        
        return executor.executeClick(target)
    }
    
    /**
     * 执行输入操作
     */
    private suspend fun executeInput(task: SubTask): TaskResult {
        val target = task.data["target"] as? String
        val text = task.data["text"] as? String
        
        if (target.isNullOrBlank()) {
            return TaskResult(false, "target参数无效")
        }
        if (text.isNullOrBlank()) {
            return TaskResult(false, "text参数无效")
        }
        
        return executor.executeInput(target, text)
    }
    
    /**
     * 执行滚动操作
     */
    private suspend fun executeScroll(task: SubTask): TaskResult {
        val direction = task.data["direction"] as? String
        val distance = task.data["distance"] as? Int
        
        if (direction.isNullOrBlank()) {
            return TaskResult(false, "direction参数无效")
        }
        if (distance == null || distance <= 0) {
            return TaskResult(false, "distance参数无效")
        }
        
        // 使用executor的滚动功能
        return try {
            executor.executeScroll(direction, distance)
            TaskResult(true, "滚动成功")
        } catch (e: Exception) {
            TaskResult(false, "滚动失败: ${e.message}")
        }
    }
    
    /**
     * 执行等待操作
     */
    private suspend fun executeWait(task: SubTask): TaskResult {
        val duration = task.data["duration"] as? Long ?: 1000L
        delay(duration)
        return TaskResult(true, "等待完成")
    }
    
    /**
     * 执行验证操作
     */
    private suspend fun executeVerify(task: SubTask): TaskResult {
        val condition = task.data["condition"] as? String ?: return TaskResult(false, "缺少condition参数")
        
        // 使用executor的验证功能
        return try {
            val verified = executor.verifyCondition(condition)
            TaskResult(verified, if (verified) "验证通过" else "验证失败")
        } catch (e: Exception) {
            TaskResult(false, "验证异常: ${e.message}")
        }
    }
    
    /**
     * 执行分析操作
     */
    private suspend fun executeAnalyze(task: SubTask): TaskResult {
        // 使用executor的分析功能
        return try {
            val analysis = executor.analyzeScreen()
            TaskResult(true, "分析完成", data = mapOf("analysis" to analysis))
        } catch (e: Exception) {
            TaskResult(false, "分析失败: ${e.message}")
        }
    }
    
    /**
     * 执行复合操作
     */
    private suspend fun executeComposite(task: SubTask): TaskResult {
        val steps = task.data["steps"] as? List<*> ?: return TaskResult(false, "缺少steps参数")
        
        for (step in steps) {
            val stepTask = step as? SubTask ?: continue
            val result = execute(stepTask)
            if (!result.success) {
                return result
            }
        }
        
        return TaskResult(true, "复合操作完成")
    }
}
