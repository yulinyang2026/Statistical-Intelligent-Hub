/**
 * API 接口类型定义模块
 *
 * 提供所有后端接口的类型定义
 *
 * ## 主要功能
 *
 * - 通用类型（分页参数、响应结构等）
 * - 认证类型（登录、用户信息等）
 * - 系统管理类型（用户、角色等）
 * - 全局命名空间声明
 *
 * ## 使用场景
 *
 * - API 请求参数类型约束
 * - API 响应数据类型定义
 * - 接口文档类型同步
 *
 * ## 注意事项
 *
 * - 在 .vue 文件使用需要在 eslint.config.mjs 中配置 globals: { Api: 'readonly' }
 * - 使用全局命名空间，无需导入即可使用
 *
 * ## 使用方式
 *
 * ```typescript
 * const params: Api.Auth.LoginParams = { userName: 'admin', password: '123456' }
 * const response: Api.Auth.UserInfo = await fetchUserInfo()
 * ```
 *
 * @module types/api/api
 * @author Statistical Intelligent Hub Team
 */

declare namespace Api {
  /** 通用类型 */
  namespace Common {
    /** 分页参数 */
    interface PaginationParams {
      /** 当前页码 */
      current: number
      /** 每页条数 */
      size: number
      /** 总条数 */
      total: number
    }

    /** 通用搜索参数 */
    type CommonSearchParams = Pick<PaginationParams, 'current' | 'size'>

    /** 分页响应基础结构 */
    interface PaginatedResponse<T = any> {
      records: T[]
      current: number
      size: number
      total: number
    }

    /** 启用状态 */
    type EnableStatus = '1' | '2'
  }

  /** 认证类型 */
  namespace Auth {
    /** 登录参数 */
    interface LoginParams {
      userName: string
      password: string
    }

    /** 登录响应 */
    interface LoginResponse {
      token: string
      refreshToken: string
    }

    /** 用户信息 */
    interface UserInfo {
      buttons: string[]
      /** 菜单型权限码（2026-09-11 新增），前端据此过滤左侧功能树 */
      menus?: string[]
      roles: string[]
      userId: number
      userName: string
      email: string
      avatar?: string
    }
  }

  /** 系统管理类型 */
  namespace SystemManage {
    /** 用户列表 */
    type UserList = Api.Common.PaginatedResponse<UserListItem>

    /** 用户列表项 */
    interface UserListItem {
      id: number
      avatar: string
      status: string
      userName: string
      userGender: string
      nickName: string
      userPhone: string
      userEmail: string
      userRoles: string[]
      createBy: string
      createTime: string
      updateBy: string
      updateTime: string
    }

    /** 用户搜索参数 */
    type UserSearchParams = Partial<
      Pick<UserListItem, 'id' | 'userName' | 'userGender' | 'userPhone' | 'userEmail' | 'status'> &
        Api.Common.CommonSearchParams
    >

    /** 角色列表 */
    type RoleList = Api.Common.PaginatedResponse<RoleListItem>

    /** 角色列表项 */
    interface RoleListItem {
      roleId: number
      roleName: string
      roleCode: string
      description: string
      enabled: boolean
      createTime: string
    }

    /** 角色搜索参数 */
    type RoleSearchParams = Partial<
      Pick<RoleListItem, 'roleId' | 'roleName' | 'roleCode' | 'description' | 'enabled'> &
        Api.Common.CommonSearchParams & {
          startTime: string | null
          endTime: string | null
        }
    >
  }

