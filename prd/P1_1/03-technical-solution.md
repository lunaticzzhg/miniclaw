# P1_1 技术方案

## 文档目标
基于 P1_1 产品需求与交互设计，给出 Android 侧可落地的技术实现方案，覆盖模型 Provider 抽象、MiniMax 试点接入、配置管理、真实流式回复、异常处理与测试方案。

## 设计输入
- 产品需求：`prd/P1_1/01-real-model-integration-and-provider-pluggability.md`
- 交互设计：`prd/P1_1/02-interaction-design.md`
- P1 技术方案：`prd/P1/02-technical-solution.md`
- 开发规范：`rules/android-0-1-development-rules.md`

## 方案目标
- 在 P1 会话与聊天基础上跑通真实模型链路
- 以 MiniMax 作为首个真实模型 Provider 完成试点接入
- 建立统一 Provider 抽象，避免供应商协议渗透到业务层和页面层
- 保持 P1 已有聊天状态机、消息持久化、失败重试与停止回复能力可复用
- 为后续 P2 工具调用和多 Provider 扩展预留稳定边界

## 当前项目上下文
- P1 已定义会话、消息、状态机、本地持久化与聊天主链路
- P1_1 不重新设计聊天容器，而是在现有链路中把假回复替换为真实模型响应
- 当前阶段只要求 1 个真实 Provider 可用，但架构必须允许继续接入第二个 Provider

## 总体实现策略
- 保留 P1 的 `Compose + Navigation + Kotlin + MVI + Repository + Room + Flow` 主架构
- 在 `data.remote` 下新增模型 Provider 层，专门负责不同供应商的请求构造、鉴权、响应解析和流式协议适配
- 在 `domain` 层建立与供应商无关的模型能力接口，例如“发送聊天请求”“校验 Provider 配置”“切换默认 Provider”
- 在 `data.local` 新增最小配置持久化能力，用于保存当前默认 Provider 与其必要配置
- 先跑通 MiniMax 文本对话主链路，再抽象第二个 MockProvider 或占位实现验证可插拔边界

## 推荐依赖基线
- UI：Jetpack Compose、Material 3、Navigation Compose
- 异步：Kotlin Coroutines、Flow
- 生命周期：Lifecycle Runtime Compose、ViewModel
- DI：Koin
- 本地存储：Room 或 DataStore
- 网络：OkHttp + Retrofit
- 序列化：Kotlinx Serialization
- 安全存储：EncryptedSharedPreferences 或更轻量的本地封装
- 测试：JUnit、Turbine、MockK、AndroidX Test、Compose UI Test

说明：
- 模型配置不建议直接存入 Room 消息表
- 若配置结构简单，优先使用 DataStore 或安全存储封装

## 包结构方案
在 `:app` 内建议在 P1 基础上补充如下结构：

```text
app/src/main/java/com/lunatic/miniclaw/
  feature/
    modelconfig/
      ui/
      presentation/
  domain/
    model/
      model/
      repository/
      usecase/
    chat/
      repository/
      usecase/
  data/
    local/
      datastore/
      secure/
    remote/
      model/
        api/
        dto/
        provider/
        minimax/
        mapper/
    repository/
  core/
    config/
    network/
    result/
```

## 分层职责
### View
- 聊天页负责展示当前模型状态、模型切换入口、消息流与失败状态
- 模型配置页负责展示 Provider 选择、参数输入、测试连接与保存结果
- 不直接感知 MiniMax 或其他供应商的协议细节

### ViewModel
- 消费聊天、模型配置和模型状态相关 Intent
- 编排发送消息、切换 Provider、测试连接、保存配置等 use case
- 将 Provider 能力状态映射为页面可展示的文案和状态

### Repository
- `ChatRepository` 继续负责会话、本地消息和远端回复的聚合
- `ModelProviderRepository` 负责默认 Provider、配置读取、Provider 可用性校验和切换
- Repository 对上暴露统一领域模型，不暴露 MiniMax DTO

### RemoteDataSource / Provider
- 每个 Provider 自己处理 baseUrl、header、鉴权、请求体和响应解析
- 上层只依赖统一的 `ModelRemoteDataSource` 或 `ChatModelProvider` 接口

