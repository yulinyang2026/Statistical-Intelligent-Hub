<template>
  <div class="md-editor art-card-xs p-0">
    <!-- 工具栏（只读预览隐藏） -->
    <div v-if="!readonly" class="flex flex-wrap items-center gap-1 border-b p-2" style="border-color: var(--art-card-border)">
      <ElButtonGroup size="small">
        <ElButton :title="t('system.doc.mdBold')" @click="wrapSelection('**')"><b>B</b></ElButton>
        <ElButton :title="t('system.doc.mdItalic')" @click="wrapSelection('*')"><i>I</i></ElButton>
        <ElButton v-for="level in [1, 2, 3]" :key="level" :title="t('system.doc.mdHeading', { level })" @click="prefixLine('#'.repeat(level) + ' ')">
          H{{ level }}
        </ElButton>
      </ElButtonGroup>
      <ElButtonGroup size="small" class="ml-1">
        <ElButton :title="t('system.doc.mdList')" @click="prefixLine('- ')">•&nbsp;—</ElButton>
        <ElButton :title="t('system.doc.mdOrderedList')" @click="prefixLine('1. ')">1.</ElButton>
        <ElButton :title="t('system.doc.mdTaskList')" @click="prefixLine('- [ ] ')">☐</ElButton>
      </ElButtonGroup>
      <ElButtonGroup size="small" class="ml-1">
        <ElButton :title="t('system.doc.mdQuote')" @click="prefixLine('> ')">❝</ElButton>
        <ElButton :title="t('system.doc.mdTable')" @click="insertSnippet(TABLE_SNIPPET)">▦</ElButton>
        <ElButton :title="t('system.doc.mdCode')" @click="wrapLines('```\n', '\n```')">{ }</ElButton>
        <ElButton :title="t('system.doc.mdDivider')" @click="insertSnippet('\n---\n')">—</ElButton>
        <ElButton :title="t('system.doc.mdLink')" @click="wrapSelection('[', '](https://)')">
          <ArtSvgIcon icon="ri:link" />
        </ElButton>
      </ElButtonGroup>
      <!-- 编辑 / 预览切换 -->
      <ElRadioGroup v-model="mode" size="small" class="ml-2">
        <ElRadioButton value="edit">{{ t('system.doc.editMode') }}</ElRadioButton>
        <ElRadioButton value="preview">{{ t('system.doc.previewMode') }}</ElRadioButton>
      </ElRadioGroup>
    </div>

    <!-- 编辑区 -->
    <textarea
      v-show="!readonly && mode === 'edit'"
      ref="textareaRef"
      class="w-full resize-none bg-transparent p-3 text-sm outline-none"
      :style="{ height }"
      :value="modelValue"
      :placeholder="t('system.doc.contentPlaceholder')"
      @input="handleInput"
      @blur="cacheSelection"
      @mouseup="cacheSelection"
      @keyup="cacheSelection"
    />

    <!-- 预览区 -->
    <div
      v-show="readonly || mode === 'preview'"
      class="md-preview overflow-auto p-3 text-sm"
      :style="{ height }"
      v-html="rendered"
    />
  </div>
</template>

