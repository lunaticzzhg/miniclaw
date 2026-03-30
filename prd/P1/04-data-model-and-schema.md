# P1 数据模型与表结构文档

## 文档目标
定义 P1 所需的数据对象、分层模型、字段语义、表结构、索引、约束与映射关系，作为 Room 实体、DAO、Repository 和 UI Model 设计依据。

## 设计输入
- 产品需求：`prd/01-chat-session-foundation.md`
- 交互设计：`prd/P1/01-interaction-design.md`
- 技术方案：`prd/P1/02-technical-solution.md`
- 页面状态机：`prd/P1/03-page-state-machine.md`

## 设计原则
- 严格区分 DTO、Entity、Domain Model、UI Model
- 字段命名以业务语义为主，不为当前接口耦合命名
- 以本地数据库作为会话与消息真值来源
- 保证 P1 够用，同时为 P2 工具调用和 P3 记忆管理预留扩展位

## 一、模型分层总览
```text
Remote DTO
-> Data Mapper
-> Entity
-> Domain Model
-> UI Model
```

说明：
- Remote DTO 只服务远端协议
- Entity 只服务本地持久化
- Domain Model 只表达业务语义
- UI Model 只表达页面展示与交互

## 二、核心业务对象
### 1. ChatSession
表示一个会话容器。

#### Domain Model
```kotlin
data class ChatSession(
    val id: String,
    val title: String,
    val lastMessagePreview: String?,
    val updatedAt: Long,
    val createdAt: Long,
    val hasStreamingMessage: Boolean
)
```

#### 字段说明
- `id`：会话唯一标识
- `title`：会话标题，P1 默认为“新会话”或首条消息摘要
- `lastMessagePreview`：列表摘要文案
- `updatedAt`：最后更新时间，用于会话列表排序
- `createdAt`：创建时间
- `hasStreamingMessage`：该会话是否存在进行中的 assistant 回复

### 2. ChatMessage
表示会话中的一条消息。

#### Domain Model
```kotlin
data class ChatMessage(
    val id: String,
    val sessionId: String,
    val requestId: String?,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus,
    val createdAt: Long,
    val updatedAt: Long
)
```

#### 字段说明
- `id`：消息唯一标识
- `sessionId`：所属会话
- `requestId`：一次请求链路标识，用于用户消息与 assistant 回复关联
- `role`：消息角色
- `content`：消息内容
- `status`：消息当前状态
- `createdAt`：消息创建时间
- `updatedAt`：消息最近更新时间

### 3. MessageRole
```kotlin
enum class MessageRole {
    USER,
    ASSISTANT
}
```

P1 只保留两类，避免模型过早复杂化。

### 4. MessageStatus
```kotlin
enum class MessageStatus {
    SENDING,
    SENT,
    SEND_FAILED,
    THINKING,
    STREAMING,
    COMPLETED,
    FAILED,
    STOPPED
}
```

说明：
- 用户消息只会使用 `SENDING / SENT / SEND_FAILED`
- assistant 消息只会使用 `THINKING / STREAMING / COMPLETED / FAILED / STOPPED`

## 三、UI 模型
### 1. SessionItemUiModel
```kotlin
data class SessionItemUiModel(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAtText: String,
    val hasStreamingMessage: Boolean
)
```

### 2. ChatMessageItemUiModel
```kotlin
data class ChatMessageItemUiModel(
    val id: String,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus,
    val showRetry: Boolean,
    val statusText: String?,
    val showLoadingIndicator: Boolean
)
```

说明：
- `statusText` 由 UI mapper 负责转换，例如“发送中”“思考中”
- `showRetry` 不在 Domain 中存储，由 `status` 推导

## 四、远端 DTO
P1 的远端协议可能后续切换，因此 DTO 不应污染业务层。

### 1. ChatRequestDto
```kotlin
data class ChatRequestDto(
    val sessionId: String,
    val messages: List<ChatHistoryMessageDto>,
    val stream: Boolean
)
```

