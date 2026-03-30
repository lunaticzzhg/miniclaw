# P1 页面状态机文档

## 文档目标
定义 P1 会话列表页与聊天页的页面状态、事件、状态迁移和副作用规则，作为 ViewModel、UiState、Intent、Effect 设计依据。

## 设计输入
- 产品需求：`prd/01-chat-session-foundation.md`
- 交互设计：`prd/P1/01-interaction-design.md`
- 技术方案：`prd/P1/02-technical-solution.md`

## 状态机设计原则
- 页面状态必须可枚举，避免隐式布尔组合失控
- 页面内一次性动作通过 `Effect` 表达，不直接塞进持久 `State`
- 消息内容真值以本地数据源为准，页面状态只负责表达展示与交互控制
- 错误状态优先局部化，只有无法展示页面时才升级为页面级异常状态

## 页面范围
- 会话列表页
- 聊天页

## 一、会话列表页状态机
### 页面目标
- 展示已有会话
- 在无会话时触发默认创建
- 支持进入已有会话和新建会话

### 状态定义
```kotlin
sealed interface SessionListPageState {
    data object Bootstrapping : SessionListPageState
    data object Loading : SessionListPageState
    data class Content(
        val sessions: List<SessionItemUiModel>
    ) : SessionListPageState
    data object Empty : SessionListPageState
    data class FatalError(
        val message: String
    ) : SessionListPageState
}
```

### 事件定义
- `ScreenStarted`
- `BootstrapSucceeded(hasSession, sessionId?)`
- `BootstrapFailed`
- `SessionsLoaded(sessions)`
- `CreateNewSessionClicked`
- `CreateSessionSucceeded(sessionId)`
- `CreateSessionFailed`
- `SessionClicked(sessionId)`

### 副作用定义
- `NavigateToChat(sessionId)`
- `ShowToast(message)`

### 状态迁移
```text
Bootstrapping
-> Loading
-> Content
-> Empty
-> FatalError
```

### 状态迁移规则
1. 页面进入后先进入 `Bootstrapping`
2. 若是冷启动且本地无会话：
   - 创建默认会话成功后直接发出 `NavigateToChat(sessionId)`
   - 列表页无需停留在空白中间态
3. 若本地已有会话：
   - 进入 `Loading`
   - 订阅会话流
   - 有数据则进入 `Content`
4. 若会话流为空且不是首次自动创建场景：
   - 进入 `Empty`
5. 若本地初始化或读取失败且无法继续：
   - 进入 `FatalError`

### 页面状态与 UI 对应
#### `Bootstrapping`
- 页面可展示轻量空白或启动占位
- 不展示空态文案，避免闪烁

#### `Loading`
- 展示列表骨架屏或加载态
- 不允许重复点击新建会话

#### `Content`
- 展示按更新时间倒序排列的会话列表
- 允许点击会话进入聊天页
- 允许点击新建会话

#### `Empty`
- 展示“还没有会话，开始第一段对话”
- 展示新建会话按钮

#### `FatalError`
- 展示错误提示
- 可保留“重试初始化”入口

## 二、聊天页状态机
### 页面目标
- 展示消息流
- 支持发送、停止、失败重试
- 让用户明确感知当前交互阶段

### 页面总状态
```kotlin
sealed interface ChatPageState {
    data object Loading : ChatPageState
    data class Ready(
        val session: ChatSessionUiModel,
        val messages: List<ChatMessageItemUiModel>,
        val input: InputState,
        val timeline: TimelineState
    ) : ChatPageState
    data class FatalError(
        val message: String
    ) : ChatPageState
}
```

### 子状态：输入区
```kotlin
data class InputState(
    val text: String = "",
    val canSend: Boolean = false,
    val canStop: Boolean = false,
    val isInputEnabled: Boolean = true
)
```

### 子状态：消息时间线
```kotlin
data class TimelineState(
    val hasInFlightReply: Boolean = false,
    val isAtBottom: Boolean = true,
    val showJumpToBottom: Boolean = false
)
```

### 页面事件
- `ScreenStarted(sessionId)`
- `SessionLoaded`
- `SessionNotFound`
- `MessagesUpdated(messages)`
- `InputChanged(text)`
- `SendClicked`
- `SendAccepted`
- `SendRejected`
- `AssistantThinkingStarted(requestId)`
- `AssistantStreamingStarted(requestId)`
- `AssistantStreamingCompleted(requestId)`
- `AssistantStreamingFailed(messageId)`
- `StopClicked(requestId)`
- `StopSucceeded(requestId)`
- `RetryUserMessageClicked(messageId)`
- `RetryAssistantMessageClicked(messageId)`
- `RetryAccepted`
- `RetryRejected`
- `ListScrolled(isAtBottom)`
- `BackClicked`

### 页面副作用
- `NavigateBack`
- `ScrollToBottom`
- `ShowToast(message)`

## 三、聊天页主状态迁移
```text
Loading -> Ready
Loading -> FatalError
Ready -> Ready
Ready -> FatalError
```

说明：
- 聊天页多数变化都在 `Ready` 内部发生
- `FatalError` 仅用于 `sessionId` 无效、会话被删除、关键数据损坏等无法继续展示的场景

