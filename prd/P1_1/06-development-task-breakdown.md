# P1_1 开发任务拆解

## 文档目标
将 P1_1「真实模型链路与模型可插拔」拆解为一组独立可开发、独立可验证、可逐项验收的任务，作为实际研发排期与执行依据。

## 拆解原则
- 每个任务必须有明确交付物
- 每个任务必须能单独验证，不依赖“大联调后再看”
- 优先拆成对主链路有实际推进价值的任务包
- 尽量控制单任务边界，避免“一个任务覆盖整个阶段”
- 允许任务之间存在前置依赖，但验收动作必须独立

## 建议执行顺序
1. 先完成 Provider 抽象和配置存储底座
2. 再完成 MiniMax 真实接入和流式解析
3. 然后接配置页与聊天页模型入口
4. 最后把真实模型接回聊天主链路并完成测试补齐

## 任务列表总览
| 任务编号 | 任务名称 | 是否阻塞主链路 | 独立可验证结果 |
| --- | --- | --- | --- |
| T1 | Provider 基础抽象与 DI 注册 | 是 | 代码中可按 ProviderId 获取实现 |
| T2 | Provider 配置本地存储 | 是 | 可保存并读取默认 Provider 与配置 |
| T3 | 模型可用性校验能力 | 是 | 可区分未配置、鉴权失败、网络失败等状态 |
| T4 | MiniMax Provider 接入 | 是 | 可向 MiniMax 发起真实或 Mock 流式请求 |
| T5 | MiniMax 流式事件解析 | 是 | 可把流式响应转为统一 ChatStreamEvent |
| T6 | 模型配置页 UI 与状态机 | 否 | 可在页面完成配置输入、保存、测试 |
| T7 | 聊天页顶部模型切换入口 | 否 | 聊天页右上角可进入配置页 |
| T8 | 聊天发送链路接入真实 Provider | 是 | 发送消息后收到真实模型回复 |
| T9 | 停止回复与失败重试适配真实模型 | 是 | 停止和重试在真实链路下成立 |
| T10 | 模型异常反馈与去配置入口 | 否 | 用户可感知未配置/鉴权失败并跳转修复 |
| T11 | FakeProvider/测试替身验证可插拔 | 否 | 不改聊天主流程即可切换测试实现 |
| T12 | 自动化测试与回归验收 | 是 | 单测、集成测、UI 测覆盖关键链路 |

## T1 Provider 基础抽象与 DI 注册
### 目标
建立与供应商无关的模型 Provider 接口和注册机制，避免业务层直接依赖 MiniMax 实现。

### 主要工作
- 定义 `ModelProviderId`
- 定义 `ChatModelProvider`
- 定义 `ModelProviderRepository` 基础接口
- 建立 `providerId -> provider implementation` 注册方式
- 在 DI 中完成 Provider 集合注入

### 输入
- `prd/P1_1/03-technical-solution.md`
- `prd/P1_1/05-data-model-and-schema.md`

### 输出
- Provider 抽象接口代码
- Provider 注册与获取逻辑
- DI 配置代码

### 完成定义
- 业务层不再需要直接 new 或直接引用某个具体 Provider
- 能根据 `ModelProviderId.MINIMAX` 取到对应实现

### 验证方式
1. 编写单元测试或调试代码，验证传入 `MINIMAX` 可返回 `MiniMaxChatModelProvider`
2. 替换为测试实现后，业务层调用代码无需修改

### 前置依赖
- 无

## T2 Provider 配置本地存储
### 目标
实现默认 Provider 与最小配置的本地持久化，为聊天链路和配置页提供真值来源。

### 主要工作
- 建立 `provider_preferences` 存储结构
- 建立敏感信息存储封装，例如 `apiKey`
- 实现 `saveProviderConfig / getCurrentProvider / observeCurrentProvider`
- 处理冷启动恢复逻辑

### 输入
- `prd/P1_1/03-technical-solution.md`
- `prd/P1_1/05-data-model-and-schema.md`

### 输出
- DataStore 或等价本地配置实现
- 敏感配置存储实现
- Repository 映射代码

### 完成定义
- 可以保存默认 Provider
- 可以保存并读取 MiniMax 所需最小配置
- 重启应用后仍能恢复配置

### 验证方式
1. 保存一份 MiniMax 配置
2. 重新读取并断言字段一致
3. 重启 App 或重建 Repository 后，配置仍可恢复

### 前置依赖
- T1

## T3 模型可用性校验能力
### 目标
建立统一的模型可用性判断和错误分类能力，支撑聊天页与配置页状态反馈。

### 主要工作
- 定义 `ModelAvailability`
- 定义 `ModelCallError`
- 实现最小字段完整性校验
- 实现联网校验结果映射
- 建立“未配置 / 鉴权失败 / 网络失败 / 服务失败”统一转换

### 输入
- `prd/P1_1/03-technical-solution.md`
- `prd/P1_1/04-page-state-machine.md`

