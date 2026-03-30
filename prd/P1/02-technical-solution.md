# P1 技术方案

## 文档目标
基于 P1 产品需求与交互设计，给出 Android 侧可落地的技术实现方案，覆盖架构分层、模块拆分、数据模型、核心流程、状态机、持久化、流式回复、异常处理与测试方案。

## 设计输入
- 产品需求：`prd/01-chat-session-foundation.md`
- 交互设计：`prd/P1/01-interaction-design.md`
- 开发规范：`rules/android-0-1-development-rules.md`

## 方案目标
- 在 Android App 内完成 P1 最小聊天闭环
- 满足首次进入可聊天、历史会话可恢复、AI 回复流式展示、失败可重试
- 严格遵循 Compose + Navigation + Kotlin + MVI + Repository 分层
- 为后续 P2-P9 保留可扩展的会话、消息、流式事件与能力接入边界

## 当前项目现状
- 当前工程仅有单一 `:app` 模块
- 尚未接入 Compose、Navigation、Koin、Room、协程等 P1 必需基础设施
- 尚无业务代码、页面、数据层与网络层实现

## 总体实现策略
- P1 先在单 `:app` 模块内完成分层与目录隔离，不急于过早拆成多个 Gradle 模块
- 在 `:app` 内按 `feature / domain / data / core` 分包，保证未来可平滑升级为组件化模块
- 先打通“本地持久化 + 假/真流式回复 + 状态机 + 页面导航”主链路，再补齐异常恢复与测试

## 推荐依赖基线
- UI：Jetpack Compose、Material 3、Navigation Compose
- 异步：Kotlin Coroutines、Flow
- 生命周期：Lifecycle Runtime Compose、ViewModel
- DI：Koin
- 本地存储：Room
- 网络：OkHttp + Retrofit
- 序列化：Kotlinx Serialization 或 Moshi，二选一即可
- 日志：Timber 或保持轻量日志封装
- 测试：JUnit、Turbine、MockK、AndroidX Test、Compose UI Test

## 包结构方案
在 `:app` 内建议采用如下目录结构：

```text
app/src/main/java/com/lunatic/miniclaw/
  app/
    MiniClawApp.kt
    MainActivity.kt
    navigation/
  core/
    common/
    dispatcher/
    model/
    ui/
  feature/
    sessionlist/
      ui/
      presentation/
    chat/
      ui/
      presentation/
  domain/
    session/
      model/
      repository/
      usecase/
    chat/
      model/
      repository/
      usecase/
  data/
    local/
      db/
      dao/
      entity/
    remote/
      api/
      dto/
      datasource/
    repository/
    mapper/
  di/
```

## 分层职责
### View
- 使用 Compose 渲染页面
- 只消费 `UiState`
- 只上抛 `Intent`
- 不直接处理业务逻辑、数据库或网络

### ViewModel
- 接收 `Intent`
- 驱动状态 reducer
- 编排 use case
- 收敛错误、加载态、副作用

### Repository
- 负责聚合本地会话、本地消息、远端 AI 回复流
- 对上屏蔽数据来源差异
- 保持接口稳定，便于后续替换模型服务、接入工具调用

### DataSource
- LocalDataSource：Room 持久化
- RemoteDataSource：AI 聊天接口与流式事件解析

## 核心领域对象
### Session
- `sessionId`
- `title`
- `lastMessagePreview`
- `updatedAt`
- `hasStreamingMessage`

### Message
- `messageId`
- `sessionId`
- `role`
  - `USER`
  - `ASSISTANT`
- `content`
- `status`
- `createdAt`
- `updatedAt`
- `requestId`

### MessageStatus
- 用户消息：
  - `Sending`
  - `Sent`
  - `SendFailed`
- AI 消息：
  - `Thinking`
  - `Streaming`
  - `Completed`
  - `Failed`
  - `Stopped`

### ChatStreamEvent
- `Started`
- `Delta(textChunk)`
- `Completed`
- `Failed(reason)`

说明：
- `requestId` 用于把“一次用户发送”与“对应 AI 回复”关联起来，便于重试与状态恢复
- 不在 P1 引入工具消息、系统消息、语音消息类型，避免模型过重

## 本地存储方案
### 目标
- 支持退出 App 后恢复会话列表与消息记录
- 支持消息状态持久化
- 支持按会话读取消息流

### Room 表设计
#### `sessions`
- `id`
- `title`
- `last_message_preview`
- `updated_at`
- `created_at`

#### `messages`
- `id`
- `session_id`
- `role`
- `content`
- `status`
- `request_id`
- `created_at`
- `updated_at`

