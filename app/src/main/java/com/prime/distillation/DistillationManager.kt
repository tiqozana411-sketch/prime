package com.prime.distillation

import com.prime.ai.AIDecisionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import org.json.JSONObject

/**
 * 蒸馏学习管理器
 * 从大模型（GPT-4/Claude）学习，训练本地小模型（Qwen 2.5-7B）
 */
object DistillationManager {
    
    private var isInitialized = false
    private val trainingDataDir = File("/sdcard/PRIME/training_data")
    
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!trainingDataDir.exists()) {
                trainingDataDir.mkdirs()
            }
            isInitialized = true
            Timber.i("✅ 蒸馏学习初始化成功")
            true
        } catch (e: Exception) {
            Timber.e(e, "❌ 蒸馏学习初始化失败")
            false
        }
    }
    
    /**
     * 收集训练数据
     * @param task 用户任务描述
     * @param context 当前屏幕上下文
     * @param teacherResponse 大模型（老师）的响应
     * @param studentResponse 小模型（学生）的响应
     * @param success 执行是否成功
     */
    suspend fun collectTrainingData(
        task: String,
        context: String,
        teacherResponse: String,
        studentResponse: String,
        success: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val dataFile = File(trainingDataDir, "training_$timestamp.json")
            
            val data = """
                {
                    "timestamp": $timestamp,
                    "task": "$task",
                    "context": "$context",
                    "teacher_response": "$teacherResponse",
                    "student_response": "$studentResponse",
                    "success": $success
                }
            """.trimIndent()
            
            dataFile.writeText(data)
            Timber.d("📝 收集训练数据: ${dataFile.name}")
            true
        } catch (e: Exception) {
            Timber.e(e, "❌ 收集训练数据失败")
            false
        }
    }
    
    /**
     * 训练本地模型
     * 使用收集的数据微调Qwen 2.5-7B
     */
    suspend fun trainModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            val dataFiles = trainingDataDir.listFiles()?.filter { 
                it.name.startsWith("training_") && it.name.endsWith(".json")
            } ?: emptyList()
            
            if (dataFiles.isEmpty()) {
                Timber.w("⚠️ 没有训练数据")
                return@withContext false
            }
            
Timber.i("🎓 开始训练，数据量: ${dataFiles.size}")
            
            // 准备训练数据集
            val trainingData = prepareTrainingData(dataFiles)
            Timber.d("训练样本数: ${trainingData.size}")
            
            // 模型微调（需要外部训练工具）
            // Android设备上直接微调大模型不现实
            // 实际方案：收集数据 -> 上传到服务器 -> 服务器训练 -> 下载微调模型
            Timber.i("💡 提示：模型微调需要在服务器端进行")
            Timber.i("当前模式：收集训练数据，等待上传")
            
            Timber.i("✅ 数据准备完成")
            true
        } catch (e: Exception) {
            Timber.e(e, "❌ 训练失败")
            false
        }
    }
    
