package com.prime.skill

import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Skill动态加载器
 * 负责加载、卸载、管理Skill
 */
object SkillLoader {
    
    private val skills = mutableMapOf<String, ISkill>()
    private val skillDir = File("/sdcard/PRIME/skills")
    
    init {
        if (!skillDir.exists()) {
            skillDir.mkdirs()
        }
    }
    
    /**
     * 加载Skill
     */
    suspend fun loadSkill(skillPath: String): Result<ISkill> = withContext(Dispatchers.IO) {
        try {
            val dexFile = File(skillPath)
            if (!dexFile.exists()) {
                return@withContext Result.failure(Exception("Skill文件不存在: $skillPath"))
            }
            
            val optimizedDir = File(skillDir, "optimized")
            if (!optimizedDir.exists()) {
                optimizedDir.mkdirs()
            }
            
            val classLoader = DexClassLoader(
                dexFile.absolutePath,
                optimizedDir.absolutePath,
                null,
                this::class.java.classLoader
            )
            
            // 加载Skill类（约定：包名.SkillImpl）
            val className = dexFile.nameWithoutExtension + ".SkillImpl"
            val skillClass = classLoader.loadClass(className)
            val skill = skillClass.newInstance() as ISkill
            
            skills[skill.id] = skill
            Timber.i("✅ Skill加载成功: ${skill.name} (${skill.id})")
            
            Result.success(skill)
        } catch (e: Exception) {
            Timber.e(e, "❌ Skill加载失败: $skillPath")
            Result.failure(e)
        }
    }
    
    /**
     * 卸载Skill
     */
    fun unloadSkill(skillId: String): Boolean {
        return skills.remove(skillId) != null
    }
    
    /**
     * 获取Skill
     */
    fun getSkill(skillId: String): ISkill? {
        return skills[skillId]
    }
    
    /**
     * 获取所有Skill
     */
    fun getAllSkills(): List<ISkill> {
        return skills.values.toList()
    }
    
    /**
     * 扫描并加载所有Skill
     */
    suspend fun loadAllSkills() {
        skillDir.listFiles()?.filter { it.extension == "dex" || it.extension == "jar" }?.forEach {
            loadSkill(it.absolutePath)
        }
    }
}