# Statistical-Intelligent-Hub 工程强制准则

> **本文件是本工程开发约束的唯一真源（Single Source of Truth），对所有 AI 编码工具强制生效。**
> 冲突时以本文件为准；任何其他副本（用户级配置、个人记忆、历史文档）一律不具效力。

## 0. 本文件的读取方式

| 工具 | 如何读到本文件 |
|---|---|
| Claude Code | 自动加载工程根 `CLAUDE.md`，其内容为一行 `@AGENTS.md`（导入展开） |
| Codex / Cursor / Copilot / Gemini CLI / Windsurf / Aider / Zed / Jules / Amp / Warp 等 | 自动加载工程根 `AGENTS.md`（本文件） |
| 人工 / 其他 | 直接阅读本文件 |

**工程结构**：`frontend/`（Vue 3 + TS + Element Plus 前端工程）、`backend/`（Java 17 + Spring Boot 3.3 + Gradle 后端工程）、`docs/`（需求文档 + 设计文档）。

**配套文档**：

1. `docs/AI-GUIDE.md` —— **详细开发参考**（页面骨架代码、组件与变量清单明细、类型检查基线）。规则正文以本文件为准，该文件仅供查细节。
2. `docs/二次开发-目录分级清单.md` —— 目录分级：禁改清单、可复用组件/Hooks/Utils 清单、配置文件路径速查。
3. `docs/需求规格/` —— 需求文档目录：用户需求强制登记入口 `16-待确认与非需求项.md`，变更记录与同步约定 `99-变更记录与文档约定.md`。

**工程约定的存放规则（重要）**：凡本工程的开发约束、口径、规范，**一律写入本文件或 `docs/` 下的工程内文件**。禁止只写在用户级配置（如 `~/.claude/CLAUDE.md`）或个人记忆里——那些内容不随工程目录走，换电脑或换工具即丢失。

---

## 1. 需求沟通

- 每次执行时需要以提问的方式明确真实需求（范围、目标页面、数据来源、验收标准），禁止在不确认的情况下直接动手写码。
- 需求涉及修改框架层文件（见第 5 节）时，必须先提出替代方案并等待确认。
- 需求不明确时优先提问，而不是"先写一版给你看"。
- **需求与改动必须落需求文档并保留修改记录（强制）**：用户每次提出的需求/改动意向，先登记 `docs/需求规格/16-待确认与非需求项.md` 第 2 节「对话需求登记」；定稿后归位到对应模块文件（涉及字段/枚举先改 `docs/需求规格/02-数据模型.md`）并在条目内留痕（提出时间/来源/结论）；每次改动在 `docs/需求规格/99-变更记录与文档约定.md` 版本记录追加一行并递增版本号。**未同步文档视为任务未完成。**

---

## 2. 样式规则

- 所有**颜色、圆角、阴影、间距**必须使用工程定义好的变量 / Tailwind 原子类，**绝对禁止硬编码色值 / 像素值**（禁止 `#xxx`、`rgb()`、`border-radius: 8px`、`box-shadow: 0 2px 8px rgba(...)`、`margin: 16px` 这类写法）。
- 全局字体、动画、过渡效果全部沿用现有配置，**不要自定义新动画**（不新增 `@keyframes`、不新增 transition 曲线、不改 `duration`）。
- 新页面的 padding、margin 和现有页面保持一致，**不要自己调整**；沿用 Tailwind 原子类即可（`p-4`、`mt-4`、`gap-3`），容器统一使用 `.page-content`。
- 需要视觉调整时，只能改全局变量，不要在页面里写局部样式。

**颜色 / 圆角变量来源（唯一真源）：`frontend/src/assets/styles/core/tailwind.css`**

| 类别 | 可用变量 |
|---|---|
| 主题色 | `--art-primary`、`--theme-color`、`--main-color`、`--el-color-primary` |
| 语义色 | `--art-success`、`--art-warning`、`--art-danger`、`--art-error`、`--art-info`、`--art-secondary` |
| 灰阶 | `--art-gray-100` ~ `--art-gray-900`（暗色模式自动反转） |
| 背景 / 容器 | `--default-bg-color`、`--default-box-color`、`--art-hover-color`、`--art-active-color` |
| 边框 | `--art-card-border`、`--default-border`、`--default-border-dashed` |
| 圆角 / 尺寸 | `--custom-radius`、`--el-component-custom-height` |
| Element 变量 | `--el-border-radius-base`、`--el-color-primary-light-9` 等（`frontend/src/assets/styles/core/el-ui.scss`） |

**容器 / 卡片类（直接用，不要自己写样式）：**

- `.page-content` —— 页面根容器（自带边框 + 主题适配，`frontend/src/assets/styles/core/app.scss`）
- `.art-card` / `.art-card-sm` / `.art-card-xs` —— 卡片容器（跟随设置面板的边框/阴影模式自动切换）
- `.art-table-card`、`.art-badge`、`.art-text-badge` —— 见 `app.scss`

---

## 3. 组件规则

