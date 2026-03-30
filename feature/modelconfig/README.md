# :feature:modelconfig 模块

## 模块职责
- 承载模型配置页 UI、状态管理与配置交互流程。
- 消费 `ModelProviderRepository` 契约，不直接依赖 data 实现。

## 核心类
- UI：`feature/modelconfig/ui/ModelConfigRoute.kt`。
- 表现层：`feature/modelconfig/presentation/ModelConfigViewModel.kt`、`ModelConfigContract.kt`。
- DI：`feature/modelconfig/di/ModelConfigKoinModule.kt`。

## 架构图
- `ModelConfigRoute -> ModelConfigViewModel -> ModelProviderRepository`。

## 数据流
- 页面启动加载配置 -> 编辑字段 -> 保存/测试连接 -> 状态与一次性效果回流 UI。
