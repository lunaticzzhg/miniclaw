# :data 模块

## 模块职责
- 实现领域仓储接口，提供本地持久化与远端流式数据访问。
- 管理 Room 实体/DAO/数据库、远端 DataSource、Repository 实现与数据映射。

## 核心类
- DI：`data/di/DataKoinModule.kt`。
- 本地存储：`data/local/*`。
- 远端数据源：`data/remote/*`。
- 仓储实现：`data/repository/LocalSessionRepository.kt`、`data/repository/LocalChatRepository.kt`。

## 架构图
- `domain repository interface <- data repository implementation -> local/remote data source`。

## 数据流
- 会话流：DAO Flow -> Repository -> Domain Model。
- 聊天流：发送请求 -> 更新本地消息状态 -> 流式事件增量写库 -> UI 订阅回流。
