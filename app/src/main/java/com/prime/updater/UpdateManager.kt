package com.prime.updater

import android.content.Context
import com.prime.core.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.URL

/**
 * 自动更新管理器
 * 负责检测版本、下载APK、静默安装
 */
object UpdateManager {
    
    private const val UPDATE_URL = "https://api.github.com/repos/YOUR_REPO/releases/latest"
    private const val CURRENT_VERSION = "1.0.0"
    
    /**
     * 检测更新
     */
    suspend fun checkUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            Timber.i("🔍 检测更新...")
            
            val url = URL(UPDATE_URL)
            val connection = url.openConnection()
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val response = connection.getInputStream().bufferedReader().readText()
            val json = org.json.JSONObject(response)
            
            val version = json.getString("tag_name").removePrefix("v")
            val assets = json.getJSONArray("assets")
            
            if (assets.length() == 0) {
                Timber.w("⚠️ 没有找到APK文件")
                return@withContext null
            }
            
            val apkAsset = assets.getJSONObject(0)
            val downloadUrl = apkAsset.getString("browser_download_url")
            val size = apkAsset.getLong("size")
            val changelog = json.optString("body", "无更新说明")
            val publishedAt = json.getString("published_at")
            
            Timber.i("✅ 发现版本: $version")
            
            UpdateInfo(
                version = version,
                downloadUrl = downloadUrl,
                changelog = changelog,
                size = size,
                publishedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Timber.e(e, "❌ 检测更新失败")
            null
        }
    }
    
    /**
     * 下载APK
     */
    suspend fun downloadApk(url: String, savePath: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val file = File(savePath)
            file.parentFile?.mkdirs()
            
            URL(url).openStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            Timber.i("✅ APK下载成功: $savePath")
            Result.success(file)
        } catch (e: Exception) {
            Timber.e(e, "❌ APK下载失败")
            Result.failure(e)
        }
    }
    
    /**
     * ROOT静默安装
     */
    suspend fun installApk(apkPath: String): Boolean = withContext(Dispatchers.IO) {
        if (!RootManager.isRootAvailable) {
            Timber.w("ROOT权限不可用，无法静默安装")
            return@withContext false
        }
        
        try {
            val result = RootManager.exec("pm install -r $apkPath")
            if (result.success) {
                Timber.i("✅ APK安装成功")
                true
            } else {
                Timber.e("❌ APK安装失败: ${result.error}")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "APK安装异常")
            false
        }
    }
    
    /**
     * 执行更新流程
     */
    suspend fun performUpdate(context: Context): Boolean {
        val updateInfo = checkUpdate() ?: return false
        
        if (updateInfo.version <= CURRENT_VERSION) {
            Timber.i("已是最新版本")
            return false
        }
        
        Timber.i("发现新版本: ${updateInfo.version}")
        
        val apkPath = "/sdcard/PRIME/update/prime_${updateInfo.version}.apk"
        val downloadResult = downloadApk(updateInfo.downloadUrl, apkPath)
        
        if (downloadResult.isFailure) {
            return false
        }
        
        return installApk(apkPath)
    }
}

/**
 * 更新信息
 */
data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val changelog: String,
    val size: Long,
    val publishedAt: Long
)