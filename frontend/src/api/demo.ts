/**
 * 演示模块 API（本地 Mock）
 *
 * 数据在内存中生成并过滤，模拟后端分页接口的请求 / 响应契约，
 * 供 Demo 页面验证 useTable / ArtTable / ArtSearchBar / ArtForm 的完整链路。
 *
 * @module api/demo
 * @author Statistical Intelligent Hub Team
 */

/** 部门选项（Mock 数据域值） */
const DEPTS = ['研发部', '产品部', '运营部', '市场部', '财务部', '人力资源部']

/** 角色选项（Mock 数据域值） */
const ROLES = ['admin', 'editor', 'viewer', 'auditor']

/** 姓氏池 */
const SURNAMES = ['张', '王', '李', '赵', '陈', '刘', '杨', '黄', '周', '吴']

/** 名字池 */
const GIVEN_NAMES = ['伟', '芳', '娜', '敏', '静', '磊', '军', '洋', '勇', '杰', '娟', '涛', '明', '霞', '平', '刚']

/** 线性同余伪随机数（种子固定，保证每次会话数据一致，便于验证查询结果） */
let seed = 20260909
const random = (): number => {
  seed = (seed * 9301 + 49297) % 233280
  return seed / 233280
}

/** 取 [min, max] 区间内随机整数 */
const randomInt = (min: number, max: number): number => Math.floor(random() * (max - min + 1)) + min

/** 左侧补零 */
const padStart = (value: number, length: number): string => String(value).padStart(length, '0')

/** 生成 Mock 用户列表（模块级单例，删除操作直接作用于该数组） */
const generateUsers = (): Api.Demo.UserListItem[] => {
  const users: Api.Demo.UserListItem[] = []
  const TOTAL = 86

  for (let i = 1; i <= TOTAL; i++) {
    const surname = SURNAMES[i % SURNAMES.length]
    const givenName = GIVEN_NAMES[(i * 7) % GIVEN_NAMES.length]
    // 创建时间分布在 2024-01-01 ~ 2026-08-31 之间
    const dayOffset = randomInt(0, 973)
    const baseTime = new Date(2024, 0, 1).getTime() + dayOffset * 24 * 60 * 60 * 1000
    const date = new Date(baseTime)
    const createTime = `${date.getFullYear()}-${padStart(date.getMonth() + 1, 2)}-${padStart(
      date.getDate(),
      2
    )} ${padStart(randomInt(8, 20), 2)}:${padStart(randomInt(0, 59), 2)}:${padStart(randomInt(0, 59), 2)}`

    users.push({
      id: 1000 + i,
      userName: `user${padStart(i, 3)}`,
      nickName: `${surname}${givenName}`,
      gender: randomInt(1, 2) === 1 ? '1' : '2',
      dept: DEPTS[i % DEPTS.length],
      role: ROLES[i % ROLES.length],
      age: randomInt(22, 45),
      phone: `13${padStart(randomInt(0, 99999999), 8)}`,
      email: `user${padStart(i, 3)}@example.com`,
      status: randomInt(1, 10) > 2 ? '1' : '2',
      createTime
    })
  }

  return users
}

/** Mock 用户数据源 */
const mockUsers = generateUsers()

/** 模拟网络延迟（毫秒） */
const simulateLatency = (): Promise<void> => new Promise((resolve) => setTimeout(resolve, 300))

/**
 * 分页查询用户列表
 * 支持简单查询（userName / status）与高级查询（全部字段）组合过滤
 */
export function fetchDemoUserPage(params: Api.Demo.UserSearchParams): Promise<Api.Demo.UserList> {
  return simulateLatency().then(() => {
    const {
      current = 1,
      size = 10,
      userName,
      nickName,
      gender,
      status,
      dept,
      role,
      phone,
      email,
      ageMin,
      ageMax,
      startTime,
      endTime
    } = params

    const filtered = mockUsers.filter((user) => {
      if (userName && !user.userName.toLowerCase().includes(userName.toLowerCase())) return false
      if (nickName && !user.nickName.includes(nickName)) return false
      if (gender && user.gender !== gender) return false
      if (status && user.status !== status) return false
      if (dept && user.dept !== dept) return false
      if (role && user.role !== role) return false
      if (phone && !user.phone.includes(phone)) return false
      if (email && !user.email.toLowerCase().includes(email.toLowerCase())) return false
      if (ageMin !== undefined && user.age < ageMin) return false
      if (ageMax !== undefined && user.age > ageMax) return false

      // 按创建时间的日期部分做区间比较（YYYY-MM-DD 字符串比较）
      const createDate = user.createTime.slice(0, 10)
      if (startTime && createDate < startTime) return false
      if (endTime && createDate > endTime) return false

      return true
    })

    // 默认按创建时间倒序，最新数据在前
    const sorted = [...filtered].sort((a, b) => b.createTime.localeCompare(a.createTime))

    const start = (current - 1) * size
    return {
      records: sorted.slice(start, start + size),
      current,
      size,
      total: sorted.length
    }
  })
}

/**
 * 删除用户（Mock，直接操作内存数据）
 */
export function deleteDemoUser(params: { id: number }): Promise<null> {
  return simulateLatency().then(() => {
    const index = mockUsers.findIndex((user) => user.id === params.id)
    if (index !== -1) {
      mockUsers.splice(index, 1)
    }
    return null
  })
}
