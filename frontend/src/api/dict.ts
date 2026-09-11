import request from '@/utils/http'

/**
 * 基础数据（枚举字典）API
 *
 * 元数据驱动：dict（字典）+ dict_field（字段）+ dict_item（枚举项，ext 存扩展字段值）。
 */

/** 字典分页 */
export function fetchDictPage(params: Api.Dict.DictSearchParams) {
  return request.get<Api.Dict.DictList>({
    url: '/api/system/dict/page',
    params
  })
}

/** 业务下拉：按字典 code 取启用枚举项（含 ext，供状态色块等真实渲染） */
export function fetchDictOptions(code: string) {
  return request.get<Api.Dict.DictItem[]>({
    url: '/api/system/dict/options',
    params: { code }
  })
}

/** 新增字典 */
export function fetchCreateDict(params: Api.Dict.DictSaveParams) {
  return request.post<null>({
    url: '/api/system/dict',
    params
  })
}

/** 修改字典（编码不可改） */
export function fetchUpdateDict(params: Api.Dict.DictSaveParams) {
  return request.put<null>({
    url: '/api/system/dict',
    params
  })
}

/** 启停字典 */
export function fetchChangeDictStatus(params: { id: number; status: number }) {
  return request.put<null>({
    url: `/api/system/dict/${params.id}/status`,
    params: { status: params.status }
  })
}

/** 删除字典（内置不可删） */
export function fetchDeleteDict(params: { id: number }) {
  return request.del<null>({
    url: `/api/system/dict/${params.id}`
  })
}

/** 字段列表 */
export function fetchDictFields(dictId: number) {
  return request.get<Api.Dict.DictField[]>({
    url: `/api/system/dict/${dictId}/fields`
  })
}

/** 新增字段 */
export function fetchCreateDictField(params: Api.Dict.DictFieldSaveParams) {
  return request.post<null>({
    url: '/api/system/dict/field',
    params
  })
}

/** 修改字段（字段编码不可改） */
export function fetchUpdateDictField(params: Api.Dict.DictFieldSaveParams) {
  return request.put<null>({
    url: '/api/system/dict/field',
    params
  })
}

/** 启停字段（停用后表单不再展示、校验跳过） */
export function fetchChangeDictFieldStatus(params: { id: number; status: number }) {
  return request.put<null>({
    url: `/api/system/dict/field/${params.id}/status`,
    params: { status: params.status }
  })
}

/** 枚举项列表（kw 模糊 code/name） */
export function fetchDictItems(dictId: number, kw?: string) {
  return request.get<Api.Dict.DictItem[]>({
    url: `/api/system/dict/${dictId}/items`,
    params: { kw }
  })
}

/** 新增枚举项 */
export function fetchCreateDictItem(params: Api.Dict.DictItemSaveParams) {
  return request.post<null>({
    url: '/api/system/dict/item',
    params
  })
}

/** 修改枚举项（code 不可改） */
export function fetchUpdateDictItem(params: Api.Dict.DictItemSaveParams) {
  return request.put<null>({
    url: '/api/system/dict/item',
    params
  })
}

/** 启停枚举项 */
export function fetchChangeDictItemStatus(params: { id: number; status: number }) {
  return request.put<null>({
    url: `/api/system/dict/item/${params.id}/status`,
    params: { status: params.status }
  })
}

/** 枚举项上移 / 下移（界面已改为拖动排序，保留兼容） */
export function fetchMoveDictItem(params: { id: number; direction: 'up' | 'down' }) {
  return request.put<null>({
    url: `/api/system/dict/item/${params.id}/move`,
    params: { direction: params.direction }
  })
}

/** 枚举项拖动排序（2026-09-11 用户需求：按传入 id 顺序重写 sort） */
export function fetchReorderDictItems(params: { dictId: number; ids: number[] }) {
  return request.post<null>({
    url: `/api/system/dict/${params.dictId}/items/reorder`,
    params: { ids: params.ids }
  })
}

/** 逻辑删除枚举项 */
export function fetchDeleteDictItem(params: { id: number }) {
  return request.del<null>({
    url: `/api/system/dict/item/${params.id}`
  })
}
