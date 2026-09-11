# 日常管理平台（Statistical Intelligent Hub）

政府统计业务线的日常管理平台，用于模块/专题/项目/任务的全生命周期管理与计划编排。

## 工程结构

| 目录 | 说明 |
| ---- | ---- |
| `frontend/` | **前端工程**（Vue 3 + TypeScript + Element Plus，Vite 构建） |
| `backend/`  | **后端工程**（Java 21 + Spring Boot 3.3 + Gradle 8.9，数据存于 `backend/data/` JSON 文件） |
| `docs/`     | **需求文档 + 设计文档**（需求规格模块化文档、技术设计文档、AI 开发准则、二次开发目录分级清单） |

## 快速开始

```bash
# 前端：启动开发服务器
cd frontend
pnpm install   # 首次
pnpm dev

# 前端：构建与类型检查
pnpm build

# 后端：通过 IDEA 打开 backend/ 工程运行（Gradle 项目，无 wrapper）
```

> 完整的启动 / 运行 / 停止操作（环境要求、端口与联调配置、按端口强制停止、常见问题）见 [`docs/运行操作手册.md`](docs/运行操作手册.md)。

## 文档入口

- 运行操作手册：`docs/运行操作手册.md`（前、后端启动/运行/停止）
- 需求文档：`docs/需求规格/00-索引与开发指南.md`（模块化拆分版，AI 开发依据）
- 需求合订本（人读）：`docs/需求规格/需求规格说明书_V6.0.md`
- 变更记录与文档同步约定：`docs/需求规格/99-变更记录与文档约定.md`
- 技术设计文档：`docs/技术设计文档.md`
- AI 开发强制准则：`docs/AI-GUIDE.md`