### 索引建议
- `sessions.updated_at`
- `messages.session_id + created_at`
- `messages.request_id`

### 持久化原则
- 用户点击发送后，先本地落一条用户消息，再发起网络请求
- AI 回复开始前，先插入一条空内容的 assistant 消息，状态为 `Thinking`
- 流式过程中按块更新同一条 assistant 消息内容与状态
- 完成、失败、停止都更新回同一条消息，避免消息列表抖动与重复插入

## 网络与流式方案
### 目标
- 支持 AI 文本回复流式展示
- 网络异常可定位到具体消息
- 未来可扩展为 OpenAI / OpenRouter / 自建模型网关

### 推荐抽象
定义稳定仓储接口：

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

### RemoteDataSource 抽象
```kotlin
interface ChatRemoteDataSource {
    fun streamChat(request: ChatRequest): Flow<ChatStreamEvent>
}
```

### 流式处理策略
1. ViewModel 发起发送意图
2. Repository 写入用户消息 `Sending`
3. 请求真正发出后，用户消息更新为 `Sent`
4. Repository 插入 assistant 占位消息 `Thinking`
5. 远端返回首个流式事件后，assistant 状态切到 `Streaming`
6. 每个 `Delta` 追加到同一条 assistant 消息
7. 收到完成事件后，assistant 状态置为 `Completed`
8. 如用户点击停止，则取消协程并将状态置为 `Stopped`
9. 如网络异常，则更新 assistant 或 user 对应消息为失败态

## 页面与导航方案
### 路由
- `session_list`
- `chat/{sessionId}`

### 启动逻辑
1. App 启动进入根导航宿主
2. 读取本地是否存在会话
3. 若不存在，则创建默认会话并跳转聊天页
4. 若存在，则进入会话列表页

### 导航原则
- 聊天页只接收 `sessionId`
- 页面不直接持有数据库对象
- 所有页面数据通过 ViewModel 订阅 Flow 转换得到

## 页面状态设计
### 会话列表页状态
```kotlin
data class SessionListUiState(
    val isLoading: Boolean = true,
    val sessions: List<SessionItemUiModel> = emptyList(),
    val isEmpty: Boolean = false
)
```

### 会话列表页 Intent
- `CreateNewSessionClicked`
- `SessionClicked(sessionId)`
- `ScreenStarted`

### 聊天页状态
```kotlin
data class ChatUiState(
    val sessionId: String = "",
    val title: String = "",
    val messages: List<ChatMessageItemUiModel> = emptyList(),
    val inputText: String = "",
    val canSend: Boolean = false,
    val isStreaming: Boolean = false,
    val showJumpToBottom: Boolean = false
)
```

### 聊天页 Intent
- `ScreenStarted`
- `InputChanged(text)`
- `SendClicked`
- `StopClicked`
- `RetryUserMessageClicked(messageId)`
- `RetryAssistantMessageClicked(messageId)`
- `ListScrolled(isAtBottom)`

### 聊天页 Effect
- `NavigateBack`
- `ScrollToBottom`
- `OpenSession(sessionId)`
- `ShowToast(message)`

说明：
- 页面显式区分 `State` 与 `Effect`，避免导航、Toast、滚动这些一次性事件污染状态

## 关键业务流程
### 流程一：首次启动自动创建会话
1. `SessionBootstrapUseCase` 检查本地会话数
2. 若为 0，创建默认会话
3. 返回新会话 `sessionId`
4. 导航到聊天页

### 流程二：发送消息
1. 用户输入文本并点击发送
2. ViewModel 校验非空并分发发送 use case
3. Repository 先写本地用户消息
4. 启动远端流式请求
5. assistant 消息按 `Thinking -> Streaming -> Completed/Failed/Stopped` 变化
6. Flow 驱动 UI 自动刷新

### 流程三：重试失败消息
1. 用户点击失败消息旁的重试
2. ViewModel 识别失败消息类型
3. 若是用户消息失败，则重新走发送链路
4. 若是 assistant 回复失败，则复用原用户消息重新发起生成
5. 成功后覆盖更新原失败状态

### 流程四：停止回复
1. 用户点击停止
2. ViewModel 调用 `stopStreaming(requestId)`
3. Repository 取消对应 job
4. assistant 消息保留已生成内容，状态置为 `Stopped`

## 状态机方案
### 单轮消息状态机
```text
User Message:
Draft -> Sending -> Sent -> Done
Draft -> Sending -> SendFailed -> Sending

Assistant Message:
None -> Thinking -> Streaming -> Completed
Thinking -> Failed
Streaming -> Failed
Streaming -> Stopped
Failed -> Thinking
```

