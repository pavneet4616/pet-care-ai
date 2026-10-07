package com.petcare.companionlite.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Local-first conversation history. The server keeps no permanent chats.

@Entity(
    tableName = "chat_sessions",
    foreignKeys = [
        ForeignKey(
            entity = PetEntity::class,
            parentColumns = ["id"],
            childColumns = ["petId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["petId", "updatedAt"]),
    ],
)
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val petId: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val language: String? = null,
    val archived: Boolean = false,
    val lastMessagePreview: String? = null,
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sessionId", "createdAt"]),
    ],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String, // "user" | "assistant"
    val content: String,
    val createdAt: Long,
    val requestId: String? = null,
    val isSaved: Boolean = false,
    // reportState: null | "helpful" | "reported:unsafe" | ...
    val reportState: String? = null,
    /** Validated structured answer JSON (assistant messages only). Never raw model output. */
    val structuredPayload: String? = null,
    /** SENDING | SENT | FAILED */
    val status: String = "SENT",
    /** HELPFUL | NOT_HELPFUL | REPORTED | null */
    val feedback: String? = null,
    val errorCode: String? = null,
)

@Entity(
    tableName = "chat_drafts",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChatDraftEntity(
    @PrimaryKey val sessionId: Long,
    val text: String,
    val updatedAt: Long,
)

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(
    val id: Long,
    val sessionId: Long,
    val role: ChatRole,
    val content: String,
    val createdAt: Long,
    val requestId: String?,
    val isSaved: Boolean,
    val reportState: String?,
    val structuredJson: String? = null,
    val status: String = "SENT",
    val feedback: String? = null,
    val errorCode: String? = null,
)

data class ChatSession(
    val id: Long,
    val petId: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val language: String?,
    val lastMessagePreview: String? = null,
)

fun ChatMessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id,
    sessionId = sessionId,
    role = if (role == "assistant") ChatRole.ASSISTANT else ChatRole.USER,
    content = content,
    createdAt = createdAt,
    requestId = requestId,
    isSaved = isSaved,
    reportState = reportState,
    structuredJson = structuredPayload,
    status = status,
    feedback = feedback,
    errorCode = errorCode,
)

fun ChatSessionEntity.toDomain(): ChatSession = ChatSession(
    id = id,
    petId = petId,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    language = language,
    lastMessagePreview = lastMessagePreview,
)