<script setup lang="ts">
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'MarkdownEditor' })

  const props = withDefaults(
    defineProps<{
      modelValue: string
      /** 只读：纯预览（隐藏工具栏，无编辑区） */
      readonly?: boolean
      height?: string
    }>(),
    { modelValue: '', readonly: false, height: '360px' }
  )

  const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

  const { t } = useI18n()

  const TABLE_SNIPPET = '| 列1 | 列2 | 列3 |\n| --- | --- | --- |\n| 内容 | 内容 | 内容 |'

  const mode = ref<'edit' | 'preview'>('edit')
  const textareaRef = useTemplateRef<HTMLTextAreaElement>('textareaRef')

  /** 最近一次选区（blur 后仍可作用于选中文本） */
  const selection = { start: 0, end: 0, text: '' }

  const cacheSelection = () => {
    const el = textareaRef.value
    if (!el) return
    selection.start = el.selectionStart
    selection.end = el.selectionEnd
    selection.text = el.value.slice(el.selectionStart, el.selectionEnd)
  }

  const handleInput = (event: Event) => {
    emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
  }

  /** 应用变更并保持光标位置 */
  const apply = (value: string, cursorStart: number, cursorEnd: number) => {
    emit('update:modelValue', value)
    nextTick(() => {
      const el = textareaRef.value
      if (!el) return
      el.focus()
      el.setSelectionRange(cursorStart, cursorEnd)
      cacheSelection()
    })
  }

  /** 选中文本包裹（如加粗/斜体/链接）；无选中时用占位文案（链接）或空串，光标落在内容区 */
  const wrapSelection = (before: string, after: string = before) => {
    const el = textareaRef.value
    if (!el) return
    const value = el.value
    const selected = value.slice(selection.start, selection.end)
    const inner = selected || (before === '[' ? t('system.doc.mdLinkText') : '')
    const next = value.slice(0, selection.start) + before + inner + after + value.slice(selection.end)
    apply(next, selection.start + before.length, selection.start + before.length + inner.length)
  }

  /** 行首前缀（标题/列表/引用；多行逐行添加，全部已有前缀则视为取消） */
  const prefixLine = (prefix: string) => {
    const el = textareaRef.value
    if (!el) return
    const value = el.value
    const start = value.lastIndexOf('\n', selection.start - 1) + 1
    let end = value.indexOf('\n', selection.end)
    if (end === -1) end = value.length
    const lines = value.slice(start, end).split('\n')
    const allPrefixed = lines.every((line) => line.startsWith(prefix))
    const next = lines.map((line) => (allPrefixed ? line.slice(prefix.length) : prefix + line)).join('\n')
    const value2 = value.slice(0, start) + next + value.slice(end)
    apply(value2, start, start + next.length)
  }

  /** 整块包裹（代码块） */
  const wrapLines = (before: string, after: string) => {
    const el = textareaRef.value
    if (!el) return
    const value = el.value
    const selected = value.slice(selection.start, selection.end)
    const next = value.slice(0, selection.start) + before + selected + after + value.slice(selection.end)
    const cursor = selection.start + before.length + selected.length
    apply(next, cursor, cursor)
  }

  /** 光标处插入片段 */
  const insertSnippet = (snippet: string) => {
    const el = textareaRef.value
    if (!el) return
    const value = el.value
    const next = value.slice(0, selection.start) + snippet + value.slice(selection.end)
    apply(next, selection.start + snippet.length, selection.start + snippet.length)
  }

  // ==================== Markdown 渲染（自研，无第三方依赖） ====================

  /** HTML 转义（防注入：所有源文本先转义再套 Markdown 标记） */
  const escapeHtml = (s: string) =>
    s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

  /** 链接安全校验：仅放行 http(s)/mailto/锚点/相对路径 */
  const safeUrl = (url: string) => (/^(https?:\/\/|mailto:|#|\/)/i.test(url) ? url : '')

  /** 行内代码占位定界符（正文不会出现的控制字符，避免与普通文本混淆） */
  const NUL = String.fromCharCode(0)

  /** 行内标记：行内代码 → 加粗 → 斜体 → 删除线 → 链接 */
  const renderInline = (raw: string) => {
    let text = escapeHtml(raw)
    const codes: string[] = []
    text = text.replace(/`([^`]+)`/g, (_, code) => {
      codes.push(code)
      return `${NUL}${codes.length - 1}${NUL}`
    })
    text = text.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    text = text.replace(/\*([^*]+)\*/g, '<em>$1</em>')
    text = text.replace(/~~([^~]+)~~/g, '<del>$1</del>')
    text = text.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (match, label, url) => {
      const safe = safeUrl(url)
      return safe ? `<a href="${safe}" target="_blank" rel="noopener">${label}</a>` : match
    })
    const codePattern = new RegExp(`${NUL}(\\d+)${NUL}`, 'g')
    text = text.replace(codePattern, (_, index) => `<code>${codes[Number(index)]}</code>`)
    return text
  }

  /** 块级解析：代码块 / 标题 / 分割线 / 引用 / 任务清单 / 有无序列表 / 表格 / 段落 */
  const renderMarkdown = (source: string): string => {
    if (!source) {
      return `<p class="md-empty">${t('system.doc.emptyContent')}</p>`
    }
    const lines = source.replace(/\r\n/g, '\n').split('\n')
    const html: string[] = []
    let i = 0
    while (i < lines.length) {
      const line = lines[i]

      // 围栏代码块
      if (/^```/.test(line.trim())) {
        const buf: string[] = []
        i++
        while (i < lines.length && !/^```/.test(lines[i].trim())) {
          buf.push(lines[i])
          i++
        }
        i++
        html.push(`<pre><code>${escapeHtml(buf.join('\n'))}</code></pre>`)
        continue
      }

      // 空行
      if (!line.trim()) {
        i++
        continue
      }

      // 分割线
      if (/^(-{3,}|\*{3,})$/.test(line.trim())) {
        html.push('<hr />')
        i++
        continue
      }

      // 标题
      const heading = line.match(/^(#{1,6})\s+(.*)$/)
      if (heading) {
        const level = heading[1].length
        html.push(`<h${level}>${renderInline(heading[2])}</h${level}>`)
        i++
        continue
      }

      // 引用块
      if (/^>\s?/.test(line)) {
        const buf: string[] = []
        while (i < lines.length && /^>\s?/.test(lines[i])) {
          buf.push(lines[i].replace(/^>\s?/, ''))
          i++
        }
        html.push(`<blockquote>${buf.map((l) => `<p>${renderInline(l)}</p>`).join('')}</blockquote>`)
        continue
      }

      // 表格（当前行含 | 且下一行为分隔行）
      if (line.includes('|') && i + 1 < lines.length && /^\s*\|?[\s:|-]+\|[\s:|-]*$/.test(lines[i + 1]) && lines[i + 1].includes('-')) {
        const splitRow = (row: string) =>
          row.replace(/^\s*\|/, '').replace(/\|\s*$/, '').split('|').map((cell) => cell.trim())
        const headers = splitRow(line)
        i += 2
        const rows: string[][] = []
        while (i < lines.length && lines[i].includes('|') && lines[i].trim()) {
          rows.push(splitRow(lines[i]))
          i++
        }
        html.push(
          `<table><thead><tr>${headers.map((h) => `<th>${renderInline(h)}</th>`).join('')}</tr></thead>` +
          `<tbody>${rows.map((r) => `<tr>${r.map((cell) => `<td>${renderInline(cell)}</td>`).join('')}</tr>`).join('')}</tbody></table>`
        )
        continue
      }

      // 任务清单 / 无序列表
      if (/^[-*]\s+/.test(line)) {
        const items: { task: boolean; done: boolean; text: string }[] = []
        while (i < lines.length && /^[-*]\s+/.test(lines[i])) {
          const task = lines[i].match(/^[-*]\s+\[( |x|X)\]\s+(.*)$/)
          if (task) {
            items.push({ task: true, done: task[1].toLowerCase() === 'x', text: task[2] })
          } else {
            items.push({ task: false, done: false, text: lines[i].replace(/^[-*]\s+/, '') })
          }
          i++
        }
        const allTasks = items.every((item) => item.task)
        if (allTasks) {
          html.push(
            `<ul class="md-tasks">${items
              .map((item) => `<li class="${item.done ? 'md-done' : ''}">${item.done ? '☑' : '☐'} ${renderInline(item.text)}</li>`)
              .join('')}</ul>`
          )
        } else {
          html.push(`<ul>${items.map((item) => `<li>${renderInline(item.text)}</li>`).join('')}</ul>`)
        }
        continue
      }

      // 有序列表
      if (/^\d+\.\s+/.test(line)) {
        const items: string[] = []
        while (i < lines.length && /^\d+\.\s+/.test(lines[i])) {
          items.push(lines[i].replace(/^\d+\.\s+/, ''))
          i++
        }
        html.push(`<ol>${items.map((item) => `<li>${renderInline(item)}</li>`).join('')}</ol>`)
        continue
      }

      // 段落（连续非空行合并）
      const buf: string[] = []
      while (
        i < lines.length &&
        lines[i].trim() &&
        !/^(```|#{1,6}\s|>|[-*]\s|\d+\.\s)/.test(lines[i]) &&
        !/^(-{3,}|\*{3,})$/.test(lines[i].trim())
      ) {
        buf.push(lines[i])
        i++
      }
      if (buf.length) {
        html.push(`<p>${buf.map((l) => renderInline(l)).join('<br />')}</p>`)
      }
    }
    return html.join('\n')
  }

  const rendered = computed(() => renderMarkdown(props.modelValue))

  /** 保存后进入预览模式（FR-DOC-004） */
  const preview = () => {
    mode.value = 'preview'
  }

  defineExpose({ preview })
