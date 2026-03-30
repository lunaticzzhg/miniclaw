# P1_1 页面状态机文档

## 文档目标
定义 P1_1 会话列表页、聊天页与模型配置页的页面状态、事件、状态迁移和副作用规则，作为 ViewModel、UiState、Intent、Effect 设计依据。

## 设计输入
- 产品需求：`prd/P1_1/01-real-model-integration-and-provider-pluggability.md`
- 交互设计：`prd/P1_1/02-interaction-design.md`
- 技术方案：`prd/P1_1/03-technical-solution.md`
- P1 页面状态机：`prd/P1/03-page-state-machine.md`

## 状态机设计原则
- 沿用 P1 已验证的聊天状态机，不重复推翻已有消息生命周期
- Provider 可用性与消息状态分层建模，避免把“模型是否可用”混同为“本条消息是否失败”
- 页面内一次性动作通过 `Effect` 表达，不直接塞进持久 `State`
- 配置问题优先局部化为聊天页或配置页状态，不升级为页面级致命错误
- 切换 Provider 与配置校验属于显式交互流程，必须有独立状态反馈

## 页面范围
- 会话列表页
- 聊天页
- 模型配置页

## 一、会话列表页状态机
### 页面目标
- 维持 P1 的默认启动和会话浏览体验
- 支持进入已有会话和新建会话
- 不承担模型切换或模型配置职责

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
1. 会话列表页完全复用 P1 启动逻辑
2. 冷启动无会话时：
   - 自动创建默认会话
   - 直接 `NavigateToChat(sessionId)`
3. 有会话时：
   - 进入 `Loading`
   - 订阅会话流
   - 根据结果进入 `Content` 或 `Empty`
4. 本页不因模型未配置进入额外状态
5. 模型相关问题应延后到聊天页处理

## 二、聊天页状态机
### 页面目标
- 展示消息流
- 支持发送、停止、失败重试
- 让用户明确感知当前模型可用性与请求阶段
- 提供顶部栏右侧模型切换入口