  /** 组织机构管理类型 */
  namespace Org {
    /** 部门树节点 */
    interface OrgTreeNode {
      /** 部门 ID */
      id: number
      /** 上级部门 ID，顶级为 0 */
      parentId: number
      /** 部门名称 */
      name: string
      /** 部门编码 */
      code: string
      /** 部门负责人用户 ID */
      leaderUserId: number | null
      /** 部门负责人姓名 */
      leaderName: string
      /** 同层级排序 */
      sort: number
      /** 0 停用 / 1 启用 */
      status: number
      /** 祖先路径，如 1/3/7 */
      path: string
      /** 层级深度（顶级 1） */
      level: number
      /** 在职人员数（主属 + 兼职去重） */
      userCount: number
      createTime: string
      updateTime: string
      children: OrgTreeNode[]
    }

    /** 部门新增/修改入参（id 为空 = 新增） */
    interface OrgSaveParams {
      id?: number
      parentId: number
      name: string
      code: string
      leaderUserId?: number | null
      sort?: number
      status?: number
    }

    /** 人员批量转移入参 */
    interface OrgTransferParams {
      fromOrgId: number
      toOrgId: number
    }
  }

  /** 基础数据（枚举字典）类型 */
  namespace Dict {
    /** 字典列表项 */
    interface DictListItem {
      id: number
      code: string
      name: string
      description: string
      /** 1=内置（不可删） */
      isBuiltin: number
      status: number
      sort: number
      /** 字段数 */
      fieldCount: number
      /** 启用枚举项数 */
      itemCount: number
      createTime: string
      updateTime: string
    }

    /** 字典分页响应 */
    type DictList = Api.Common.PaginatedResponse<DictListItem>

    /** 字典搜索参数 */
    interface DictSearchParams extends Api.Common.CommonSearchParams {
      name?: string
      code?: string
      status?: number
    }

    /** 字典新增/修改入参（编码仅新增时可设） */
    interface DictSaveParams {
      id?: number
      code: string
      name: string
      description?: string
      sort?: number
    }

    /** 字典字段定义 */
    interface DictField {
      id: number
      dictId: number
      fieldCode: string
      fieldName: string
      /** string/enum（本期） */
      fieldType: string
      isRequired: number
      isUnique: number
      /** 1=系统默认字段（code/name） */
      isSystem: number
      /** enum 类型的可选项 */
      options: string[] | null
      defaultValue: string
      sort: number
      status: number
      createTime: string
      updateTime: string
    }

    /** 字典字段新增/修改入参（字段编码仅新增时可设） */
    interface DictFieldSaveParams {
      id?: number
      dictId: number
      fieldCode: string
      fieldName: string
      fieldType: string
      isRequired?: number
      isUnique?: number
      options?: string[] | null
      defaultValue?: string
      sort?: number
    }

    /** 字典枚举项 */
    interface DictItem {
      id: number
      dictId: number
      code: string
      name: string
      /** 扩展字段值 */
      ext: Record<string, any> | null
      sort: number
      status: number
      createTime: string
      updateTime: string
      deleted: number
    }

    /** 字典项新增/修改入参（code 仅新增时可设） */
    interface DictItemSaveParams {
      id?: number
      dictId: number
      code: string
      name: string
      ext?: Record<string, any> | null
      sort?: number
    }
  }

  /** 任务管理类型 */
  namespace Task {
    /** 任务列表项（两级树） */
    interface TaskListItem {
      id: string
      title: string
      desc: string
      projectId: string | null
      moduleId: string | null
      topicId: string | null
      subsystemId: string | null
      assignee: string
      priority: string
      deadline: string
      hours: number | null
      week: number | null
      planDate: string | null
      status: string
      group: string | null
      parentId: string | null
      /** 派生：项目·xxx / 模块·xxx / 专题·xxx */
      belongLabel: string | null
      subsystemName: string | null
      childCount: number
      children: TaskListItem[]
      createTime: string
      updateTime: string
    }

    /** 任务分页响应 */
    type TaskList = Api.Common.PaginatedResponse<TaskListItem>

    /** 任务搜索参数 */
    interface TaskSearchParams extends Api.Common.CommonSearchParams {
      kw?: string
      projectId?: string
      moduleId?: string
      topicId?: string
      subsystemId?: string
      status?: string
      priority?: string
      assignee?: string
      group?: string
      deadline?: string
    }

