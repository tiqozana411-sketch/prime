package com.prime.swarm

import com.prime.ai.AIDecision
import com.prime.core.TaskResult
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 蜂群Agent基类
 * 基于PRIME三大思想：字节级数据筛选、周易因果推理、严谨多维验证
 */
sealed class SwarmAgent(
    val id: String,
    val type: AgentType
) {
    val isActive = AtomicBoolean(false)
    val taskCount = AtomicInteger(0)
    
    abstract suspend fun execute(task: SubTask): TaskResult
    abstract fun canHandle(task: SubTask): Boolean
    abstract fun getCapability(): AgentCapability
}

/**
 * Agent类型
 */
enum class AgentType {
    QUEEN,   // 女王：主控调度
    WORKER,  // 工蜂：执行任务
    SCOUT,   // 侦察蜂：探索环境
    GUARD    // 守卫蜂：门控检查
}

/**
 * Agent能力评估
 */
data class AgentCapability(
    val complexity: Int,      // 可处理的任务复杂度 1-10
    val speed: Int,           // 执行速度 1-10
    val accuracy: Int,        // 准确率 1-10
    val reliability: Int      // 可靠性 1-10
)

/**
 * 子任务
 */
data class SubTask(
    val id: String,
    val type: TaskType,
    val description: String,
    val complexity: Int,      // 复杂度 1-10
    val priority: Int,        // 优先级 1-10
    val dependencies: List<String> = emptyList(),  // 依赖的任务ID
    val timeout: Long = 30000L,  // 超时时间（毫秒）
    val retryCount: Int = 3,     // 重试次数
    val data: Map<String, Any> = emptyMap()
)

/**
 * 任务类型
 */
enum class TaskType {
    CLICK,      // 点击操作
    INPUT,      // 输入操作
    SCROLL,     // 滚动操作
    WAIT,       // 等待操作
    VERIFY,     // 验证操作
    ANALYZE,    // 分析操作
    COMPOSITE   // 复合操作
}

/**
 * 任务执行结果
 */
data class SwarmTaskResult(
    val taskId: String,
    val success: Boolean,
    val result: TaskResult?,
    val executedBy: String,
    val executionTime: Long,
    val error: String? = null
)
