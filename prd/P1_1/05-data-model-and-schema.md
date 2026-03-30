# P1_1 数据模型与表结构文档

## 文档目标
定义 P1_1 所需的数据对象、分层模型、字段语义、本地配置存储结构、远端 DTO 与映射关系，作为 Provider 接入、配置持久化、Repository 和 UI Model 设计依据。

## 设计输入
- 产品需求：`prd/P1_1/01-real-model-integration-and-provider-pluggability.md`
- 交互设计：`prd/P1_1/02-interaction-design.md`
- 技术方案：`prd/P1_1/03-technical-solution.md`
- 页面状态机：`prd/P1_1/04-page-state-machine.md`
- P1 数据模型：`prd/P1/04-data-model-and-schema.md`

## 设计原则
- 严格区分 DTO、Local Config Model、Entity、Domain Model、UI Model
- P1 已有 `sessions` / `messages` 继续作为会话与消息真值来源，P1_1 不轻易破坏
- Provider 差异收敛在远端 DTO 和 Provider 专属配置层，不污染聊天业务层
- 模型配置与模型可用性属于系统配置，不直接塞进消息主表
- 保证 P1_1 够用，同时为后续 P2 工具调用和多 Provider 扩展预留空间

## 一、模型分层总览
```text
Remote DTO
-> Provider Mapper
-> Local Config Model / Entity
-> Domain Model
-> UI Model
```

说明：
- Remote DTO 只服务 MiniMax 等供应商协议
- Local Config Model 只服务本地 Provider 配置存储
- Entity 继续服务会话与消息持久化
- Domain Model 只表达业务语义
- UI Model 只表达页面展示与交互

## 二、P1 复用的核心业务对象
### 1. ChatSession
P1_1 继续沿用 P1 的会话模型。

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

### 2. ChatMessage
P1_1 继续沿用 P1 的消息模型。

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

### 3. MessageRole
```kotlin
enum class MessageRole {
    USER,
    ASSISTANT
}
```

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
- P1_1 的重点是把 assistant 回复来源切换为真实模型
- 消息角色与消息状态不因引入真实模型而新增复杂类型

## 三、P1_1 新增核心业务对象
### 1. ModelProviderId
表示当前模型服务提供者。

```kotlin
enum class ModelProviderId {
    MINIMAX
}
```

说明：
- P1_1 先只落地 `MINIMAX`
- 后续可扩展 `OPENAI`、`OPENROUTER`、`CUSTOM_GATEWAY` 等

### 2. ModelProviderConfig
表示当前 Provider 的业务配置。

```kotlin
data class ModelProviderConfig(
    val providerId: ModelProviderId,
    val modelName: String,
    val apiKey: String?,
    val baseUrl: String?,
    val isConfigured: Boolean,
    val updatedAt: Long
)
```

#### 字段说明
- `providerId`：Provider 唯一标识
- `modelName`：默认使用的模型名
- `apiKey`：敏感鉴权字段，读取时可为空或脱敏
- `baseUrl`：自定义网关或兼容服务地址
- `isConfigured`：当前 Provider 是否满足最小配置要求
- `updatedAt`：配置最后更新时间

### 3. ModelAvailability
表示当前 Provider 的运行时可用性。

```kotlin
sealed interface ModelAvailability {
    data object Available : ModelAvailability
    data object NotConfigured : ModelAvailability
    data object Validating : ModelAvailability
    data object AuthFailed : ModelAvailability
    data object NetworkUnavailable : ModelAvailability
    data object ServiceUnavailable : ModelAvailability
    data object Unknown : ModelAvailability
}
```

说明：
- `ModelAvailability` 是运行时状态，不建议作为长期真值完整持久化
- 可选地记录最近一次校验结果摘要，用于冷启动时快速展示

### 4. ChatModelRequest
表示与供应商无关的统一聊天请求。

```kotlin
data class ChatModelRequest(
    val sessionId: String,
    val requestId: String,
    val messages: List<ChatHistoryMessage>,
    val stream: Boolean = true
)
```

### 5. ChatHistoryMessage
表示发往模型服务的历史消息片段。

```kotlin
data class ChatHistoryMessage(
    val role: MessageRole,
    val content: String
)
```

### 6. ChatStreamEvent
表示与供应商无关的流式事件。

```kotlin
sealed interface ChatStreamEvent {
    data object Started : ChatStreamEvent
    data class Delta(val textChunk: String) : ChatStreamEvent
    data object Completed : ChatStreamEvent
    data class Failed(val error: ModelCallError) : ChatStreamEvent
}
```

### 7. ModelCallError
表示统一错误类型。