    /** 归属候选值 */
    interface BelongOption {
      id: string
      name: string
      type: 'project' | 'module' | 'topic'
    }

    /** 子系统候选值 */
    interface SubsystemOption {
      id: string
      name: string
      /** 所属项目 */
      projectId: string
    }

    /** 筛选候选值 */
    interface TaskOptions {
      belongOptions: BelongOption[]
      subsystems: SubsystemOption[]
      deadlines: string[]
      statuses: string[]
      priorities: string[]
      assignees: string[]
      groups: string[]
    }

    /** 单字段更新响应（看板拖拽 / 工时双击编辑；changed=false 表示值未变化，服务端未写库未写日志） */
    interface TaskPatchResult {
      changed: boolean
    }

    /** 任务新增/修改入参 */
    interface TaskSaveParams {
      id?: string
      title: string
      desc?: string
      projectId?: string | null
      moduleId?: string | null
      topicId?: string | null
      subsystemId?: string | null
      assignee: string
      priority: string
      /** 截止月份（2026-09-09 起与计划完成时间合并，选填） */
      deadline?: string | null
      hours?: number | null
      week?: number | null
      /** 计划完成时间 YYYY-MM-DD（必填，具体到年月日） */
      planDate?: string | null
      status: string
      group?: string
      parentId?: string | null
    }
  }

  /** 模块管理类型 */
  namespace Module {
    /** 模块列表项（含派生字段） */
    interface ModuleListItem {
      id: string
      name: string
      product: string
      category: string
      dept: string
      team: string
      owner: string
      rd: string[] | null
      test: string | null
      cost: string | null
      status: string
      stakeholders: string[] | null
      desc: string | null
      /** 待办任务数（直属 ∪ 挂靠项目任务，状态 ≠ 已完成） */
      todoCount: number
      /** 挂靠到本模块的项目 */
      belongProjects: { id: string; name: string }[]
      canEdit: boolean
      createTime: string
      updateTime: string
    }

    /** 模块分页响应 */
    type ModuleList = Api.Common.PaginatedResponse<ModuleListItem>

    /** 模块搜索参数（筛选变更即自动查询） */
    interface ModuleSearchParams extends Api.Common.CommonSearchParams {
      kw?: string
      product?: string
      category?: string
      dept?: string
      team?: string
      owner?: string
      status?: string
      /** mine = 仅本人负责的模块 */
      scope?: string
    }

    /** 筛选候选值 */
    interface ModuleOptions {
      products: string[]
      categories: string[]
      statuses: string[]
      depts: string[]
      teams: string[]
      owners: string[]
    }

    /** 模块成员（由角色字段 + 任务执行人自动推导，不建独立成员表） */
    interface ModuleMember {
      name: string
      roles: string[]
    }

    /** 操作日志项 */
    interface ModuleLog {
      time: string
      operator: string
      action: string
      detail: string
    }

    /** 模块详情（列表项字段 + 统计概览 + 成员 + 日志） */
    interface ModuleDetail extends ModuleListItem {
      totalTaskCount: number
      doneTaskCount: number
      todoCount: number
      /** 完成率（0~100 整数） */
      completionRate: number
      docCount: number
      members: ModuleMember[]
      logs: ModuleLog[]
    }

    /** 模块新增/修改入参 */
    interface ModuleSaveParams {
      id?: string
      name: string
      product: string
      category: string
      dept: string
      team: string
      owner: string
      rd?: string[]
      test?: string
      cost?: string
      /** 状态由任务待办自动流转，表单不再填写（2026-09-11）：不传则新建默认「非活跃」、编辑保持原值 */
      status?: string
      stakeholders?: string[]
      desc?: string
    }

    /** 删除响应（级联删除任务数 / 文档数） */
    interface ModuleDeleteResult {
      tasks: number
      docs: number
    }