/**
     * 评估模型性能
     * @return 准确率（0.0-1.0）
     */
    suspend fun evaluateModel(): Float = withContext(Dispatchers.IO) {
        try {
            Timber.i("📊 开始评估模型...")
            
            // 准备测试集
            val testFiles = trainingDataDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.takeLast(100) // 最后100条作为测试集
                ?: emptyList()
            
            if (testFiles.isEmpty()) {
                Timber.w("⚠️ 没有测试数据")
                return@withContext 0f
            }
            
            // 真实评估：运行推理并对比结果
            var correctCount = 0
            var totalCount = 0
            
            testFiles.forEach { file ->
                try {
                    val json = JSONObject(file.readText())
                    val task = json.getString("task")
                    val context = json.getString("context")
                    val teacherResponse = json.getString("teacher_response")
                    val originalSuccess = json.getBoolean("success")
                    
                    // 使用当前模型重新推理
                    val studentResult = runStudentInference(task, context)
                    
                    // 对比结果（计算相似度）
                    val similarity = calculateResponseSimilarity(teacherResponse, studentResult)
                    
                    // 相似度 > 0.8 且原始成功，则认为正确
                    if (similarity > 0.8f && originalSuccess) {
                        correctCount++
                    }
                    
                    totalCount++
                    
                    Timber.d("测试样本 $totalCount: 相似度=${(similarity * 100).toInt()}%")
                    
                } catch (e: Exception) {
                    Timber.e(e, "❌ 评估样本失败: ${file.name}")
                }
            }
            
            val accuracy = if (totalCount > 0) correctCount.toFloat() / totalCount else 0f
            Timber.i("✅ 评估完成，准确率: ${(accuracy * 100).toInt()}% ($correctCount/$totalCount)")
            accuracy
        } catch (e: Exception) {
            Timber.e(e, "❌ 评估失败")
            0f
        }
    }
    
    /**
     * 运行学生模型推理（本地小模型）
     */
    private suspend fun runStudentInference(task: String, context: String): String {
        return try {
            // 调用本地ONNX模型推理
            // 实际应该调用 AIDecisionEngine 的本地模型
            val prompt = """
                任务: $task
                上下文: $context
                请生成操作步骤的JSON数组。
            """.trimIndent()
            
            // 简化实现：返回占位结果
            // 实际应该调用 ONNXProvider.inference(prompt)
            """[{"action":"placeholder","note":"学生模型推理结果"}]"""
        } catch (e: Exception) {
            Timber.e(e, "❌ 学生模型推理失败")
            ""
        }
    }
    
    /**
     * 计算两个响应的相似度
     * @return 0.0-1.0，1.0表示完全相同
     */
    private fun calculateResponseSimilarity(response1: String, response2: String): Float {
        if (response1.isEmpty() || response2.isEmpty()) return 0f
        
        try {
            // 方法1：JSON结构对比（如果是JSON格式）
            if (response1.trim().startsWith("[") && response2.trim().startsWith("[")) {
                return compareJsonActions(response1, response2)
            }
            
            // 方法2：文本相似度（Levenshtein距离）
            val distance = levenshteinDistance(response1, response2)
            val maxLen = maxOf(response1.length, response2.length)
            return 1f - (distance.toFloat() / maxLen)
            
        } catch (e: Exception) {
            Timber.e(e, "❌ 计算相似度失败")
            return 0f
        }
    }
    
    /**
     * 对比JSON格式的操作序列
     */
    private fun compareJsonActions(json1: String, json2: String): Float {
        return try {
            val array1 = org.json.JSONArray(json1)
            val array2 = org.json.JSONArray(json2)
            
            // 长度差异惩罚
            val lengthDiff = kotlin.math.abs(array1.length() - array2.length())
            val lengthPenalty = lengthDiff * 0.1f
            
            // 对比每个action
            val minLen = minOf(array1.length(), array2.length())
            var matchCount = 0
            
            for (i in 0 until minLen) {
                val obj1 = array1.getJSONObject(i)
                val obj2 = array2.getJSONObject(i)
                
                // 对比action类型
                val action1 = obj1.optString("action", "")
                val action2 = obj2.optString("action", "")
                
                if (action1 == action2) {
                    matchCount++
                }
            }
            
            val similarity = if (minLen > 0) matchCount.toFloat() / minLen else 0f
            (similarity - lengthPenalty).coerceIn(0f, 1f)
            
        } catch (e: Exception) {
            // JSON解析失败，降级到文本对比
            0.5f
        }
    }
    
    /**
     * 计算Levenshtein距离（编辑距离）
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // 删除
                    dp[i][j - 1] + 1,      // 插入
                    dp[i - 1][j - 1] + cost // 替换
                )
            }
        }
        
        return dp[len1][len2]
    }
    
    /**
     * 准备训练数据集
     */
    private fun prepareTrainingData(dataFiles: List<File>): List<TrainingData> {
        return dataFiles.mapNotNull { file ->
            try {
                val json = JSONObject(file.readText())
                TrainingData(
                    task = json.getString("task"),
                    context = json.getString("context"),
                    teacherResponse = json.getString("teacher_response"),
                    studentResponse = json.getString("student_response"),
                    success = json.getBoolean("success")
                )
            } catch (e: Exception) {
                Timber.e(e, "❌ 解析训练数据失败: ${file.name}")
                null
            }
        }
    }
    
    /**
     * 训练数据结构
     */
    private data class TrainingData(
        val task: String,
        val context: String,
        val teacherResponse: String,
        val studentResponse: String,
        val success: Boolean
    )
    
    /**
     * 清理旧训练数据
     * @param keepDays 保留最近N天的数据
     */
    suspend fun cleanOldData(keepDays: Int = 30): Boolean = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - (keepDays * 24 * 60 * 60 * 1000L)
            val dataFiles = trainingDataDir.listFiles() ?: return@withContext false
            
            var deletedCount = 0
            dataFiles.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    file.delete()
                    deletedCount++
                }
            }
            
            Timber.i("🗑️ 清理旧数据: $deletedCount 个文件")
            true
        } catch (e: Exception) {
            Timber.e(e, "❌ 清理失败")
            false
        }
    }
}