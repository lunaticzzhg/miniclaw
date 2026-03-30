# :feature:sessionlist 模块

## 模块职责
- 承载会话列表页 UI、状态管理和导航触发。
- 消费 `SessionRepository` 契约，不直接依赖数据实现。

## 核心类
- UI：`feature/sessionlist/ui/SessionListRoute.kt`。
- 表现层：`feature/sessionlist/presentation/SessionListViewModel.kt`、`SessionListContract.kt`。
- DI：`feature/sessionlist/di/SessionListKoinModule.kt`。

## 架构图
- `SessionListRoute -> SessionListViewModel -> SessionRepository interface`。

## 数据流
- 页面启动 -> bootstrap + 订阅会话流 -> 列表渲染 -> 点击后发出导航 effect。