### 2. ChatHistoryMessageDto
```kotlin
data class ChatHistoryMessageDto(
    val role: String,
    val content: String
)
```

### 3. ChatStreamChunkDto
```kotlin
data class ChatStreamChunkDto(
    val type: String,
    val delta: String? = null,
    val error: String? = null
)
```

说明：
- DTO 字段命名按接口协议决定
- 不要求与 Entity 或 Domain 一致

## 五、本地数据库表设计
P1 采用 Room，至少包含 `sessions` 和 `messages` 两张表。

## 六、表一：`sessions`
### 建表语义
存储会话基础信息，用于会话列表、排序与恢复。

### 字段设计
| 字段名 | 类型 | 非空 | 说明 |
| --- | --- | --- | --- |
| `id` | TEXT | 是 | 会话主键 |
| `title` | TEXT | 是 | 会话标题 |
| `last_message_preview` | TEXT | 否 | 最后一条消息摘要 |
| `updated_at` | INTEGER | 是 | 最后更新时间，毫秒时间戳 |
| `created_at` | INTEGER | 是 | 创建时间，毫秒时间戳 |

### Room Entity 示例
```kotlin
@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["updated_at"])
    ]
)
data class SessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "last_message_preview")
    val lastMessagePreview: String?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
```

### 约束
- `title` 不允许为空字符串
- `updated_at >= created_at`
- 默认按 `updated_at DESC` 查询

## 七、表二：`messages`
### 建表语义
存储单条消息及其流式状态。

### 字段设计
| 字段名 | 类型 | 非空 | 说明 |
| --- | --- | --- | --- |
| `id` | TEXT | 是 | 消息主键 |
| `session_id` | TEXT | 是 | 所属会话 |
| `request_id` | TEXT | 否 | 一次请求链路标识 |
| `role` | TEXT | 是 | `USER` 或 `ASSISTANT` |
| `content` | TEXT | 是 | 消息内容 |
| `status` | TEXT | 是 | 消息状态 |
| `created_at` | INTEGER | 是 | 创建时间 |
| `updated_at` | INTEGER | 是 | 更新时间 |

### Room Entity 示例
```kotlin
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id", "created_at"]),
        Index(value = ["request_id"]),
        Index(value = ["session_id", "updated_at"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "request_id")
    val requestId: String?,
    @ColumnInfo(name = "role")
    val role: String,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
```

### 约束
- `session_id` 必须存在于 `sessions.id`
- `role` 仅允许 `USER`、`ASSISTANT`
- `status` 必须符合角色约束
- `updated_at >= created_at`
- 会话内消息默认按 `created_at ASC` 查询

## 八、角色与状态约束矩阵
| role | allowed status |
| --- | --- |
| `USER` | `SENDING` `SENT` `SEND_FAILED` |
| `ASSISTANT` | `THINKING` `STREAMING` `COMPLETED` `FAILED` `STOPPED` |

说明：
- 该约束可先通过业务层保证
- 若后续有需要，可在 migration 或本地校验层加强

## 九、`requestId` 设计
### 设计目标
- 把一次“用户发送”与“对应 assistant 回复”关联起来
- 支持停止流式任务
- 支持 assistant 失败后原位重试

### 使用规则
- 用户点击发送时生成 `requestId`
- 该轮用户消息写入同一个 `requestId`
- 紧随其后的 assistant 占位消息也写入同一个 `requestId`
- 通过 `requestId` 找到进行中的流任务和对应消息

### P1 约束
- 一个 `requestId` 最多关联一条用户消息和一条 assistant 消息
- 一个会话同一时刻最多只有一个正在 `THINKING/STREAMING` 的 `requestId`

## 十、建议 DAO
### SessionDao
```kotlin
@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY updated_at DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getById(sessionId: String): SessionEntity?

    @Query(
        "UPDATE sessions SET title = :title, last_message_preview = :preview, updated_at = :updatedAt WHERE id = :sessionId"
    )
    suspend fun updateSummary(
        sessionId: String,
        title: String,
        preview: String?,
        updatedAt: Long
    )
}
```