## 核心抽象设计
### 一、Provider 标识
```kotlin
enum class ModelProviderId {
    MINIMAX
}
```

说明：
- P1_1 先只落地 `MINIMAX`
- 架构允许未来继续增加 `OPENAI`、`OPENROUTER`、`CUSTOM_GATEWAY` 等实现

### 二、Provider 配置模型
```kotlin
data class ModelProviderConfig(
    val providerId: ModelProviderId,
    val apiKey: String,
    val baseUrl: String? = null,
    val modelName: String,
    val isConfigured: Boolean
)
```

说明：
- 字段语义以产品能力为主，不按单个供应商原样命名
- 若 MiniMax 还需要 groupId、endpointPath 等字段，可在 Provider 专属配置层扩展

### 三、运行时模型状态
```kotlin
sealed interface ModelAvailability {
    data object Available : ModelAvailability
    data object NotConfigured : ModelAvailability
    data object Validating : ModelAvailability
    data object AuthFailed : ModelAvailability
    data object NetworkUnavailable : ModelAvailability
    data object ServiceUnavailable : ModelAvailability
}
```

### 四、统一聊天请求
```kotlin
data class ChatModelRequest(
    val sessionId: String,
    val requestId: String,
    val messages: List<ChatHistoryMessage>,
    val stream: Boolean = true
)
```

### 五、统一流式事件
```kotlin
sealed interface ChatStreamEvent {
    data object Started : ChatStreamEvent
    data class Delta(val textChunk: String) : ChatStreamEvent
    data object Completed : ChatStreamEvent
    data class Failed(val error: ModelCallError) : ChatStreamEvent
}
```

### 六、统一错误模型
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

## Repository 抽象建议
### 聊天仓储
```kotlin
interface ChatRepository {
    suspend fun sendUserMessage(sessionId: String, text: String): SendMessageResult
    fun observeMessages(sessionId: String): Flow<List<ChatMessage>>
    fun observeSessions(): Flow<List<ChatSession>>
    suspend fun retryUserMessage(messageId: String)
    suspend fun retryAssistantMessage(messageId: String)
    suspend fun stopStreaming(requestId: String)
}
```

### 模型 Provider 仓储
```kotlin
interface ModelProviderRepository {
    fun observeCurrentProvider(): Flow<ModelProviderConfig?>
    fun observeAvailability(): Flow<ModelAvailability>
    suspend fun getCurrentProvider(): ModelProviderConfig?
    suspend fun saveProviderConfig(config: ModelProviderConfig)
    suspend fun switchProvider(providerId: ModelProviderId)
    suspend fun validateCurrentProvider(): ModelAvailability
}
```

### 远端 Provider 抽象
```kotlin
interface ChatModelProvider {
    val providerId: ModelProviderId

    suspend fun validate(config: ModelProviderConfig): ModelAvailability

    fun streamChat(
        config: ModelProviderConfig,
        request: ChatModelRequest
    ): Flow<ChatStreamEvent>
}
```

说明：
- `ChatRepository` 不直接知道具体是 MiniMax 还是其他供应商
- `ModelProviderRepository` 负责拿到当前 Provider 配置并提供给 `ChatRepository`
- 每个具体 Provider 只实现自己的协议适配

## MiniMax 试点接入方案
### 目标
- 跑通 1 条真实文本对话流
- 验证鉴权、超时、流式解析、取消请求和失败重试
- 不让 MiniMax 特有字段污染聊天业务层

### 推荐实现
- 新建 `MiniMaxChatModelProvider`
- 新建 `MiniMaxApiService`
- 新建 `MiniMaxRequestDto / MiniMaxResponseChunkDto`
- 新建 `MiniMaxStreamMapper`

### 适配原则
- MiniMax 请求参数在 provider 层内构造
- 响应流在 provider 层内解析为统一 `ChatStreamEvent`
- Provider 层负责把 HTTP 错误、鉴权错误、协议异常映射为统一 `ModelCallError`

## 配置管理方案
### 目标
- 支持保存当前默认 Provider
- 支持保存 MiniMax 试点所需最小配置
- 支持冷启动后恢复当前配置状态

### 推荐存储
- 默认 Provider Id：DataStore
- 非敏感配置项：DataStore
- 敏感配置项如 `apiKey`：优先安全存储封装