    /** 导入结果 */
    interface ModuleImportResult {
      created: number
      skipped: number
    }
  }

  /** 专题管理类型（06-专题管理；与模块管理同构，差异：无归属产品 / 模块分类） */
  namespace Topic {
    /** 专题列表项（含派生字段） */
    interface TopicListItem {
      id: string
      name: string
      dept: string
      team: string
      owner: string
      rd: string[] | null
      test: string | null
      cost: string | null
      status: string
      stakeholders: string[] | null
      desc: string | null
      /** 待办任务数（直属任务，状态 ≠ 已完成） */
      todoCount: number
      canEdit: boolean
      createTime: string
      updateTime: string
    }

    /** 专题分页响应 */
    type TopicList = Api.Common.PaginatedResponse<TopicListItem>

    /** 专题搜索参数（筛选变更即自动查询） */
    interface TopicSearchParams extends Api.Common.CommonSearchParams {
      kw?: string
      dept?: string
      team?: string
      owner?: string
      status?: string
      /** mine = 仅本人负责的专题 */
      scope?: string
    }

    /** 筛选候选值 */
    interface TopicOptions {
      statuses: string[]
      depts: string[]
      teams: string[]
      owners: string[]
    }

    /** 专题成员（由角色字段自动推导，不建独立成员表） */
    interface TopicMember {
      name: string
      roles: string[]
    }

    /** 操作日志项 */
    interface TopicLog {
      time: string
      operator: string
      action: string
      detail: string
    }

    /** 专题详情（列表项字段 + 统计概览 + 成员 + 日志） */
    interface TopicDetail extends TopicListItem {
      totalTaskCount: number
      doneTaskCount: number
      todoCount: number
      /** 完成率（0~100 整数） */
      completionRate: number
      docCount: number
      members: TopicMember[]
      logs: TopicLog[]
    }

    /** 专题新增/修改入参 */
    interface TopicSaveParams {
      id?: string
      name: string
      dept: string
      team: string
      owner: string
      rd?: string[]
      test?: string
      cost?: string
      /** 状态由任务待办自动流转，表单不再填写（2026-09-11）：不传则新建默认「非活跃」、编辑保持原值 */
      status?: string
      stakeholders?: string[]
      desc?: string
    }

    /** 删除响应（级联删除任务数 / 文档数） */
    interface TopicDeleteResult {
      tasks: number
      docs: number
    }

    /** 导入结果 */
    interface TopicImportResult {
      created: number
      skipped: number
    }
  }

  /** 文档中心类型 */
  namespace Doc {
    /** 文档列表项 */
    interface DocListItem {
      id: string
      title: string
      projectId: string | null
      moduleId: string | null
      topicId: string | null
      /** 派生：项目·xxx / 模块·xxx / 专题·xxx */
      belongLabel: string | null
      /** 摘要（正文前 40 字） */
      summary: string
      updatedBy: string | null
      updatedAt: string | null
      wordCount: number
      canEdit: boolean
      canDelete: boolean
    }

    /** 文档分页响应 */
    type DocList = Api.Common.PaginatedResponse<DocListItem>

    /** 文档搜索参数 */
    interface DocSearchParams extends Api.Common.CommonSearchParams {
      kw?: string
      projectId?: string
      moduleId?: string
      topicId?: string
    }

    /** 文档详情（含正文） */
    interface DocDetail extends DocListItem {
      content: string
    }

    /** 文档新增/修改入参（归属三选一必填其一） */
    interface DocSaveParams {
      id?: string
      title: string
      projectId?: string | null
      moduleId?: string | null
      topicId?: string | null
      content?: string
    }
  }

  /** 用户管理类型 */
  namespace User {
    /** 用户-部门归属项 */
    interface UserOrgItem {
      orgId: number
      orgName: string
      isPrimary: boolean
    }

