# :app 模块

## 模块职责
- 作为应用壳层，负责启动入口、导航聚合与 DI 初始化。
- 组合 `:data`、`:feature:*`、`:core:domain` 模块，提供应用级依赖装配。

## 核心类
- 应用入口：`app/MiniClawApp.kt`、`app/MainActivity.kt`。
- 导航：`app/navigation/MiniClawNavHost.kt`。
- 模块聚合：`di/AppModules.kt`（通过 `ServiceLoader` 自动发现各模块的 Koin Provider）。

## 架构图
- `:app(navigation+di聚合)` -> `:feature:chat / :feature:sessionlist` -> `:core:domain`。
- `:app` -> `:data(repository implementation -> Room/RemoteDataSource)`。

## 数据流
- 会话启动：`SessionListViewModel` 通过 `SessionRepository` 契约发起，具体实现由 `:data` 提供。
- 聊天发送：`ChatViewModel` 通过 `ChatRepository` 契约发起，具体实现由 `:data` 提供。
- 重试/停止：由 `:data` 内部仓储实现更新数据库状态并控制流式任务生命周期。
