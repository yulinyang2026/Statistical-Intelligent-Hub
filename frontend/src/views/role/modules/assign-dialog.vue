<!-- 角色授权弹窗（菜单/接口/数据三类权限勾选 + 数据范围设置）
     2026-09-11 用户需求：① 弹窗改为**全屏**（内容与交互不变）；② 接口权限由竖向树改为**按模块分组的矩阵**排列
     （每组支持全选/清空，右上角显示已选总数）；③ 演示页面、帮助中心的菜单权限点已从后端下线，不再出现 -->
<template>
  <ElDialog
    v-model="visible"
    :title="t('system.role.assign')"
    fullscreen
    :close-on-click-modal="false"
    append-to-body
    @closed="handleClosed"
  >
    <div v-loading="loading" class="flex flex-col gap-4 pr-1">
      <!-- ① 接口权限（矩阵） -->
      <div>
        <div class="mb-2 flex items-center gap-2">
          <span class="text-sm font-medium">{{ t('system.role.apiPermission') }}</span>
          <span class="text-xs text-g-500">
            {{ t('system.role.permSelected', { n: apiChecked.size }) }}
          </span>
        </div>
        <div class="flex flex-col gap-2">
          <div
            v-for="group in apiGroups"
            :key="group.key"
            class="border-full-d rounded-custom-xs p-3"
          >
            <div class="mb-2 flex items-center gap-2">
              <ElCheckbox
                :model-value="group.allChecked"
                :indeterminate="group.someChecked && !group.allChecked"
                @change="(checked: any) => toggleGroup(group.ids, !!checked)"
              >
                <span class="text-sm font-medium">{{ group.label }}</span>
              </ElCheckbox>
              <span class="text-xs text-g-500">{{ group.items.length }}</span>
            </div>
            <div class="grid grid-cols-2 gap-x-4 gap-y-1 pl-6 md:grid-cols-3 xl:grid-cols-4">
              <ElCheckbox
                v-for="item in group.items"
                :key="item.id"
                :model-value="apiChecked.has(item.id)"
                @change="(checked: any) => toggleItem(item.id, !!checked)"
              >
                <span class="text-[13px]">{{ item.name }}</span>
              </ElCheckbox>
            </div>
          </div>
        </div>
      </div>

      <!-- ② 菜单权限 -->
      <div v-if="menuNodes.length">
        <div class="mb-2 text-sm font-medium">{{ t('system.role.menuPermission') }}</div>
        <ElTree
          ref="menuTreeRef"
          :data="menuNodes"
          node-key="id"
          show-checkbox
          default-expand-all
          :props="{ label: 'name', children: 'children' }"
        />
      </div>

      <!-- ③ 数据权限 -->
      <div v-if="dataNodes.length">
        <div class="mb-2 text-sm font-medium">{{ t('system.role.dataPermission') }}</div>
        <ElTree
          ref="dataTreeRef"
          :data="dataNodes"
          node-key="id"
          show-checkbox
          default-expand-all
          :props="{ label: 'name', children: 'children' }"
        />
      </div>

      <!-- ④ 数据范围 -->
      <div class="grid grid-cols-2 gap-3">
        <div>
          <div class="mb-2 text-sm font-medium">{{ t('system.role.dataScope') }}</div>
          <ElSelect v-model="formData.dataScope" :placeholder="t('system.role.dataScopePlaceholder')" class="w-full">
            <ElOption v-for="option in dataScopeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </ElSelect>
        </div>
        <div v-if="formData.dataScope === 6">
          <div class="mb-2 text-sm font-medium">{{ t('system.role.customOrgIds') }}</div>
          <ElTreeSelect
            v-model="formData.customOrgIds"
            :data="orgTree"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            multiple
            check-strictly
            clearable
            :placeholder="t('system.role.customOrgIdsPlaceholder')"
            class="w-full"
          />
        </div>
      </div>
    </div>
    <template #footer>
      <ElButton @click="visible = false">{{ t('common.cancel') }}</ElButton>
      <ElButton type="primary" v-ripple @click="handleSubmit">
        {{ t('common.confirm') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
  import {
    fetchAssignRolePermissions,
    fetchPermissionTree,
    fetchRolePermissions
  } from '@/api/role'
  import { fetchOrgTree } from '@/api/org'
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'AssignDialog' })

  const emit = defineEmits<{ success: [] }>()

  const { t, te } = useI18n()

  const visible = defineModel<boolean>('visible', { default: false })

  const loading = ref(false)
  const role = ref<Api.Role.RoleListItem>()
  const formData = ref<Record<string, any>>({ dataScope: 1, customOrgIds: [] })

  /** 权限树原始数据 */
  const permissionTree = ref<Api.Role.PermissionTreeNode[]>([])
  /** 自定义部门范围树 */
  const orgTree = ref<Api.Org.OrgTreeNode[]>([])

  /** 接口权限已勾选集合（矩阵形态，直接维护 id 集合） */
  const apiChecked = ref<Set<number>>(new Set())

  /** 递归展平权限树（接口权限可能挂在菜单节点之下，需全量收集） */
  const flattenNodes = (nodes: Api.Role.PermissionTreeNode[]): Api.Role.PermissionTreeNode[] =>
    (nodes || []).flatMap((node) => [node, ...flattenNodes(node.children || [])])

  /** 菜单权限：只保留菜单节点本身（其下的接口权限归入下方矩阵，避免同一权限在两处重复出现） */
  const menuNodes = computed(() =>
    permissionTree.value.filter((node) => node.type === 'menu').map((node) => ({ ...node, children: [] }))
  )
  /** 接口权限：整棵树里的全部 api 节点（含挂在菜单下的），交给矩阵按模块分组展示 */
  const apiNodes = computed(() => flattenNodes(permissionTree.value).filter((node) => node.type === 'api'))
  /** 数据权限：同样全量收集 */
  const dataNodes = computed(() => flattenNodes(permissionTree.value).filter((node) => node.type === 'data'))

  /** 分组展示顺序（未列出的前缀排在后面，按字母序） */
  const GROUP_ORDER = ['module', 'topic', 'project', 'task', 'doc', 'dict', 'system', 'log']

  /** 分组标题：有对应文案用文案，没有则回退为前缀本身 */
  const groupLabel = (key: string) => {
    const i18nKey = `system.role.permGroup.${key}`
    return te(i18nKey) ? t(i18nKey) : key
  }

  /** 接口权限按 code 前缀分组的矩阵数据 */
  const apiGroups = computed(() => {
    const map = new Map<string, { id: number; name: string }[]>()
    for (const node of apiNodes.value) {
      const key = String(node.code || '').split(':')[0] || 'other'
      if (!map.has(key)) {
        map.set(key, [])
      }
      map.get(key)!.push({ id: node.id, name: node.name })
    }
    const keys = [...map.keys()].sort((a, b) => {
      const ia = GROUP_ORDER.indexOf(a)
      const ib = GROUP_ORDER.indexOf(b)
      if (ia === -1 && ib === -1) return a.localeCompare(b)
      if (ia === -1) return 1
      if (ib === -1) return -1
      return ia - ib
    })
    return keys.map((key) => {
      const items = map.get(key)!
      const ids = items.map((i) => i.id)
      const hit = ids.filter((id) => apiChecked.value.has(id)).length
      return {
        key,
        label: groupLabel(key),
        items,
        ids,
        allChecked: hit === ids.length,
        someChecked: hit > 0
      }
    })
  })

  // ElTree 是全局注册组件（模板可直接用），脚本里用结构化类型声明 ref
  const menuTreeRef = useTemplateRef<{
    getCheckedKeys: (leafOnly?: boolean) => number[]
    getHalfCheckedKeys: () => number[]
    setCheckedKeys: (keys: number[]) => void
  }>('menuTreeRef')
  const dataTreeRef = useTemplateRef<{
    getCheckedKeys: (leafOnly?: boolean) => number[]
    getHalfCheckedKeys: () => number[]
    setCheckedKeys: (keys: number[]) => void
  }>('dataTreeRef')

  const dataScopeOptions = computed(() => [
    { label: t('system.role.scopeAll'), value: 1 },
    { label: t('system.role.scopeOrgAndChildren'), value: 2 },
    { label: t('system.role.scopeOrgOnly'), value: 3 },
    { label: t('system.role.scopeSelf'), value: 4 },
    { label: t('system.role.scopeSelfAndSubordinates'), value: 5 },
    { label: t('system.role.scopeCustom'), value: 6 }
  ])

  // ==================== 矩阵勾选 ====================

  const toggleItem = (id: number, checked: boolean) => {
    const next = new Set(apiChecked.value)
    if (checked) {
      next.add(id)
    } else {
      next.delete(id)
    }
    apiChecked.value = next
  }

  const toggleGroup = (ids: number[], checked: boolean) => {
    const next = new Set(apiChecked.value)
    ids.forEach((id) => (checked ? next.add(id) : next.delete(id)))
    apiChecked.value = next
  }

  const open = async (row: Api.Role.RoleListItem) => {
    role.value = row
    loading.value = true
    try {
      const [tree, permissions, orgs] = await Promise.all([
        fetchPermissionTree(),
        fetchRolePermissions({ roleId: row.id }),
        fetchOrgTree()
      ])
      permissionTree.value = tree
      orgTree.value = orgs
      formData.value = {
        dataScope: permissions.dataScope,
        customOrgIds: permissions.customOrgIds || []
      }
      const granted = new Set(permissions.permissionIds || [])
      apiChecked.value = new Set(
        flattenNodes(tree).filter((node) => node.type === 'api' && granted.has(node.id)).map((node) => node.id)
      )
      visible.value = true
      // 回显勾选（等待树渲染完成）
      await nextTick()
      menuTreeRef.value?.setCheckedKeys(permissions.permissionIds)
      dataTreeRef.value?.setCheckedKeys(permissions.permissionIds)
    } finally {
      loading.value = false
    }
  }

  const handleClosed = () => {
    formData.value = { dataScope: 1, customOrgIds: [] }
    apiChecked.value = new Set()
  }

  /** 收集勾选与半选（父级）节点：接口权限取矩阵集合，菜单/数据权限取树 */
  const collectPermissionIds = () => {
    const ids = [
      ...apiChecked.value,
      ...(menuTreeRef.value?.getCheckedKeys(false) || []),
      ...(menuTreeRef.value?.getHalfCheckedKeys() || []),
      ...(dataTreeRef.value?.getCheckedKeys(false) || []),
      ...(dataTreeRef.value?.getHalfCheckedKeys() || [])
    ]
    return [...new Set(ids)]
  }

  const handleSubmit = async () => {
    if (!role.value) return
    await fetchAssignRolePermissions({
      roleId: role.value.id,
      permissionIds: collectPermissionIds(),
      dataScope: formData.value.dataScope ?? 1,
      customOrgIds: formData.value.dataScope === 6 ? formData.value.customOrgIds || [] : null
    })
    ElMessage.success(t('common.operateSuccess'))
    visible.value = false
    emit('success')
  }

  defineExpose({ open })
</script>