</script>

<style scoped lang="scss">
  /* 编辑器自身排版（不覆盖 Element Plus 默认样式；颜色全部走全局变量） */
  .md-preview {
    line-height: 1.7;
    color: var(--art-gray-800, var(--default-text-color));

    :deep(h1),
    :deep(h2),
    :deep(h3),
    :deep(h4),
    :deep(h5),
    :deep(h6) {
      margin: 0.8em 0 0.4em;
      font-weight: 600;
      line-height: 1.4;
    }

    :deep(h1) {
      font-size: 1.5em;
    }

    :deep(h2) {
      font-size: 1.3em;
    }

    :deep(h3) {
      font-size: 1.15em;
    }

    :deep(p) {
      margin: 0.5em 0;
    }

    :deep(a) {
      color: var(--art-primary);
      text-decoration: underline;
    }

    :deep(code) {
      padding: 0.15em 0.4em;
      border-radius: var(--custom-radius, 4px);
      background-color: var(--art-gray-100);
      color: var(--art-danger);
      font-family: monospace;
    }

    :deep(pre) {
      margin: 0.6em 0;
      padding: 0.75em 1em;
      overflow-x: auto;
      border-radius: var(--custom-radius, 4px);
      background-color: var(--art-gray-100);

      code {
        padding: 0;
        background-color: transparent;
        color: var(--art-gray-800);
      }
    }

    :deep(blockquote) {
      margin: 0.6em 0;
      padding: 0.4em 1em;
      border-left: 3px solid var(--art-primary);
      color: var(--art-gray-600);
      background-color: var(--art-hover-color);
      border-radius: var(--custom-radius, 4px);

      p {
        margin: 0.2em 0;
      }
    }

    :deep(ul),
    :deep(ol) {
      margin: 0.5em 0;
      padding-left: 1.5em;

      li {
        margin: 0.2em 0;
      }
    }

    :deep(ul.md-tasks) {
      list-style: none;
      padding-left: 0.5em;

      li.md-done {
        color: var(--art-gray-500);
        text-decoration: line-through;
      }
    }

    :deep(table) {
      margin: 0.6em 0;
      border-collapse: collapse;
      width: 100%;

      th,
      td {
        padding: 0.4em 0.8em;
        border: 1px solid var(--art-card-border);
        text-align: left;
      }

      th {
        background-color: var(--art-gray-100);
        font-weight: 600;
      }
    }

    :deep(hr) {
      margin: 1em 0;
      border: none;
      border-top: 1px solid var(--art-card-border);
    }

    :deep(del) {
      color: var(--art-gray-500);
    }

    .md-empty {
      color: var(--art-gray-500);
    }
  }
</style>
