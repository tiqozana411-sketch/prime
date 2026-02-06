package com.prime.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class MemoryStorage(context: Context) : SQLiteOpenHelper(
    context, "prime_memory.db", null, 1
) {
    
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE memories (
                id TEXT PRIMARY KEY,
                content TEXT NOT NULL,
                keywords TEXT,
                emotion TEXT,
                importance REAL,
                timestamp INTEGER,
                clarity REAL,
                access_count INTEGER,
                last_access INTEGER,
                consolidation_level INTEGER
            )
        """.trimIndent())
        
        db.execSQL("CREATE INDEX idx_keywords ON memories(keywords)")
        db.execSQL("CREATE INDEX idx_emotion ON memories(emotion)")
    }
    
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        // 永不删除数据
    }
    
    fun save(memory: Memory) {
        writableDatabase.insertWithOnConflict(
            "memories", null,
            ContentValues().apply {
                put("id", memory.id)
                put("content", memory.content)
                put("keywords", memory.keywords.joinToString(","))
                put("emotion", memory.emotion.name)
                put("importance", memory.importance)
                put("timestamp", memory.timestamp)
                put("clarity", memory.clarity)
                put("access_count", memory.accessCount)
                put("last_access", memory.lastAccess)
                put("consolidation_level", memory.consolidationLevel)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }
    
    fun search(query: String): List<Memory> {
        val keywords = query.split(Regex("[\\s，。！？]")).filter { it.isNotBlank() }
        if (keywords.isEmpty()) return emptyList()
        
        val where = keywords.joinToString(" OR ") { "keywords LIKE ?" }
        val args = keywords.map { "%$it%" }.toTypedArray()
        
        return queryMemories(where, args)
    }
    
    fun searchByEmotion(emotion: Emotion): List<Memory> {
        return queryMemories("emotion = ?", arrayOf(emotion.name))
    }
    
    private fun queryMemories(where: String, args: Array<String>): List<Memory> {
        val cursor = readableDatabase.query(
            "memories", null, where, args, null, null, "importance DESC", "50"
        )
        
        val results = mutableListOf<Memory>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(Memory(
                    id = it.getString(0),
                    content = it.getString(1),
                    keywords = it.getString(2).split(","),
                    emotion = Emotion.valueOf(it.getString(3)),
                    importance = it.getFloat(4),
                    timestamp = it.getLong(5),
                    clarity = it.getFloat(6),
                    accessCount = it.getInt(7),
                    lastAccess = it.getLong(8),
                    consolidationLevel = it.getInt(9)
                ))
            }
        }
        return results
    }
}