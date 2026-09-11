@AGENTS.md

## Claude Code 专有说明

- **本文件是适配层，不含规则正文。** 全部工程约束在 `AGENTS.md`（上方 `@` 导入已在会话启动时展开）。其他工具（Codex / Cursor / Copilot 等）直接读 `AGENTS.md`，不读本文件。
- **不要在本文件里添加规则**。要改约束请改 `AGENTS.md`——这里每多写一条，就多一份必然漂移的副本。
- **Claude Code 的记忆与用户级配置不随工程走**：项目记忆在 `~/.claude/projects/<工程路径净化名>/memory/`，用户级配置在 `~/.claude/CLAUDE.md`，二者都在工程目录之外，换电脑、换工程路径即失效。凡需要长期生效的工程约定，必须落进 `AGENTS.md` 或 `docs/`，不要只写进记忆。
- 本工程 `.claude/settings.local.json` 仅存放权限白名单（机器本地），不承载任何规则。
- 查看本会话实际加载了哪些记忆文件：`/memory`。