    /** 用户列表项 */
    interface UserListItem {
      id: number
      name: string
      employeeNo: string
      mobile: string
      email: string
      managerUserId: number | null
      managerName: string
      status: number
      username: string
      /** 部门归属（主属在前） */
      orgs: UserOrgItem[]
      roleNames: string[]
      /** 是否为其所属机构的负责人（组织管理页「身份」列，2026-09-11） */
      orgLeader: boolean
      createTime: string
    }

    /** 用户分页响应 */
    type UserList = Api.Common.PaginatedResponse<UserListItem>

    /** 用户搜索参数 */
    interface UserSearchParams extends Api.Common.CommonSearchParams {
      name?: string
      employeeNo?: string
      mobile?: string
      status?: number
      orgId?: number
      roleId?: number
    }

    /** 用户详情（表单回显） */
    interface UserDetail {
      id: number
      name: string
      employeeNo: string
      mobile: string
      email: string
      managerUserId: number | null
      status: number
      username: string
      orgs: UserOrgItem[]
      orgIds: number[]
      primaryOrgId: number | null
      roleIds: number[]
      createTime: string
    }

    /** 用户新增/修改入参（id 为空 = 新增；修改时 password 留空不变更） */
    interface UserSaveParams {
      id?: number
      name: string
      employeeNo: string
      mobile: string
      email?: string
      managerUserId?: number | null
      status?: number
      orgIds: number[]
      primaryOrgId: number
      username: string
      password?: string
      roleIds: number[]
      /** 组织管理页新增时的创建属性：true=机构管理者（同时写为机构负责人）/ false=下级成员（主管默认取机构负责人） */
      asOrgLeader?: boolean | null
    }

    /** 用户下拉选项 */
    interface UserOption {
      id: number
      name: string
      employeeNo: string
    }

    /** 重置密码入参 */
    interface ResetPasswordParams {
      userId: number
      password: string
    }

    /** 启停入参 */
    interface StatusParams {
      userId: number
      status: number
    }

    /** 个人中心：本人基本信息（含部门/角色名称与头像） */
    interface UserProfile {
      userId: number
      name: string
      employeeNo: string | null
      mobile: string | null
      email: string | null
      /** 头像 data URL（空 = 使用系统默认头像） */
      avatar: string
      status: number | null
      /** 登录账号 */
      username: string
      orgNames: string[]
      roleNames: string[]
      createTime: string | null
    }
  }

  /** 系统级操作日志类型（2026-09-11 用户需求） */
  namespace Log {
    /** 操作日志项 */
    interface LogItem {
      id: number
      /** 操作时间 YYYY-MM-DD HH:mm:ss */
      time: string
      /** 操作人（中文姓名） */
      operator: string
      /** 模块标识（task / module / project / doc / menu / user / topic） */
      module: string
      /** 操作对象（如「智慧统计项目(p1)」） */
      target: string
      /** 动作（新增/编辑/删除/状态自动流转…） */
      action: string
      /** 详情（旧值 → 新值等） */
      detail: string
    }

    /** 操作日志搜索参数 */
    interface LogSearchParams extends Api.Common.CommonSearchParams {
      operator?: string
      module?: string
      action?: string
      keyword?: string
      startTime?: string
      endTime?: string
      /** 表头漏斗通用筛选（JSON） */
      filters?: string
    }
  }

  /** 角色管理类型 */
  namespace Role {
    /** 角色列表项 */
    interface RoleListItem {
      id: number
      name: string
      code: string
      /** 1 内置（不可删）/ 2 自定义 */
      type: number
      /** 数据范围：1 全部 / 2 本组织及下级 / 3 仅本组织 / 4 仅本人 / 5 本人+直属下属 / 6 自定义部门 */
      dataScope: number
      customOrgIds: number[] | null
      remark: string
      status: number
      /** 挂靠用户数 */
      userCount: number
      createTime: string
      updateTime: string
    }

