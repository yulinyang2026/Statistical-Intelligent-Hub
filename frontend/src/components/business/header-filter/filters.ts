/**
 * 表头漏斗筛选 → 后端通用 `filters` 参数（2026-09-10 用户需求：所有字段均可筛选）
 *
 * 后端约定：`filters` 为 JSON 字符串 `{字段: {op, value}}`，
 * op ∈ like（模糊）/ eq（精确）/ in（多值）/ gte（≥）/ lte（≤）/ between（区间，含边界）。
 */

export type FilterOp = 'like' | 'eq' | 'in' | 'gte' | 'lte' | 'between'

/** 字段 → 运算符（页面声明自己的漏斗字段与语义） */
export type FilterSpecMap = Record<string, FilterOp>

/** 判断筛选值是否为空（空值不参与查询） */
export const isEmptyFilterValue = (value: unknown): boolean => {
  if (value === undefined || value === null || value === '') return true
  if (Array.isArray(value)) {
    if (value.length === 0) return true
    if (value.every((item) => item === undefined || item === null || item === '')) return true
  }
  return false
}

/**
 * 从表单值构建 filters JSON（无有效条件时返回空串，页面据此不带该参数）
 */
export function buildFilters(source: Record<string, any>, spec: FilterSpecMap): string {
  const result: Record<string, { op: FilterOp; value: unknown }> = {}
  for (const [field, op] of Object.entries(spec)) {
    const value = source[field]
    if (isEmptyFilterValue(value)) continue
    if (op === 'between') {
      const range = Array.isArray(value) ? value : [value]
      if (range.length !== 2 || range.some((item) => isEmptyFilterValue(item))) continue
      result[field] = { op, value: range }
    } else {
      result[field] = { op, value: Array.isArray(value) ? value : `${value}` }
    }
  }
  return Object.keys(result).length ? JSON.stringify(result) : ''
}

/** 解析 filters JSON（前端本地筛选用，如部门树） */
export function parseFilters(json: string): Record<string, { op: FilterOp; value: any }> {
  if (!json) return {}
  try {
    return JSON.parse(json)
  } catch {
    return {}
  }
}

/** 本地匹配（与后端 RowFilters 同语义；部门树等非分页数据用） */
export function matchLocal(value: unknown, op: FilterOp, expected: any): boolean {
  if (value === undefined || value === null) return false
  if (Array.isArray(value)) return value.some((item) => matchLocal(item, op, expected))
  const actualText = String(value)
  switch (op) {
    case 'like':
      return actualText.toLowerCase().includes(String(expected).toLowerCase())
    case 'in':
      return (Array.isArray(expected) ? expected : [expected]).some((item) => actualText === `${item}`)
    case 'gte':
      return compareLocal(value, expected) >= 0
    case 'lte':
      return compareLocal(value, expected) <= 0
    case 'between':
      return (
        Array.isArray(expected) &&
        expected.length === 2 &&
        compareLocal(value, expected[0]) >= 0 &&
        compareLocal(value, expected[1]) <= 0
      )
    case 'eq':
    default:
      return actualText === `${expected}`
  }
}

/** 比较：数值按数值，其余按字符串（ISO 日期可直接比较） */
export function compareLocal(actual: unknown, expected: unknown): number {
  const a = Number(actual)
  const b = Number(expected)
  if (!Number.isNaN(a) && !Number.isNaN(b) && `${actual}`.trim() !== '' && `${expected}`.trim() !== '') {
    return a === b ? 0 : a > b ? 1 : -1
  }
  return String(actual).localeCompare(String(expected))
}