### MessageDao
```kotlin
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY created_at ASC")
    fun observeBySession(sessionId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Update
    suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE request_id = :requestId ORDER BY created_at ASC")
    suspend fun getByRequestId(requestId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY created_at ASC")
    suspend fun listBySession(sessionId: String): List<MessageEntity>
}
```

## 十一、Repository 需要维护的映射
### Session 聚合字段
`hasStreamingMessage` 不是 `sessions` 表的原始字段，P1 推荐通过消息表聚合得出：
- 某会话存在 `ASSISTANT + THINKING`
- 或某会话存在 `ASSISTANT + STREAMING`

### `lastMessagePreview` 更新规则
- 用户消息发送后可立即更新为该用户消息摘要
- assistant 流式完成后再更新为 assistant 最终内容摘要
- 若 assistant 失败，仍可保留当前已有内容摘要

说明：
- P1 为简化查询性能，可以直接把 `last_message_preview` 持久化在 `sessions`
- `hasStreamingMessage` 则建议运行时聚合，不必先持久化

## 十二、Mapper 设计
### Entity -> Domain
- `SessionEntityMapper`
- `MessageEntityMapper`

### Domain -> UI
- `SessionUiModelMapper`
- `ChatMessageUiModelMapper`

### DTO -> Domain Event
- `ChatStreamChunkMapper`

### Mapper 职责
- 统一状态文案和显示逻辑
- 统一异常字段兜底
- 禁止 ViewModel 手写散落转换逻辑

## 十三、时间字段与排序
### 时间统一规范
- 全部使用毫秒时间戳 `Long`
- 不在数据库里存格式化文案
- 展示文案在 UI 层格式化

### 排序规则
- 会话列表：`updatedAt DESC`
- 消息列表：`createdAt ASC`

### 更新时间更新规则
- 新建会话时写入 `createdAt` 和 `updatedAt`
- 新消息产生时同步刷新 `sessions.updated_at`
- assistant 流式追加时可只在关键状态点更新，避免过高写频

## 十四、标题与摘要更新规则
### 会话标题
- 初始值：`新会话`
- 首条用户消息成功后：
  - 截取前若干字符作为标题
  - 去除换行和多余空白

### 消息摘要
- 优先取最近一条消息内容
- 若内容为空但处于 `THINKING`：
  - 可显示固定文案，例如“思考中...”
- 摘要长度由 UI 层截断

## 十五、重试与恢复需要的数据条件
### 用户消息重试
需要保留：
- 原消息 `id`
- `sessionId`
- `content`
- 原 `requestId` 或新建 `requestId`

P1 推荐：
- 用户消息重试时生成新的 `requestId`
- 原失败状态覆盖为新的发送状态

### assistant 回复重试
需要保留：
- 对应 assistant 消息 `id`
- 对应用户消息内容
- 所属 `sessionId`

P1 推荐：
- assistant 重试复用当前 assistant 消息 `id`
- 生成新的 `requestId`
- assistant 内容清空或保留，需要统一策略

推荐策略：
- 清空失败的未完成内容后重试，减少歧义

## 十六、P1 不建模内容
- 工具调用消息
- 多模态附件
- 语音片段
- 记忆片段
- 多端同步元数据
- 草稿箱持久化

## 十七、向后扩展建议
### 面向 P2
可新增字段：
- `message_type`
- `tool_call_id`
- `tool_name`
- `tool_status`

### 面向 P3
可新增字段：
- `summary`
- `archived`
- `pinned`
- `last_read_at`

### 面向 P4+
可新增字段：
- `media_uri`
- `duration_ms`
- `device_action_type`

## 十八、落地顺序建议
1. 先定义 `MessageRole`、`MessageStatus`
2. 再实现 `SessionEntity`、`MessageEntity`
3. 然后补 DAO 和 mapper
4. 最后接入 Repository 与页面状态映射