```kotlin
sealed interface ModelCallError {
    data object NotConfigured : ModelCallError
    data object AuthFailed : ModelCallError
    data object NetworkUnavailable : ModelCallError
    data object RequestTimeout : ModelCallError
    data object ServiceUnavailable : ModelCallError
    data object Unknown : ModelCallError
}
```

## 四、UI 模型
### 1. 聊天页模型入口 UI
```kotlin
data class ModelSwitcherUiModel(
    val providerLabel: String,
    val availabilityText: String?,
    val isEnabled: Boolean
)
```

### 2. 模型配置页 UI
```kotlin
data class ModelConfigUiModel(
    val selectedProvider: ModelProviderId,
    val providerDisplayName: String,
    val modelName: String,
    val apiKeyMasked: String,
    val baseUrl: String,
    val availabilityText: String?,
    val canSave: Boolean,
    val canValidate: Boolean
)
```

### 3. 聊天消息 UI 扩展
P1 的 `ChatMessageItemUiModel` 可继续使用，必要时补充配置动作入口：

```kotlin
data class ChatMessageItemUiModel(
    val id: String,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus,
    val showRetry: Boolean,
    val statusText: String?,
    val showLoadingIndicator: Boolean,
    val showGoToConfig: Boolean = false
)
```

说明：
- `showGoToConfig` 不进入 Domain 层
- 它只用于在模型未配置、鉴权失败等场景下给 UI 一个直接动作

## 五、远端 DTO
P1_1 首期以 MiniMax 为试点，DTO 只在 Provider 层使用。

### 1. MiniMaxChatRequestDto
```kotlin
data class MiniMaxChatRequestDto(
    val model: String,
    val messages: List<MiniMaxMessageDto>,
    val stream: Boolean
)
```

### 2. MiniMaxMessageDto
```kotlin
data class MiniMaxMessageDto(
    val role: String,
    val content: String
)
```

### 3. MiniMaxStreamChunkDto
```kotlin
data class MiniMaxStreamChunkDto(
    val type: String? = null,
    val delta: String? = null,
    val error: MiniMaxErrorDto? = null
)
```

### 4. MiniMaxErrorDto
```kotlin
data class MiniMaxErrorDto(
    val code: String? = null,
    val message: String? = null
)
```

说明：
- DTO 字段命名按 MiniMax 协议调整
- DTO 不要求与 Domain 一致
- 若后续接入其他 Provider，每个 Provider 自己维护一套 DTO

## 六、本地存储设计
### 总体策略
- `sessions` 与 `messages` 继续使用 P1 的 Room 表
- Provider 配置使用独立配置存储，不混入消息主表
- 允许持久化“当前默认 Provider”与其最小必要配置

### 为什么不把 Provider 配置放进 `messages`
- Provider 配置属于系统级设置，不是对话内容
- 与消息表耦合会增加迁移复杂度
- 后续 Provider 切换和配置修复不应污染历史会话数据

## 七、P1 继续沿用的 Room 表
### 1. `sessions`
P1_1 不修改表结构，继续沿用：
- `id`
- `title`
- `last_message_preview`
- `updated_at`
- `created_at`

### 2. `messages`
P1_1 不强制修改表结构，继续沿用：
- `id`
- `session_id`
- `request_id`
- `role`
- `content`
- `status`
- `created_at`
- `updated_at`

说明：
- 这样可以最大化复用 P1 的聊天链路和本地数据
- 真实模型只替换远端来源，不重做消息存储设计

## 八、Provider 配置存储结构
P1_1 推荐使用 DataStore 或本地配置存储，而不是 Room。

### 1. `provider_preferences`
### 建模语义
存储当前默认 Provider 与其基础非敏感配置。

### 字段设计
| 字段名 | 类型 | 非空 | 说明 |
| --- | --- | --- | --- |
| `current_provider_id` | STRING | 否 | 当前默认 Provider |
| `model_name` | STRING | 否 | 当前默认模型名 |
| `base_url` | STRING | 否 | 当前 Provider 的基础地址 |
| `is_configured` | BOOLEAN | 是 | 是否满足最小配置要求 |
| `last_validation_status` | STRING | 否 | 最近一次校验结果 |
| `updated_at` | LONG | 否 | 更新时间 |

### Local Model 示例
```kotlin
data class ProviderPreferences(
    val currentProviderId: String? = null,
    val modelName: String? = null,
    val baseUrl: String? = null,
    val isConfigured: Boolean = false,
    val lastValidationStatus: String? = null,
    val updatedAt: Long? = null
)
```