### 页面总状态
```kotlin
sealed interface ChatPageState {
    data object Loading : ChatPageState
    data class Ready(
        val session: ChatSessionUiModel,
        val messages: List<ChatMessageItemUiModel>,
        val input: InputState,
        val timeline: TimelineState,
        val model: ModelState
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

### 子状态：模型状态
```kotlin
data class ModelState(
    val currentProviderId: ModelProviderId? = null,
    val currentProviderLabel: String = "",
    val availability: ModelAvailabilityUi = ModelAvailabilityUi.Unknown,
    val isSwitcherEnabled: Boolean = true
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
- `ProviderAvailabilityLoaded(availability)`
- `ProviderAvailabilityChanged(availability)`
- `ModelSwitcherClicked`
- `ReturnFromModelConfig`
- `AssistantThinkingStarted(requestId)`
- `AssistantStreamingStarted(requestId)`
- `AssistantStreamingCompleted(requestId)`
- `AssistantStreamingFailed(messageId, error)`
- `StopClicked(requestId)`
- `StopSucceeded(requestId)`
- `RetryUserMessageClicked(messageId)`
- `RetryAssistantMessageClicked(messageId)`
- `RetryAfterConfigClicked`
- `RetryAccepted`
- `RetryRejected`
- `ListScrolled(isAtBottom)`
- `BackClicked`

### 页面副作用
- `NavigateBack`
- `NavigateToModelConfig(sessionId)`
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
- 聊天页的主状态仍以 `Loading / Ready / FatalError` 为主
- P1_1 的新增复杂度主要体现在 `Ready.model` 子状态中
- `FatalError` 仍只用于会话无效、关键数据损坏等无法继续展示的场景

## 四、聊天页内部状态机
### 1. 输入区状态机
输入区不单独跳页面主状态，而是在 `Ready.input` 内变化。

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

说明：
- 即使 Provider 处于 `NotConfigured`，只要用户输入非空，`canSend` 仍可为 `true`
- 是否真正受理发送，由发送链路根据 `Ready.model.availability` 决定

### 2. 模型入口状态机
模型切换入口不单独跳页面主状态，而是在 `Ready.model` 内变化。

#### 状态定义
```text
Unknown
Available
NotConfigured
Validating
AuthFailed
NetworkUnavailable
ServiceUnavailable
```

#### 迁移规则
1. 聊天页进入后：
   - 先读取当前 Provider 与上次已知可用性
   - 若无配置，进入 `NotConfigured`
2. 用户从配置页返回且保存成功：
   - 触发重新加载
   - 进入 `Validating` 或直接进入 `Available`
3. 若配置校验失败：
   - 根据原因进入 `AuthFailed / NetworkUnavailable / ServiceUnavailable`
4. 若当前可正常发送请求：
   - 进入 `Available`

### 3. 单轮对话状态机
单轮对话由“用户消息”“assistant 回复”和“模型可用性校验”共同组成。

```text
Idle
-> UserSending
-> ProviderChecking
-> AssistantThinking
-> AssistantStreaming
-> AssistantCompleted

Idle
-> UserSending
-> ProviderRejected

Idle
-> UserSending
-> ProviderChecking
-> AssistantThinking
-> AssistantFailed
-> AssistantThinking

AssistantStreaming
-> AssistantStopped
```

说明：
- `ProviderChecking` 表示发送链路已进入真实模型前置校验
- `ProviderRejected` 代表因为未配置或配置无效，本轮请求未真正进入正常模型生成

### 4. 消息项状态机
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

说明：
- 若发送前即发现 `NotConfigured`，实现上可选择：
  - 用户消息进入 `SendFailed`
  - 或插入一条 assistant 失败消息
- P1_1 推荐优先沿用 P1 语义：
  - 用户消息写入后仍可保留
  - assistant 位置展示失败信息与“去配置/重试”入口

## 五、聊天关键交互链路状态展开
### 链路 1：模型已配置，正常发送
1. 页面 `Loading -> Ready`
2. `Ready.model = Available`
3. 用户输入文本，`canSend = true`
4. 点击发送
5. 用户消息插入列表，状态 `Sending`
6. 用户消息更新为 `Sent`
7. assistant 占位消息插入，状态 `Thinking`
8. assistant 首包到达，状态变为 `Streaming`
9. 文本逐步追加
10. 完成后变为 `Completed`

### 链路 2：发送时发现模型未配置
1. 页面处于 `Ready`
2. `Ready.model = NotConfigured`
3. 用户点击发送
4. 发送链路拒绝进入正常模型调用
5. 页面发出 `NavigateToModelConfig(sessionId)` 或展示原位引导
6. 聊天页保留当前会话上下文
7. 用户完成配置后返回聊天页

### 链路 3：模型鉴权失败
1. `Ready.model = Available` 或 `Unknown`
2. 用户点击发送
3. assistant 占位消息进入 `Thinking`
4. 请求返回鉴权错误
5. assistant 消息变为 `Failed`
6. `Ready.model.availability` 更新为 `AuthFailed`
7. 用户可选择重试或进入配置页修复

### 链路 4：网络异常或服务不可用
1. 用户点击发送
2. assistant 占位消息处于 `Thinking` 或 `Streaming`
3. 请求超时、断网或服务异常
4. assistant 消息变为 `Failed`
5. `Ready.model.availability` 更新为对应错误态
6. 用户可点击重试

### 链路 5：用户点击模型切换入口
1. 页面处于 `Ready`
2. 用户点击顶部栏右侧入口
3. 发出 `NavigateToModelConfig(sessionId)`
4. 当前聊天页状态保持不销毁
5. 配置成功后返回聊天页并刷新 `Ready.model`

### 链路 6：用户停止回复
1. assistant 消息处于 `Streaming`
2. 输入区显示停止按钮
3. 用户点击停止
4. 流式任务取消
5. assistant 消息变为 `Stopped`
6. 已生成文本保留

## 六、模型配置页状态机
### 页面目标
- 让用户或开发者完成当前 Provider 的最小配置
- 支持保存、测试连接、切换 Provider 和返回聊天页
- 对配置问题提供明确反馈

### 页面总状态
```kotlin
sealed interface ModelConfigPageState {
    data object Loading : ModelConfigPageState
    data class Editing(
        val selectedProvider: ModelProviderId,
        val form: ModelConfigFormState,
        val validation: ValidationState
    ) : ModelConfigPageState
    data class FatalError(
        val message: String
    ) : ModelConfigPageState
}
```

### 子状态：表单
```kotlin
data class ModelConfigFormState(
    val apiKey: String = "",
    val modelName: String = "",
    val baseUrl: String = "",
    val canSave: Boolean = false,
    val canValidate: Boolean = false
)
```

### 子状态：校验状态
```kotlin
sealed interface ValidationState {
    data object Idle : ValidationState
    data object Saving : ValidationState
    data object Validating : ValidationState
    data object Success : ValidationState
    data class Failed(
        val reason: ModelAvailabilityUi
    ) : ValidationState
}
```

### 页面事件
- `ScreenStarted`
- `CurrentConfigLoaded`
- `CurrentConfigLoadFailed`
- `ProviderSelected(providerId)`
- `ApiKeyChanged(value)`
- `ModelNameChanged(value)`
- `BaseUrlChanged(value)`
- `SaveClicked`
- `SaveSucceeded`
- `SaveFailed`
- `ValidateClicked`
- `ValidateSucceeded`
- `ValidateFailed(reason)`
- `BackClicked`
- `ReturnToChatClicked`

### 页面副作用
- `NavigateBack`
- `NavigateBackToChat(sessionId)`
- `ShowToast(message)`

### 主状态迁移
```text
Loading -> Editing
Loading -> FatalError
Editing -> Editing
Editing -> FatalError
```

### 状态迁移规则
1. 页面进入后先进入 `Loading`
2. 读取当前 Provider 配置成功后进入 `Editing`
3. 用户修改任一字段后仍留在 `Editing`
4. 点击保存后：
   - `validation = Saving`
   - 成功后回到 `Editing + Success`
5. 点击测试连接后：
   - `validation = Validating`
   - 根据结果变为 `Success` 或 `Failed(reason)`
6. 若当前配置读取失败且无法继续编辑，则进入 `FatalError`

## 七、模型配置页内部状态机
### 1. 表单状态机
```text
Pristine
Editing
ReadyToSave
Saving
```

#### 迁移规则
1. 初始读取完成后，进入 `Pristine`
2. 用户任意修改字段后，进入 `Editing`
3. 表单满足最小必填要求后，进入 `ReadyToSave`
4. 点击保存后进入 `Saving`
5. 保存完成后回到 `Editing` 或 `Pristine`

### 2. 校验状态机
```text
Idle
Validating
Success
Failed
```

#### 迁移规则
1. 初始为 `Idle`
2. 点击“保存并测试”或“测试连接”后进入 `Validating`
3. 成功则进入 `Success`
4. 失败则进入 `Failed`
5. 用户继续修改表单后，可回到 `Idle`

### 3. Provider 选择状态机
```text
SingleProviderReady
MultiProviderSelectable
```

说明：
- P1_1 当前大概率处于 `SingleProviderReady`
- 即使只有 MiniMax，也建议 UI 按可切换结构建模
- 后续接入第二个 Provider 时不需要重做状态机

## 八、页面级异常策略
### 会话列表页
- 与 P1 保持一致
- 模型问题不提升为列表页异常

### 聊天页
- `sessionId` 不存在
- 会话已被删除
- 关键数据映射失败且无法恢复

这些场景进入 `FatalError`。

### 模型配置页
- 当前配置读取损坏且无法修复
- Provider 元数据缺失，无法构建表单

这些场景进入 `FatalError`。

## 九、状态与数据边界
### 页面状态持有内容
- 当前输入文本
- 当前页面滚动辅助状态
- 当前 Provider 标签和可用性
- 模型配置页表单输入内容
- 当前一次性校验反馈

### 页面状态不持有真值
- 不把消息列表作为唯一真值缓存于 ViewModel 内
- 不把 Provider 原始远端响应直接放入页面状态
- 不把密钥明文作为长期 UI 真值来源

说明：
- 消息与会话真值仍以数据库为准
- Provider 配置真值以本地配置存储为准
- 页面只订阅并映射展示状态

## 十、建议的 MVI 契约
### 会话列表页
- `SessionListIntent`
- `SessionListState`
- `SessionListEffect`

### 聊天页
- `ChatIntent`
- `ChatState`
- `ChatEffect`

### 模型配置页
- `ModelConfigIntent`
- `ModelConfigState`
- `ModelConfigEffect`

## 十一、开发落地建议
1. 先复用 P1 会话列表页状态机，不在该页扩展模型逻辑
2. 再为聊天页新增 `ModelState` 子状态与 `ModelSwitcherClicked` 事件
3. 然后实现模型配置页 `Loading / Editing / FatalError` 主状态
4. 最后接通真实 Provider 校验与聊天发送链路，让消息状态机在真实模型下跑通

## 十二、非目标
- 不在 P1_1 处理多并发模型请求
- 不在 P1_1 设计复杂模型参数调优面板
- 不在 P1_1 加入多 Provider 并列对比或分流策略