- **表格**优先使用 `useTable` hooks + `ArtTable` + `ArtTableHeader`（一体化支持分页、搜索、列显隐、缓存、5 种刷新策略），**不要用原生 `ElTable` 从零写**。
- **表单**使用 `ArtForm`，**搜索区**使用 `ArtSearchBar`，**不要用原生 `ElForm` 从零堆**。
- **图标**优先使用 `frontend/src/assets/images/` 下的现有资源（`@imgs` 别名）与 Iconify（`ArtSvgIcon` / Iconify 图标名如 `ri:pie-chart-line`），**不要引入新的图标库**。
- **弹窗、抽屉、消息提示**全部沿用 Element Plus 全局样式配置（`ElDialog` / `ElDrawer` / `ElMessage` / `ElMessageBox`），**不要复写样式**。
- 行内操作按钮统一用 `ArtButtonTable`（`type`: `add | edit | delete | more | view`）。
- 二次开发优先级：`components/core` 已有 → 不要自己写第二套。

**导入规则（已实测，照抄避免踩坑）：**

| 名称 | 是否需要手写 import |
|---|---|
| 模板中的 `ElXxx` 组件、`Art*` 组件、`v-auth` / `v-ripple` 指令 | ❌ 不需要（自动注册） |
| `ref` / `computed` / `reactive` / `useTemplateRef` 等 Vue API | ❌ 不需要（auto-import） |
| `ElMessage` | ❌ 不需要（`ElMessage` 已在 auto-imports.d.ts） |
| `ElMessageBox` | ✅ 需要：`import { ElMessageBox } from 'element-plus'` |
| `useI18n` | ✅ 需要：`import { useI18n } from 'vue-i18n'` |
| `useTable` / `useTableColumns` | ✅ 需要：`import { useTable } from '@/hooks'` |
| `request` | ✅ 需要：`import request from '@/utils/http'` |

**可直接复用的核心清单：**

| 能力 | 名称 | 路径 |
|---|---|---|
| 表格 | `ArtTable` / `ArtTableHeader` | `frontend/src/components/core/tables/` |
| 表单 / 搜索栏 | `ArtForm` / `ArtSearchBar` | `frontend/src/components/core/forms/` |
| 表格数据 | `useTable` / `useTableColumns` | `frontend/src/hooks/core/` |
| 图表 | `ArtLineChart` 等 9 个 | `frontend/src/components/core/charts/` |
| 卡片 | `ArtStatsCard` 等 8 个 | `frontend/src/components/core/cards/` |
| 异常 / 结果页 | `ArtException` / `ArtResultPage` | `frontend/src/components/core/views/` |
| 请求 | `request`（`get/post/put/del`） | `frontend/src/utils/http/index.ts` |
| 校验规则 | 手机号 / 身份证 / 银行卡 / 密码强度等 | `frontend/src/utils/form/validator.ts` |
| 按钮权限 | `v-auth` | `frontend/src/directives/core/auth.ts` |

---

## 4. 参考模板

> ⚠️ 工程已无保留的业务示例页（原 `system/user`、`examples/*` 等已删除）。
> **API 真源**以下列组件源码为准；**页面结构**直接复制 `docs/AI-GUIDE.md` 第 4.3 / 4.4 节的骨架（已按真实 API 编写并实测零类型错误）。

### 4.1 权威来源（组件源码 = 规范）

| 想写的页面 | 先读源码 |
|---|---|
| 表格列配置 `ColumnOption` | `frontend/src/types/component/index.ts` |
| 表格 props / 插槽 / 分页事件 | `frontend/src/components/core/tables/art-table/index.vue` |
| 表单项 `FormItem`（`key/label/type/props/options/span/hidden/render/slots`） | `frontend/src/components/core/forms/art-form/index.vue` |
| 搜索项 `SearchFormItem`（同上） | `frontend/src/components/core/forms/art-search-bar/index.vue` |
| 表格数据 hooks 配置与返回值 | `frontend/src/hooks/core/useTable.ts` |
| 现有页面外壳写法 | `frontend/src/views/result/success/index.vue`、`frontend/src/views/exception/404/index.vue` |
| 菜单/路由写法 | `frontend/src/router/modules/result.ts`、`frontend/src/router/modules/exception.ts` |
| 接口定义写法 | `frontend/src/api/system-manage.ts`、`frontend/src/types/api/api.d.ts` |
| 列表页 / 表单弹窗完整骨架 | `docs/AI-GUIDE.md` 第 4.3 / 4.4 节（照抄） |

### 4.2 关键约定（照抄，不要自创）

- 页面根目录 class：**`.page-content`**
- `<script setup lang="ts">` 必须有 `defineOptions({ name: 'Xxx' })`，name 与路由 `name` 保持一致
- 表格：`ArtTable` 的插槽名 = 列的 `prop`，且该列必须标注 `useSlot: true`
- 分页事件：`@pagination:size-change`、`@pagination:current-change`
- 接口泛型：`request.get<Api.Common.PaginatedResponse<XxxItem>>({ url, params })`
- 文案统一走 `t('xxx')`，新业务 key 加进 `frontend/src/locales/langs/zh.json` / `en.json`（当前 `common` 节点只有 `tips / cancel / confirm / logOutTips`）