### 输出
- 校验 use case
- 错误映射器
- Availability 状态流

### 完成定义
- 配置页和聊天页都能拿到统一的可用性状态
- 不同失败原因不会都被压成一个 `Unknown`

### 验证方式
1. 空配置返回 `NotConfigured`
2. 模拟 401/403 返回 `AuthFailed`
3. 模拟断网返回 `NetworkUnavailable`
4. 模拟 5xx 返回 `ServiceUnavailable`

### 前置依赖
- T1
- T2

## T4 MiniMax Provider 接入
### 目标
完成 MiniMax 真实 Provider 的网络接入，使 App 具备向真实模型发请求的能力。

### 主要工作
- 创建 `MiniMaxApiService`
- 创建请求 DTO 和响应 DTO
- 实现 `MiniMaxChatModelProvider`
- 实现鉴权 header、baseUrl、modelName 注入

### 输入
- `prd/P1_1/03-technical-solution.md`
- `prd/P1_1/05-data-model-and-schema.md`

### 输出
- MiniMax 网络接口
- Provider 实现代码
- DTO 与协议适配代码

### 完成定义
- Provider 层可单独发起聊天请求
- 请求参数和鉴权逻辑都收敛在 MiniMax 实现内部

### 验证方式
1. 使用 MockWebServer 或真实测试账号发起一次请求
2. 能收到服务端响应或至少拿到结构化错误
3. 上层不需要知道任何 MiniMax DTO 字段

### 前置依赖
- T1
- T2

## T5 MiniMax 流式事件解析
### 目标
把 MiniMax 的流式返回统一转换为 `ChatStreamEvent`，供聊天主链路复用。

### 主要工作
- 解析流式 chunk
- 把首包、增量文本、完成、异常统一映射为 `ChatStreamEvent`
- 对协议异常做兜底处理

### 输入
- `prd/P1_1/03-technical-solution.md`
- `prd/P1_1/05-data-model-and-schema.md`

### 输出
- `MiniMaxStreamMapper`
- 流式解析逻辑
- 相关单元测试

### 完成定义
- 可以从 MiniMax 流式返回中持续产出 `Started / Delta / Completed / Failed`

### 验证方式
1. 用模拟流式数据输入解析器
2. 断言事件顺序正确
3. 异常 chunk 可被映射为 `Failed`

### 前置依赖
- T4

## T6 模型配置页 UI 与状态机
### 目标
实现模型配置页或面板，使用户或开发者能完成最小 Provider 配置、保存与测试连接。

### 主要工作
- 创建 `model_config` 页面
- 实现 `ModelConfigUiState / Intent / Effect`
- 支持 Provider 选择、参数输入、保存、测试连接
- 展示成功、失败、校验中状态

### 输入
- `prd/P1_1/02-interaction-design.md`
- `prd/P1_1/04-page-state-machine.md`

### 输出
- 配置页 UI
- 配置页 ViewModel
- 页面导航接入

### 完成定义
- 用户可进入配置页
- 可编辑 MiniMax 配置并触发保存与测试连接
- 页面可明确展示校验成功或失败

### 验证方式
1. 打开配置页，输入配置并点击保存
2. 输入合法配置时看到成功反馈
3. 输入非法配置时看到失败反馈

### 前置依赖
- T2
- T3

## T7 聊天页顶部模型切换入口
### 目标
在聊天页顶部栏右上角接入模型切换入口，作为唯一模型配置入口。

### 主要工作
- 在聊天页顶部栏展示当前 Provider 标签或状态
- 响应点击事件进入配置页
- 读取并展示当前模型可用性摘要

### 输入
- `prd/P1_1/02-interaction-design.md`
- `prd/P1_1/04-page-state-machine.md`

### 输出
- 聊天页顶部入口 UI
- 入口点击逻辑
- 当前 Provider 状态展示

### 完成定义
- 聊天页右上角能看到模型入口
- 点击后能进入配置页
- 会话列表页没有该入口

### 验证方式
1. 进入聊天页检查右上角入口存在
2. 点击后跳转到配置页
3. 切回会话列表页，确认无模型入口

### 前置依赖
- T2
- T6

## T8 聊天发送链路接入真实 Provider
### 目标
将现有聊天发送链路从假数据或模拟实现切换到真实 Provider，实现真实模型回复。

### 主要工作
- 改造 `ChatRepository.sendUserMessage`
- 接入当前默认 Provider 配置
- 用真实 `ChatStreamEvent` 驱动 assistant 消息状态变化
- 保持 P1 的消息持久化策略不变

### 输入
- `prd/P1_1/03-technical-solution.md`
- `prd/P1_1/04-page-state-machine.md`

### 输出
- 改造后的聊天仓储主链路
- 真实 assistant 流式消息更新逻辑

### 完成定义
- 用户发送消息后，assistant 回复来自真实模型
- `Thinking -> Streaming -> Completed` 状态链在真实链路下成立