    /** 角色分页响应 */
    type RoleList = Api.Common.PaginatedResponse<RoleListItem>

    /** 角色搜索参数 */
    interface RoleSearchParams extends Api.Common.CommonSearchParams {
      name?: string
      code?: string
      status?: number
    }

    /** 角色下拉选项 */
    interface RoleOption {
      id: number
      name: string
      code: string
    }

    /** 角色新增/修改入参 */
    interface RoleSaveParams {
      id?: number
      name: string
      code: string
      dataScope?: number
      customOrgIds?: number[] | null
      remark?: string
      status?: number
    }

    /** 权限树节点 */
    interface PermissionTreeNode {
      id: number
      code: string
      name: string
      /** menu / api / data */
      type: string
      parentId: number
      sort: number
      children: PermissionTreeNode[]
    }

    /** 角色已分配权限（回显） */
    interface RolePermissionResponse {
      roleId: number
      permissionIds: number[]
      dataScope: number
      customOrgIds: number[] | null
    }

    /** 角色授权入参 */
    interface RoleAssignPermissionParams {
      roleId: number
      permissionIds: number[]
      dataScope: number
      customOrgIds?: number[] | null
    }
  }

  /** 演示模块类型 */
  namespace Demo {
    /** 用户列表项 */
    interface UserListItem {
      /** 用户 ID */
      id: number
      /** 用户名 */
      userName: string
      /** 昵称 */
      nickName: string
      /** 性别：1 男 / 2 女 */
      gender: '1' | '2'
      /** 部门 */
      dept: string
      /** 角色 */
      role: string
      /** 年龄 */
      age: number
      /** 手机号 */
      phone: string
      /** 邮箱 */
      email: string
      /** 状态：1 启用 / 2 禁用 */
      status: '1' | '2'
      /** 创建时间 YYYY-MM-DD HH:mm:ss */
      createTime: string
    }

    /** 用户列表（分页响应） */
    type UserList = Api.Common.PaginatedResponse<UserListItem>

    /** 用户搜索参数（简单 + 高级查询字段） */
    interface UserSearchParams extends Api.Common.CommonSearchParams {
      /** 用户名（模糊） */
      userName?: string
      /** 昵称（模糊） */
      nickName?: string
      /** 性别（精确） */
      gender?: string
      /** 状态（精确） */
      status?: string
      /** 部门（精确） */
      dept?: string
      /** 角色（精确） */
      role?: string
      /** 手机号（模糊） */
      phone?: string
      /** 邮箱（模糊） */
      email?: string
      /** 最小年龄（含） */
      ageMin?: number
      /** 最大年龄（含） */
      ageMax?: number
      /** 创建时间起 YYYY-MM-DD（含） */
      startTime?: string
      /** 创建时间止 YYYY-MM-DD（含） */
      endTime?: string
    }
  }

  /** 管理小帮手（AI 对话） */
  namespace Ai {
    /** 会话列表项 */
    interface ChatSessionItem {
      /** 会话 id */
      id: number
      /** 会话标题 */
      title: string
      /** 最近更新时间 */
      updateTime: string
    }

    /** 消息列表项 */
    interface ChatMessageItem {
      /** 消息 id */
      id: number
      /** user=用户提问 assistant=AI 回答 */
      role: 'user' | 'assistant'
      /** 消息内容 */
      content: string
      /** 发送时间 */
      createTime: string
    }
  }

  /** 项目管理类型 */
  namespace Project {
    /** 子系统行（列表树形 children / 详情「子系统」Tab 同源，含任务统计） */
    interface SubsystemItem {
      nodeType: 'subsystem'
      id: string
      name: string
      projectId: string
      projectName: string
      owner: string | null
      sort: number
      /** 2026-09-11 用户需求：子系统字段与项目一致 */
      cost: string | null
      product: string | null
      dept: string | null
      team: string | null
      rd: string | null
      pm: string | null
      test: string | null
      status: string | null
      /** 任务总数 */
      taskCount: number
      /** 待办任务数（状态 ≠ 已完成） */
      todoCount: number
      /** 完成率（0~100 整数） */
      completionRate: number
      canEdit: boolean
      createTime?: string | null
      updateTime?: string | null
    }