### 4.3 新增一个业务模块的标准动作

1. `frontend/src/views/<module>/index.vue`（列表）+ `frontend/src/views/<module>/modules/xxx-dialog.vue`（表单），骨架照抄 `docs/AI-GUIDE.md` 4.3 / 4.4
2. `frontend/src/api/<module>.ts` 定义接口，类型写进 `frontend/src/types/api/api.d.ts`
3. `frontend/src/router/modules/<module>.ts` 写菜单（`component: '/index/index'` 为一级菜单布局，子项 `component: '/<module>/index'`）
4. 在 `frontend/src/router/modules/index.ts` 的 import 与 `routeModules` 数组中登记
5. 文案加进 `frontend/src/locales/langs/zh.json` 与 `en.json`
6. 按第 6 节验证构建与类型

---

## 5. 禁止事项

- **绝对不要修改**（注意：本工程的真实路径与通用模板不同）：
  - `frontend/src/assets/`（含 `frontend/src/assets/styles/`、`frontend/src/assets/images/`、`frontend/src/assets/svg/`）—— 全局样式变量与所有静态资源
  - `frontend/src/components/core/` —— 58 个 `Art*` 框架组件
  - `frontend/src/views/index/index.vue` —— **布局容器**（本工程没有 `frontend/src/layout/` 目录，布局在这里）
  - `frontend/src/views/outside/Iframe.vue`、`frontend/src/views/auth/`、`frontend/src/views/exception/`
  - `frontend/src/router/` 下 7 个路由核心类文件（ComponentLoader / IframeRouteManager / MenuProcessor / RoutePermissionValidator / RouteRegistry / RouteTransformer / RouteValidator，2026-09-09 由 `router/core/` 扁平化）、`frontend/src/router/guards/`、`frontend/src/router/routes/staticRoutes.ts`、`frontend/src/router/routesAlias.ts`
  - `frontend/src/store/modules/`、`frontend/src/hooks/core/`、`frontend/src/utils/`、`frontend/src/directives/core/`、`frontend/src/enums/`、`frontend/src/plugins/`、`frontend/src/locales/index.ts`
  - `frontend/src/types/import/*.d.ts`（自动生成）、`frontend/src/mock/json/chinaMap.json`、`frontend/src/mock/upgrade/changeLog.ts`
  - `frontend/` 下的 `vite.config.ts`、`tsconfig.json`、`.env*`、`.env.development`、`.env.production`、`index.html`
- **绝对不要引入新的 npm 依赖**（任何第三方库先确认是否已有等价能力：ECharts、@vueuse/core、dayjs 等已在依赖中）。
- **绝对不要写 `<style scoped>` 去覆盖 Element Plus 默认样式**；需要调整一律走全局变量 / Tailwind 原子类 / `frontend/src/assets/styles/core/` 下的全局文件（且需人工确认后再改）。
- 不要改动 `frontend/package.json` 依赖版本、不要删改既有的 `frontend/pnpm-lock.yaml`。
- 不要删除第 5 节清单中任何现有文件——它们多为框架运行时依赖。

---

## 6. 交付前自检

- [ ] 未出现硬编码色值 / 圆角 / 阴影 / 间距像素值
- [ ] 未新增 `@keyframes` 或自定义 transition
- [ ] 页面根容器使用 `.page-content`，间距沿用 Tailwind 原子类
- [ ] 表格走 `useTable` + `ArtTable`，表单走 `ArtForm`，搜索走 `ArtSearchBar`
- [ ] 未修改第 5 节任何文件
- [ ] 未新增 npm 依赖
- [ ] `<script setup lang="ts">` 内含 `defineOptions({ name })`
- [ ] 新菜单已在 `router/modules/index.ts` 登记，文案已进 `locales/langs/*.json`
- [ ] 构建与类型检查通过：在 `frontend/` 目录执行 `pnpm build`（vue-tsc + vite build）

### 6.1 类型检查基线（2026-09-09 已修复）

历史遗留的 20 个 vue-tsc 框架类型错误已于 2026-09-09 全部修复，**当前基线为 0 错误，`pnpm build` 完整通过**。此后任何文件报类型错误都是新问题，不允许当作"基线"忽略；修复时优先保持既有 API 语义不变，涉及第 5 节框架层文件仍需先提需求确认。

---

## 7. 本文件的维护约定

- **改约束只改这里**。不要在 `CLAUDE.md`、用户级配置、个人记忆或其他文档里另写一份规则正文——那会产生副本并必然漂移。
- `CLAUDE.md` 是纯适配层（仅 `@AGENTS.md` 一行 + Claude Code 专有补充），**不得写入规则正文**。
- `docs/AI-GUIDE.md` 是详细参考（骨架代码、清单明细），**规则口径冲突时以本文件为准**。
- 本文件变更属工程级变更，需按第 1 节同步 `docs/需求规格/99-变更记录与文档约定.md` 版本记录。
- **体量约束**：本文件会被每个会话完整加载，请控制在 32 KiB 以内（部分工具在此处静默截断）。超出时应把细节下沉到 `docs/AI-GUIDE.md`，此处只留规则。
