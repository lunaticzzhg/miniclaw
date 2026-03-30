# :feature:chat 模块

## 模块职责
- 承载聊天页 UI、状态管理和聊天交互意图处理。
- 消费 `ChatRepository` / `SessionRepository` / `ModelProviderRepository` 契约，不直接依赖数据实现。

## 核心类
- UI：`feature/chat/ui/ChatRoute.kt`。
- 表现层：`feature/chat/presentation/ChatViewModel.kt`、`ChatContract.kt`。
- DI：`feature/chat/di/ChatKoinModule.kt`。

## 架构图
- `ChatRoute -> ChatViewModel -> domain repository interface`。

## 数据流
- 输入变更、发送、停止、重试 -> ViewModel -> 仓储契约 -> Flow 回流驱动 UI。
- 模型入口状态：观察当前 Provider 与可用性 -> 顶栏模型按钮文案更新 -> 点击跳转配置页。