    /** 项目列表项（含派生字段与子系统 children） */
    interface ProjectListItem {
      nodeType: 'project'
      id: string
      name: string
      cost: string | null
      level: string | null
      product: string | null
      productLine: string | null
      dept: string | null
      team: string | null
      sale: string | null
      pm: string | null
      owner: string | null
      rd: string | null
      test: string | null
      status: string
      budget: number | null
      startDate: string | null
      endDate: string | null
      stakeholders: string[] | null
      desc: string | null
      /** 任务总数（含各子系统任务） */
      taskCount: number
      /** 待办任务数（状态 ≠ 已完成，含各子系统任务） */
      todoCount: number
      subsystemCount: number
      canEdit: boolean
      createTime: string
      updateTime: string
      /** 子系统（树形 children） */
      children?: SubsystemItem[]
    }

    /** 项目分页响应 */
    type ProjectList = Api.Common.PaginatedResponse<ProjectListItem>

    /** 项目搜索参数（筛选变更即自动查询） */
    interface ProjectSearchParams extends Api.Common.CommonSearchParams {
      /** 项目名称 / 成本对象模糊 */
      kw?: string
      product?: string
      status?: string
      owner?: string
      level?: string
      /** mine = 仅本人负责的项目 */
      scope?: string
    }

    /** 筛选候选值 + 挂靠对象候选 */
    interface ProjectOptions {
      products: string[]
      statuses: string[]
      levels: string[]
      depts: string[]
      teams: string[]
      owners: string[]
    }

    /** 项目成员（角色字段 + 任务执行人自动推导） */
    interface ProjectMember {
      name: string
      roles: string[]
    }

    /** 操作日志项 */
    interface ProjectLog {
      time: string
      operator: string
      action: string
      detail: string
    }

    /** 项目详情（列表项字段 + 统计概览 + 子系统 + 成员 + 日志）
        2026-09-11：统计概览（待办/总数/已完成/完成率）改为**仅直属任务**口径，与子系统待办相互独立；
        原「未关联子系统的任务/待办」派生字段随提示一并下线 */
    interface ProjectDetail extends ProjectListItem {
      totalTaskCount: number
      doneTaskCount: number
      completionRate: number
      docCount: number
      subsystems: SubsystemItem[]
      members: ProjectMember[]
      logs: ProjectLog[]
    }

    /** 项目新增/修改入参 */
    interface ProjectSaveParams {
      id?: string
      name: string
      cost: string
      level: string
      product: string
      productLine?: string | null
      dept: string
      team: string
      sale?: string | null
      pm?: string | null
      owner: string
      rd?: string | null
      test?: string | null
      status: string
      budget?: number | null
      startDate?: string | null
      endDate?: string | null
      stakeholders?: string[]
      desc?: string | null
    }

    /** 删除响应（级联数量） */
    interface ProjectDeleteResult {
      subsystems: number
      tasks: number
      docs: number
    }

    /** 导入结果 */
    interface ProjectImportResult {
      created: number
      skipped: number
    }

    /** 子系统新增/修改入参 */
    interface SubsystemSaveParams {
      id?: string
      projectId?: string
      name: string
      owner?: string | null
      sort?: number | null
      /** 2026-09-11 用户需求：子系统表单扩字段 */
      cost?: string | null
      product?: string | null
      dept?: string | null
      team?: string | null
      rd?: string | null
      pm?: string | null
      test?: string | null
      status?: string | null
    }

    /** 子系统删除响应（受影响任务数） */
    interface SubsystemDeleteResult {
      tasks: number
    }
  }
}
