package com.prime.skill

import org.json.JSONObject

/**
 * Skill接口规范
 * 所有动态生成的Skill必须实现此接口
 */
interface ISkill {
    
    /** Skill唯一ID */
    val id: String
    
    /** Skill名称 */
    val name: String
    
    /** Skill描述 */
    val description: String
    
    /** Skill版本 */
    val version: String
    
    /** 作者 */
    val author: String
    
    /** 执行Skill */
    suspend fun execute(params: JSONObject): SkillResult
    
    /** 验证参数 */
    fun validate(params: JSONObject): Boolean
}

/**
 * Skill执行结果
 */
data class SkillResult(
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null,
    val logs: List<String> = emptyList()
)

/**
 * Skill元数据
 */
data class SkillMetadata(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    val parameters: List<SkillParameter>,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Skill参数定义
 */
data class SkillParameter(
    val name: String,
    val type: String, // string, int, boolean, object, array
    val required: Boolean,
    val description: String,
    val defaultValue: Any? = null
)