### 持久化对象
```kotlin
data class StoredProviderConfig(
    val providerId: String,
    val modelName: String,
    val baseUrl: String?,
    val hasApiKey: Boolean
)
```

说明：
- 是否真的把完整密钥明文落本地，需要在实现时做安全权衡
- 从 PRD/方案层面，至少要有“可用配置可恢复”的能力

## 聊天主链路调整
### P1 到 P1_1 的主要变化
- P1 中可能使用假数据或简化模拟流
- P1_1 需要将 assistant 回复来源切换为真实 Provider
- 消息状态机和本地持久化策略保持不变，重点替换远端回复实现

### 真实发送链路
1. ViewModel 收到 `SendClicked`
2. `ChatRepository` 先写本地用户消息 `Sending`
3. 查询当前 Provider 配置与可用性
4. 若未配置，用户消息进入失败态或 assistant 占位消息进入失败态，并返回 `NotConfigured`
5. 若配置有效，插入 assistant 占位消息 `Thinking`
6. 调用当前 `ChatModelProvider.streamChat(...)`
7. 收到 `Started` 后保持 `Thinking` 或切入首包等待态
8. 收到首个 `Delta` 后 assistant 状态改为 `Streaming`
9. 按块追加内容
10. 收到 `Completed` 后状态改为 `Completed`
11. 若取消则改为 `Stopped`
12. 若异常则映射为 `Failed`

### 停止回复
- 继续沿用 P1 的 `requestId -> Job` 映射
- Provider 流请求必须可取消
- 用户点击停止后，取消流式请求并保留已生成内容

## 页面与导航方案
### 路由
- `session_list`
- `chat/{sessionId}`
- `model_config?returnToSessionId={sessionId}`

### 导航原则
- 会话列表页不承担模型切换入口职责
- 聊天页顶部栏右侧提供唯一模型切换入口
- 模型配置完成后优先返回原聊天会话
- 若从聊天发送动作触发配置页，则返回后尽量保留当前输入内容

## 页面状态设计
### 聊天页补充状态
```kotlin
data class ChatUiState(
    val sessionId: String = "",
    val title: String = "",
    val messages: List<ChatMessageItemUiModel> = emptyList(),
    val inputText: String = "",
    val canSend: Boolean = false,
    val canStop: Boolean = false,
    val showJumpToBottom: Boolean = false,
    val currentProviderLabel: String = "",
    val modelAvailability: ModelAvailabilityUi = ModelAvailabilityUi.Unknown
)
```

### 模型配置页状态
```kotlin
data class ModelConfigUiState(
    val selectedProvider: ModelProviderId = ModelProviderId.MINIMAX,
    val modelName: String = "",
    val apiKeyInput: String = "",
    val baseUrlInput: String = "",
    val isSaving: Boolean = false,
    val isValidating: Boolean = false,
    val availability: ModelAvailabilityUi = ModelAvailabilityUi.Unknown,
    val canSave: Boolean = false
)
```

### 聊天页 Intent 补充
- `ModelSwitcherClicked`
- `RetryAfterConfigClicked`

### 模型配置页 Intent
- `ScreenStarted`
- `ProviderSelected(providerId)`
- `ApiKeyChanged(value)`
- `ModelNameChanged(value)`
- `BaseUrlChanged(value)`
- `SaveClicked`
- `ValidateClicked`
- `BackToChatClicked`

### Effect
- `NavigateToModelConfig(sessionId)`
- `NavigateBackToChat(sessionId)`
- `ShowToast(message)`

## 可用性校验策略
### 目标
- 区分“未配置”“配置错误”“网络问题”“服务问题”
- 避免用户一发送消息就只看到笼统失败

### 触发时机
- 模型配置页点击“保存并测试”时
- 聊天页点击模型切换入口进入配置后
- 聊天发送前进行轻量校验

### 建议策略
- 发送前只做最小必要校验，例如是否存在当前 Provider 和必要字段
- 真正的联网有效性校验放在“保存并测试”或真实请求时完成
- 避免每次进入聊天页都自动做重型探活请求

## 错误处理方案
### 错误分类
- 未配置 Provider
- 配置缺失
- 鉴权失败
- 网络不可用
- 请求超时
- 服务端错误
- 流式协议解析失败

