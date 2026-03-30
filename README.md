# MiniClaw

## 整体架构
- 技术栈：Kotlin + Compose + Navigation + MVI + Room + Koin。
- 分层模型：`View -> ViewModel -> Repository -> DataSource`。
- 数据真值：本地 Room 数据库（`sessions`、`messages`）。

## 模块关系
- `:app`：应用壳层，负责启动、导航聚合与模块装配。
- `:data`：数据实现层，负责 Room、本地仓储实现与远端数据源接入。
- `:core:domain`：领域模型与仓储契约。
- `:feature:chat`：聊天页面与聊天交互表现层。
- `:feature:sessionlist`：会话列表页面与会话交互表现层。

## 核心数据流
- 会话链路：`SessionListRoute -> SessionListViewModel -> SessionRepository -> Room -> Flow -> UI`。
- 聊天链路：`ChatRoute -> ChatViewModel -> ChatRepository -> 本地数据库 + 远端流 -> Flow -> UI`。
- 流式链路：`startChat -> 插入 assistant 思考中 -> 增量追加 delta -> 完成/失败/停止`。