## 四、聊天页内部状态机
### 1. 输入区状态机
输入区不单独跳页面状态，而是在 `Ready.input` 内变化。

#### 状态规则
```text
EmptyIdle
Typing
ReadyToSend
StreamingLocked
```

#### 迁移说明
1. 初始进入聊天页：
   - 输入为空
   - `canSend = false`
   - `canStop = false`
2. 用户输入非空文本：
   - 进入可发送状态
   - `canSend = true`
3. 用户点击发送后：
   - 若当前没有进行中的 assistant 回复，则受理发送
   - 输入内容清空
   - `canSend = false`
4. assistant 进入 `Streaming`：
   - `canStop = true`
   - 输入框可继续编辑
   - 发送按钮不可用
5. assistant 完成、失败或停止：
   - `canStop = false`
   - 若输入框有内容，则重新允许发送

### 2. 单轮对话状态机
单轮对话由“用户消息”和“assistant 回复”共同组成。

```text
Idle
-> UserSending
-> AssistantThinking
-> AssistantStreaming
-> AssistantCompleted

Idle
-> UserSending
-> UserSendFailed
-> UserSending

Idle
-> UserSending
-> AssistantThinking
-> AssistantFailed
-> AssistantThinking

AssistantStreaming
-> AssistantStopped
```

### 3. 消息项状态机
每个消息项按自身角色进入不同状态机。

#### 用户消息状态机
```text
Sending -> Sent
Sending -> SendFailed
SendFailed -> Sending
```

#### Assistant 消息状态机
```text
Thinking -> Streaming
Thinking -> Failed
Streaming -> Completed
Streaming -> Failed
Streaming -> Stopped
Failed -> Thinking
```

## 五、关键交互链路状态展开
### 链路 1：首次发送消息
1. 页面 `Loading -> Ready`
2. 用户输入文本，`canSend = true`
3. 点击发送
4. 用户消息插入列表，状态 `Sending`
5. 用户消息更新为 `Sent`
6. assistant 占位消息插入，状态 `Thinking`
7. assistant 首包到达，状态变为 `Streaming`
8. 文本逐步追加
9. 完成后变为 `Completed`

### 链路 2：用户消息发送失败
1. 用户点击发送
2. 本地消息插入成功
3. 网络请求提交失败
4. 该用户消息变为 `SendFailed`
5. 页面保留原消息与重试按钮
6. 用户点击重试
7. 消息重新进入 `Sending`

### 链路 3：assistant 回复失败
1. 用户消息已发送成功
2. assistant 占位消息处于 `Thinking` 或 `Streaming`
3. 流式请求异常中断
4. assistant 消息变为 `Failed`
5. 用户点击重试
6. assistant 消息重新进入 `Thinking`

### 链路 4：用户停止回复
1. assistant 消息处于 `Streaming`
2. 输入区显示停止按钮
3. 用户点击停止
4. 流式任务取消
5. assistant 消息变为 `Stopped`
6. 已生成文本保留

## 六、滚动状态机
### 状态定义
```text
FollowingBottom
ReadingHistory
```

### 迁移规则
1. 默认处于 `FollowingBottom`
2. 用户主动上滑离开底部，进入 `ReadingHistory`
3. 在 `ReadingHistory` 中，若有新消息到来：
   - 不强制跳底
   - `showJumpToBottom = true`
4. 用户点击“回到底部”或手动滑到底部：
   - 回到 `FollowingBottom`
   - `showJumpToBottom = false`

## 七、页面级异常策略
### 会话列表页
- 若初始化失败但可恢复，优先 toast + 重试
- 若数据库不可用导致无法展示，进入 `FatalError`

### 聊天页
- `sessionId` 不存在
- 会话已被删除
- 关键数据映射失败且无法恢复

这些场景进入 `FatalError`，建议只保留：
- 错误文案
- 返回按钮
- 重试按钮

## 八、状态与数据边界
### 页面状态持有内容
- 当前输入文本
- 当前页面滚动辅助状态
- 当前页面一次性交互效果

### 页面状态不持有真值
- 不把消息列表作为唯一真值缓存于 ViewModel 内
- 不把 session 是否存在写死在页面内存中
- 不把进行中回复内容只存在内存里

说明：
- 真值应持久化到数据库
- 页面订阅数据库 Flow 后渲染
- 这样进程重建后仍可恢复

## 九、建议的 MVI 契约
### 会话列表页
- `SessionListIntent`
- `SessionListState`
- `SessionListEffect`

### 聊天页
- `ChatIntent`
- `ChatState`
- `ChatEffect`

### 命名建议
- `State` 只表达当前页面可稳定复用的展示状态
- `Effect` 只承载一次性动作
- `Intent` 必须对应用户行为或系统输入事件

## 十、开发落地建议
1. 先实现会话列表页状态机与聊天页 Loading/Ready/FatalError 主状态
2. 再补输入区、滚动区和单条消息状态映射
3. 最后接入流式事件，把 assistant 状态机跑通

## 非目标
- 不在 P1 做复杂离线恢复提示
- 不在 P1 处理多并发回复
- 不在 P1 引入复杂的消息分组、日期分段、草稿箱状态