### 表现策略
- 配置类问题优先引导进入模型配置页
- 请求类问题优先映射到消息局部失败态
- 页面级致命错误仍只用于会话不存在、关键数据损坏等无法展示场景

### 映射原则
- HTTP 401/403 映射为 `AuthFailed`
- 网络断开映射为 `NetworkUnavailable`
- 超时映射为 `RequestTimeout`
- 5xx 或服务拥塞映射为 `ServiceUnavailable`
- 未识别异常映射为 `Unknown`

## 并发与协程策略
- 同一会话同一时刻仍只允许一个进行中的 assistant 流式任务
- `ChatRepository` 继续维护 `requestId -> Job`
- `ModelProviderRepository` 的配置读写在 `IO Dispatcher`
- UI 状态更新在 `Main Dispatcher`
- 切换 Provider 时，不主动中断已有已完成消息，但应避免与进行中的请求并发修改配置

## 可插拔验证策略
### 最低要求
- 不能只写死 `MiniMaxApiService` 然后在业务层直接调用
- 至少要存在统一 `ChatModelProvider` 接口
- DI 中通过 `providerId -> provider implementation` 的方式注册

### 建议验证方式
- 除 `MiniMaxChatModelProvider` 外，再提供一个 `FakeChatModelProvider` 或测试实现
- 在不改聊天页和 `ChatRepository` 调用方式的前提下，可切换为测试实现
- 以此验证“可插拔”不是停留在文档层面

## 数据迁移建议
### 是否需要修改现有消息表
- P1_1 原则上不强制修改 `sessions` 与 `messages` 主表结构
- Provider 配置与可用性属于系统配置，不建议混入消息表

### 可选新增数据
- 若聊天页需要展示消息当时的 Provider，可后续再考虑为消息增加 `provider_id`
- P1_1 为控制复杂度，可先不持久化到消息级别

## 从 P1 到 P1_1 的实施拆分
### 阶段 1：Provider 基础抽象
- 定义 `ModelProviderId`
- 定义 `ModelProviderConfig`
- 定义 `ChatModelProvider`
- 建立 `ModelProviderRepository`

### 阶段 2：MiniMax 试点接入
- 实现 MiniMax API Service
- 实现请求/响应 DTO
- 实现流式协议解析
- 完成统一错误映射

### 阶段 3：配置页与配置持久化
- 搭建模型配置页或配置面板
- 完成当前 Provider 读取、保存、校验
- 打通从聊天页进入配置页和返回链路

### 阶段 4：聊天链路切换为真实模型
- 将 `ChatRepository` 接到 Provider 层
- 打通发送、流式、停止、失败重试
- 校验消息状态机在真实模型下成立

### 阶段 5：测试与验收
- 完成单元测试
- 完成仓储集成测试
- 完成配置页与聊天页 UI 测试

## 测试方案
### 单元测试
- `ModelProviderRepository` 配置读写测试
- `MiniMaxStreamMapper` 解析测试
- `ChatRepository` 在不同 `ModelAvailability` 下的状态流转测试
- 模型配置页 ViewModel reducer 测试

### 集成测试
- 使用 MockWebServer 模拟 MiniMax 流式返回
- 验证 `Thinking -> Streaming -> Completed`
- 验证鉴权失败映射
- 验证停止回复后的取消行为

### UI 测试
- 聊天页顶部栏模型切换入口展示正确
- 未配置时点击发送可进入配置引导
- 配置成功后返回聊天页并可继续发送
- 配置失败时错误文案可见

## 对 P2 的预留边界
- P2 的工具调用不应直接耦合 MiniMax Provider 实现
- 若后续接入支持 function calling 的 Provider，可在 `ChatModelProvider` 之上扩展新的能力接口
- P1_1 先把“纯文本真实模型流”抽象干净，P2 再叠加工具消息和 Agent loop

## 风险与注意事项
- MiniMax 流式协议可能与预期不完全一致，必须以统一事件模型隔离
- 配置存储如果处理不当，容易带来安全与调试体验冲突
- 若发送前校验过重，聊天页会因为频繁探活而变慢
- 若 Provider 抽象过度设计，P1_1 会失去试点阶段应有的推进速度

