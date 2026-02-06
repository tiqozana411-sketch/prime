package com.prime.swarm

import com.prime.core.TaskResult
import android.util.Log

/**
 * Guard Agent - 守卫蜂
 * 职责：门控检查、风险评估
 * 
 * 基于严谨多维验证：执行前检查 → 执行中监控 → 执行后验证
 */
class GuardAgent(
    id: String
) : SwarmAgent(id, AgentType.GUARD) {
    
    private val TAG = "GuardAgent"
    
    override suspend fun execute(task: SubTask): TaskResult {
        return TaskResult(false, "Guard不直接执行任务")
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
     * 执行前门控检查
     * 严谨性：门控通过率100%
     */
    fun checkBeforeExecution(task: SubTask, context: Map<String, Any>): GateCheckResult {
        Log.d(TAG, "[$id] 执行前门控检查: ${task.description}")
        
        val issues = mutableListOf<String>()
        
        // 1. 检查任务参数完整性
        when (task.type) {
            TaskType.CLICK -> {
                if (!task.data.containsKey("target")) {
                    issues.add("缺少target参数")
                }
            }
            TaskType.INPUT -> {
                if (!task.data.containsKey("target") || !task.data.containsKey("text")) {
                    issues.add("缺少target或text参数")
                }
            }
            TaskType.SCROLL -> {
                // 滚动操作参数可选
            }
            TaskType.WAIT -> {
                // 等待操作参数可选
            }
            TaskType.VERIFY -> {
                if (!task.data.containsKey("condition")) {
                    issues.add("缺少condition参数")
                }
            }
            TaskType.ANALYZE -> {
                // 分析操作不需要额外参数
            }
            TaskType.COMPOSITE -> {
                if (!task.data.containsKey("steps")) {
                    issues.add("缺少steps参数")
                }
            }
        }
        
        // 2. 检查任务复杂度合理性
        if (task.complexity < 1 || task.complexity > 10) {
            issues.add("任务复杂度超出范围: ${task.complexity}")
        }
        
        // 3. 检查任务优先级合理性
        if (task.priority < 1 || task.priority > 10) {
            issues.add("任务优先级超出范围: ${task.priority}")
        }
        
        // 4. 检查超时时间合理性
        if (task.timeout < 1000 || task.timeout > 300000) {
            issues.add("超时时间不合理: ${task.timeout}ms")
        }
        
        // 5. 检查重试次数合理性
        if (task.retryCount < 1 || task.retryCount > 5) {
            issues.add("重试次数不合理: ${task.retryCount}")
        }
        
        val passed = issues.isEmpty()
        Log.d(TAG, "[$id] 门控检查${if (passed) "通过" else "失败"}: ${issues.joinToString()}")
        
        return GateCheckResult(
            passed = passed,
            issues = issues,
            risk = calculateRisk(task, issues)
        )
    }
    
    /**
     * 执行后结果验证
     */
    fun verifyResult(task: SubTask, result: TaskResult): VerificationResult {
        Log.d(TAG, "[$id] 结果验证: ${task.description}")
        
        val issues = mutableListOf<String>()
        
        // 1. 检查结果完整性
        if (result.message.isEmpty()) {
            issues.add("结果消息为空")
        }
        
        // 2. 检查关键任务的成功率
        if (task.priority >= 8 && !result.success) {
            issues.add("关键任务失败")
        }
        
        val verified = issues.isEmpty() || result.success
        Log.d(TAG, "[$id] 验证${if (verified) "通过" else "失败"}: ${issues.joinToString()}")
        
        return VerificationResult(
            verified = verified,
            issues = issues,
            confidence = if (result.success) 1.0 else 0.0
        )
    }
    
    /**
     * 计算任务风险等级
     */
    private fun calculateRisk(task: SubTask, issues: List<String>): RiskLevel {
        return when {
            issues.isNotEmpty() -> RiskLevel.HIGH
            task.priority >= 8 -> RiskLevel.MEDIUM
            task.complexity >= 8 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }
}

/**
 * 门控检查结果
 */
data class GateCheckResult(
    val passed: Boolean,
    val issues: List<String>,
    val risk: RiskLevel
)

/**
 * 验证结果
 */
data class VerificationResult(
    val verified: Boolean,
    val issues: List<String>,
    val confidence: Double
)

/**
 * 风险等级
 */
enum class RiskLevel {
    LOW,     // 低风险
    MEDIUM,  // 中风险
    HIGH     // 高风险
}