### 验证方式
1. 配置有效 MiniMax 凭据
2. 在聊天页发送一条消息
3. 观察 assistant 消息从思考中进入回复中并最终完成

### 前置依赖
- T3
- T4
- T5

## T9 停止回复与失败重试适配真实模型
### 目标
保证 P1 已有的停止回复和失败重试能力在真实模型链路下仍然成立。

### 主要工作
- 将 `stopStreaming(requestId)` 连接到真实流请求取消
- 适配 assistant 失败重试
- 适配用户消息失败重试
- 保持已生成内容保留策略

### 输入
- `prd/P1_1/03-technical-solution.md`
- `prd/P1_1/04-page-state-machine.md`

### 输出
- 真实链路取消逻辑
- 重试逻辑适配代码

### 完成定义
- 流式回复中可停止
- 停止后状态变为 `Stopped`
- 失败消息可重试且不需重进会话

### 验证方式
1. 发起长回复并点击停止
2. 验证流停止且已生成文本保留
3. 制造一次失败后点击重试，验证链路恢复

### 前置依赖
- T8

## T10 模型异常反馈与去配置入口
### 目标
在未配置、鉴权失败、网络失败等场景下，为用户提供明确反馈和恢复动作。

### 主要工作
- 在聊天消息局部展示模型失败状态
- 根据失败原因展示“重试”或“去配置”
- 从失败消息上下文跳转到配置页
- 返回后保留原会话上下文

### 输入
- `prd/P1_1/02-interaction-design.md`
- `prd/P1_1/04-page-state-machine.md`

### 输出
- 失败态 UI
- 去配置动作
- 返回聊天页恢复逻辑

### 完成定义
- 用户不会只看到静默失败
- 配置缺失和鉴权失败都能找到修复入口

### 验证方式
1. 清空配置后发送消息，验证出现去配置入口
2. 填入错误凭据后发送消息，验证出现明确失败提示
3. 从失败消息进入配置页并返回，确认会话未丢失

### 前置依赖
- T6
- T7
- T8

## T11 FakeProvider/测试替身验证可插拔
### 目标
用一个测试 Provider 验证“可插拔”不是只停留在接口定义层。

### 主要工作
- 实现 `FakeChatModelProvider` 或等价测试实现
- 接入 DI 注册
- 支持通过配置或构建开关切换到测试实现

### 输入
- `prd/P1_1/03-technical-solution.md`

### 输出
- FakeProvider 代码
- 对应注册与切换逻辑

### 完成定义
- 不修改聊天页和 `ChatRepository` 代码，即可切换到测试 Provider

### 验证方式
1. 切换到 FakeProvider
2. 在聊天页发送消息
3. 能收到测试实现返回的回复，且页面状态正常

### 前置依赖
- T1
- T8

## T12 自动化测试与回归验收
### 目标
为 P1_1 的关键链路补齐自动化测试，并完成整期回归验收。

### 主要工作
- 补 `ModelProviderRepository` 单测
- 补 `MiniMaxStreamMapper` 单测
- 补聊天仓储真实链路状态流转测试
- 补配置页与聊天页关键 UI 测试
- 输出回归验收清单

### 输入
- `prd/P1_1/01-real-model-integration-and-provider-pluggability.md`
- `prd/P1_1/03-technical-solution.md`
- `prd/P1_1/04-page-state-machine.md`

### 输出
- 单元测试
- 集成测试
- UI 测试
- 回归验收记录

### 完成定义
- 关键成功链路和失败链路均有自动化覆盖
- 可以按文档验收标准执行一次完整回归

### 验证方式
1. 运行单元测试，验证 Provider 抽象、配置存储、流式解析通过
2. 运行集成测试，验证真实链路状态流转正确
3. 运行 UI 测试，验证配置入口、模型入口和发送主链路可用

### 前置依赖
- T3
- T5
- T6
- T8
- T9
- T10

## 里程碑建议
### M1 底座完成
- T1
- T2
- T3

可验证结果：
- Provider 可抽象
- 配置可保存
- 可用性可判断

### M2 真实模型可连通
- T4
- T5
- T8

可验证结果：
- App 内消息可拿到真实 MiniMax 回复

### M3 交互闭环完成
- T6
- T7
- T9
- T10

可验证结果：
- 用户可配置模型、发送消息、停止回复、修复错误

### M4 可插拔与质量收口
- T11
- T12

可验证结果：
- Provider 可替换
- 自动化覆盖关键链路

## 不建议合并的大任务
- 不要把“Provider 抽象 + MiniMax 接入 + 聊天主链路改造”合并成一个任务
- 不要把“模型配置页 UI + 配置存储 + 校验逻辑”合并成一个任务
- 不要把“停止回复 + 重试 + 错误映射 + UI 反馈”合并成一个任务

说明：
- 这些内容虽然彼此相关，但拆开后更容易定位风险、安排并行开发和独立验收