### 约束
- 一条用户消息最多对应一条 assistant 回复记录
- 同一会话同一时刻只允许一个进行中的 assistant 流式任务
- 若存在进行中回复，输入框仍可编辑，但发送按钮禁用或受规则约束

P1 推荐策略：
- AI 回复进行中时不允许并发发下一条消息
- 保留输入能力，但点击发送无效或按钮不可用

## 会话标题策略
P1 不做复杂标题生成，采用简单策略：
- 新会话默认标题：`新会话`
- 当首条用户消息发送成功后，以首条消息前若干字符更新标题
- 若后续接入模型标题总结能力，再替换该策略

## 错误处理方案
### 错误分类
- 用户消息发送失败
- AI 回复流中断
- 本地数据库读写失败
- 会话不存在或参数异常

### 表现策略
- 网络类错误优先映射到消息局部失败态
- 页面级致命错误只用于无法继续展示的场景，例如 `sessionId` 非法
- 所有异常进入统一错误映射层，避免 UI 直接消费原始异常

### 错误映射建议
```kotlin
sealed interface ChatError {
    data object NetworkUnavailable : ChatError
    data object RequestTimeout : ChatError
    data object ServerError : ChatError
    data object SessionNotFound : ChatError
    data object Unknown : ChatError
}
```

## 并发与协程策略
- 每个会话进行中的流式任务由 Repository 或专门的 stream coordinator 管理
- 使用 `requestId -> Job` 映射维护可取消流
- 所有数据库更新在 `IO Dispatcher`
- UI 状态汇聚在 `Main Dispatcher`
- 避免在 ViewModel 内直接维护消息真值，消息真值以数据库 Flow 为准

## 数据流设计
```text
Intent
-> ViewModel
-> UseCase
-> Repository
-> LocalDataSource / RemoteDataSource
-> Room Flow / Stream Event
-> Repository 聚合
-> ViewModel reducer
-> UiState
-> Compose View
```

## 从当前工程到 P1 的实施拆分
### 阶段 1：基础设施接入
- 接入 Compose、Navigation、Lifecycle、Coroutines
- 接入 Koin
- 接入 Room
- 接入网络库

### 阶段 2：应用骨架
- 新建 `Application`
- 新建 `MainActivity`
- 搭建根导航
- 建立 `session_list` 与 `chat` 两个页面壳子

### 阶段 3：本地数据闭环
- 完成 Session / Message Entity、DAO、Database
- 打通创建会话、查询会话、查询消息
- 完成退出重进恢复

### 阶段 4：聊天主链路
- 实现发送消息
- 实现 assistant 占位消息
- 实现流式追加更新
- 实现停止回复

### 阶段 5：异常与重试
- 用户消息失败重试
- assistant 回复失败重试
- 网络异常映射

### 阶段 6：测试与验收
- 补单元测试
- 补数据库与仓储集成测试
- 补 Compose UI 测试

## 测试方案
### 单元测试
- ViewModel reducer 测试
- use case 测试
- repository 状态转换测试
- 失败重试逻辑测试

### 数据层测试
- Room DAO 测试
- 流式事件到消息状态的映射测试

### UI 测试
- 首次进入自动创建会话
- 会话列表展示
- 进入会话并发送多轮消息
- 流式回复可见
- 失败后重试可恢复

### 验收映射
- 对应 PRD 中 5 条测试方式逐条建立测试用例

## 可扩展性预留
### 面向 P2 的预留
- `Message.role` 和 `Message.status` 设计保持可扩展
- RemoteDataSource 后续可扩展工具调用事件
- Repository 不暴露具体模型厂商字段

### 面向 P3 的预留
- Session / Message 已具备多会话隔离基础
- 后续可增加 pinned、archived、memorySummary 等字段

### 面向 P4 及以后预留
- 输入区事件模型可扩展语音输入
- Message 类型未来可增加图片、语音、系统提示

## 非目标
- 不在 P1 引入真正的插件架构
- 不在 P1 接入设备能力
- 不在 P1 处理多端同步
- 不在 P1 处理复杂消息富文本

## 技术决策总结
- 架构：单 `app` 模块内分层分包，先稳住边界
- 状态真值：本地数据库优先，UI 订阅 Flow
- 流式更新：一条 assistant 消息增量更新，不重复插入
- 错误恢复：局部消息失败态 + 原位重试
- 导航：仅保留会话列表与聊天两级路由

## 建议产出顺序
1. 先补 P1 的页面状态机文档
2. 再补 P1 的数据模型与表结构文档
3. 最后开始按阶段实施代码
