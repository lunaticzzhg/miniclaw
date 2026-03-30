# :core:domain 模块

## 模块职责
- 定义会话与聊天领域模型及仓储契约。
- 提供跨模块稳定依赖边界，避免业务模块直接依赖数据实现。

## 核心类
- 聊天模型：`domain/chat/model/*`。
- 会话模型：`domain/session/model/*`。
- 仓储接口：`domain/chat/repository/ChatRepository.kt`、`domain/session/repository/SessionRepository.kt`。

## 架构图
- `feature/* -> domain repository interface <- data implementation`。

## 数据流
- 该模块不持有数据与状态，只定义契约和业务语义对象。