### 约束
- `current_provider_id` 为空表示尚未完成任何 Provider 配置
- `is_configured = true` 不等于当前联网一定可用，只表示字段层面完整
- `last_validation_status` 只作为辅助展示，不作为请求真值

## 九、敏感配置存储结构
### 1. `provider_secret_store`
### 建模语义
保存 Provider 所需密钥等敏感字段。

### 字段建议
| 字段名 | 类型 | 非空 | 说明 |
| --- | --- | --- | --- |
| `provider_id` | STRING | 是 | Provider 标识 |
| `api_key` | STRING | 否 | 鉴权密钥 |
| `updated_at` | LONG | 否 | 更新时间 |

### Local Model 示例
```kotlin
data class ProviderSecret(
    val providerId: String,
    val apiKey: String?,
    val updatedAt: Long?
)
```

说明：
- 实际实现可用 EncryptedSharedPreferences、Keystore 封装等方式
- 从模型设计角度，它与非敏感配置应逻辑分离

## 十、字段约束与扩展位
### 1. `requestId` 继续沿用
- 用户点击发送时生成 `requestId`
- 同一轮用户消息与 assistant 消息仍共享同一个 `requestId`
- Provider 不直接影响 `requestId` 设计

### 2. 是否需要消息级 `provider_id`
P1_1 推荐先不增加。

原因：
- 当前默认只需保证“当前 Provider 可用”
- 对消息级回溯 Provider 不是 P1_1 核心目标
- 可以把复杂度留给后续多 Provider 深度切换阶段

### 3. 预留扩展位
后续如有需要，可新增：
- `provider_id`
- `provider_model_name`
- `provider_request_trace_id`
- `tool_call_id`
- `tool_name`
- `tool_status`

## 十一、建议 Repository 维护的映射
### 1. Provider 配置聚合
`ModelProviderConfig` 由两部分聚合得到：
- 非敏感配置：`provider_preferences`
- 敏感配置：`provider_secret_store`

### 2. 可用性聚合
`ModelAvailability` 推荐由以下信息综合推导：
- 当前是否存在 `current_provider_id`
- 必填配置是否完整
- 最近一次校验结果
- 当前真实请求结果

说明：
- `ModelAvailability` 不建议简单等同于某个单独字段

## 十二、Mapper 设计
### Local -> Domain
- `ProviderPreferencesMapper`
- `ProviderSecretMapper`
- `ModelProviderConfigMapper`

### Domain -> UI
- `ModelSwitcherUiMapper`
- `ModelConfigUiMapper`
- `ChatMessageUiModelMapper`

### DTO -> Domain Event
- `MiniMaxStreamChunkMapper`
- `MiniMaxErrorMapper`

### Mapper 职责
- 统一 Provider 状态文案
- 统一错误类型映射
- 统一脱敏逻辑，例如 API Key 显示为掩码
- 禁止 ViewModel 手写散落转换逻辑

## 十三、时间字段与排序
### 时间统一规范
- 全部使用毫秒时间戳 `Long`
- 不在配置存储里直接存展示文案
- 展示文案在 UI 层格式化

### 排序规则
- 会话列表：`updatedAt DESC`
- 消息列表：`createdAt ASC`
- Provider 配置不涉及列表排序，但需要记录 `updatedAt`

## 十四、配置保存与恢复需要的数据条件
### 保存 Provider 配置
需要保留：
- `providerId`
- `modelName`
- `baseUrl`
- `apiKey`
- `updatedAt`

### 配置校验
需要保留：
- 当前 Provider 配置快照
- 最近一次校验结果
- 最近一次校验时间

P1_1 推荐：
- “字段完整性”与“联网有效性”分开建模
- `isConfigured` 只表示字段足够发起请求
- 联网是否成功由 `ModelAvailability` 表达

## 十五、与 P1 表结构关系
### 不变部分
- `sessions` 表保持不变
- `messages` 表保持不变
- `requestId` 关联逻辑保持不变

### 新增部分
- 新增 Provider 配置存储
- 新增敏感配置存储
- 新增 Provider 相关 Domain / UI Model

### 迁移影响
- 如果 P1 已落地数据库，P1_1 不需要因 Provider 接入修改主表 migration
- 只需增加配置存储初始化逻辑

## 十六、向后扩展建议
### 面向 P2
可在当前模型之上继续新增：
- `tool_call_id`
- `tool_name`
- `tool_status`
- `message_type`

### 面向多 Provider
可继续新增：
- `provider_priority`
- `provider_enabled`
- `fallback_provider_id`
- `capabilities`

### 面向调试与观测
可继续新增：
- `last_request_trace_id`
- `last_error_code`
- `last_error_message`

