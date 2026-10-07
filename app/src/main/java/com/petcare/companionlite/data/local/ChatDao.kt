package com.petcare.companionlite.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.petcare.companionlite.data.model.ChatDraftEntity
import com.petcare.companionlite.data.model.ChatMessageEntity
import com.petcare.companionlite.data.model.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions WHERE petId = :petId AND archived = 0 ORDER BY updatedAt DESC")
    fun observeForPet(petId: Long): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getById(id: Long): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions WHERE petId = :petId AND archived = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestForPet(petId: Long): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions WHERE archived = 0 ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ChatSessionEntity>>

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ChatSessionEntity): Long

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt, title = CASE WHEN title = '' THEN :fallbackTitle ELSE title END WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long, fallbackTitle: String)

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt, lastMessagePreview = :preview WHERE id = :id")
    suspend fun setPreview(id: Long, updatedAt: Long, preview: String)

    @Query("UPDATE chat_sessions SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE chat_sessions SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC LIMIT :limit")
    fun observeForSession(sessionId: Long, limit: Int = 60): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(sessionId: Long, limit: Int = 10): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: Long, content: String)

    @Query("UPDATE chat_messages SET status = :status, content = :text, structuredPayload = :payload, errorCode = :errorCode WHERE id = :id")
    suspend fun updateMessageResult(id: Long, status: String, text: String, payload: String?, errorCode: String?)

    @Query("UPDATE chat_messages SET isSaved = :saved WHERE id = :id")
    suspend fun setSaved(id: Long, saved: Boolean)

    @Query("UPDATE chat_messages SET reportState = :state WHERE id = :id")
    suspend fun setReportState(id: Long, state: String)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
}

@Dao
interface ChatDraftDao {
    @Query("SELECT * FROM chat_drafts WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getDraft(sessionId: Long): ChatDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: ChatDraftEntity)

    @Query("DELETE FROM chat_drafts WHERE sessionId = :sessionId")
    suspend fun deleteDraft(sessionId: Long)

    @Query("DELETE FROM chat_drafts")
    suspend fun deleteAll()
}
