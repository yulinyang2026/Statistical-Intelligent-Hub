# -*- coding: utf-8 -*-
"""
端到端功能测试用例（API 级）—— 2026-09-11

覆盖范围：登录鉴权 / 组织 / 角色 / 用户 / 权限判定 / 模块 / 专题 / 项目与子系统 /
          任务 / 文档 / 待办口径与状态自动流转 / Excel 导入导出 / 数据可见性 /
          个人中心 / 操作日志 / 菜单顺序 / 级联删除 / 异常入参回归。

响应约定（本工程）：
  · 业务错误 = HTTP 200 + body.code（400 参数/业务校验、403 无权限）
  · 未登录   = HTTP 401（拦截器置真实状态码）
  · 未预期异常 = HTTP 500
  因此断言一律看 body.code，仅在断言 401/500 时才看 HTTP 状态码。

用法：python scripts/e2e-test.py [--base http://localhost:8091] [--keep]
"""
import argparse
import io
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid


class Runner:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.results = []
        self.section = ""
        self.counter = 0
        self.token = None
        self.leftover = {}

    # ---------- HTTP ----------
    def call(self, method, path, body=None, token=None, raw=False, params=None):
        url = self.base + path
        if params:
            url += ("&" if "?" in url else "?") + urllib.parse.urlencode(params)
        data = None
        headers = {"Accept": "application/json"}
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json; charset=UTF-8"
        if token:
            headers["Authorization"] = "Bearer " + token
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req) as resp:
                payload = resp.read()
                if raw:
                    return resp.status, payload, dict(resp.headers)
                return resp.status, json.loads(payload.decode("utf-8")), dict(resp.headers)
        except urllib.error.HTTPError as e:
            payload = e.read()
            if raw:
                return e.code, payload, dict(e.headers)
            try:
                return e.code, json.loads(payload.decode("utf-8")), dict(e.headers)
            except Exception:
                return e.code, {"raw": payload.decode("utf-8", "replace")}, dict(e.headers)

    def login(self, username, password):
        _, body, _ = self.call("POST", "/api/auth/login",
                               {"userName": username, "password": password})
        return body.get("data", {}).get("token") if body.get("code") == 200 else None

    def upload(self, path, filename, content, token):
        boundary = "----E2E" + uuid.uuid4().hex
        body = b""
        body += ("--%s\r\n" % boundary).encode()
        body += ('Content-Disposition: form-data; name="file"; filename="%s"\r\n' % filename).encode()
        body += b"Content-Type: text/csv\r\n\r\n"
        body += content
        body += ("\r\n--%s--\r\n" % boundary).encode()
        req = urllib.request.Request(self.base + path, data=body, method="POST", headers={
            "Content-Type": "multipart/form-data; boundary=%s" % boundary,
            "Authorization": "Bearer " + token,
        })
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode("utf-8"))

    # ---------- 断言 ----------
    def section_start(self, name):
        self.section = name
        print("\n" + "=" * 78)
        print("## " + name)
        print("=" * 78)

    def check(self, name, ok, detail=""):
        self.counter += 1
        self.results.append((self.counter, "%s / %s" % (self.section, name), bool(ok), detail))
        print("%s%3d. %s%s" % ("  ok " if ok else "FAIL ", self.counter, name,
                               ("   <- " + detail) if (detail and not ok) else ""))

    def code(self, res, name, want, note=""):
        """业务码断言：HTTP 200 + body.code == want（业务错误约定）"""
        status, body, _ = res
        got = (body or {}).get("code")
        d = "http=%s code=%s msg=%s" % (status, got, (body or {}).get("msg"))
        if note:
            d = note + " | " + d
        self.check(name, got == want, d)
        return body

    def bad(self, res, name):
        """断言业务失败（code != 200）"""
        status, body, _ = res
        got = (body or {}).get("code")
        self.check(name, got != 200, "http=%s code=%s msg=%s" % (status, got, (body or {}).get("msg")))
        return body

    def eq(self, name, actual, expected):
        self.check(name, actual == expected, "expected=%r actual=%r" % (expected, actual))

    def summary(self):
        total = len(self.results)
        failed = [r for r in self.results if not r[2]]
        print("\n" + "=" * 78)
        print("测试汇总：共 %d 项，通过 %d，失败 %d" % (total, total - len(failed), len(failed)))
        print("=" * 78)
        for num, name, _, detail in failed:
            print("  FAIL %3d. %s\n           %s" % (num, name, detail))
        return len(failed)


# ==================== 工具 ====================

def _org_named(nodes, org_id, run_id):
    """组织树中该 id 的节点名称是否带本次运行前缀（删除前的归属确认）"""
    for n in nodes or []:
        if n.get("id") == org_id:
            return str(n.get("name") or "").startswith(run_id)
        if _org_named(n.get("children"), org_id, run_id):
            return True
    return False


def find_node(nodes, name):
    for n in nodes or []:
        if n.get("name") == name:
            return n
        found = find_node(n.get("children"), name)
        if found:
            return found
    return None


def flatten_tasks(records):
    """任务列表（父行内嵌子任务）→ 扁平列表"""
    out = []
    for r in records or []:
        out.append(r)
        out.extend(flatten_tasks(r.get("children")))
    return out


def collect_permissions(nodes, out=None):
    out = {} if out is None else out
    for it in nodes or []:
        if it.get("code"):
            out[it["code"]] = it.get("id")
        collect_permissions(it.get("children"), out)
    return out


# ==================== 测试主体 ====================

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8091")
    ap.add_argument("--keep", action="store_true")
    args = ap.parse_args()

    t = Runner(args.base)
    R = "E2E"

    # ---------------------------------------------------------- 0 登录鉴权
    t.section_start("0. 登录与鉴权")
    body = t.code(t.call("POST", "/api/auth/login", {"userName": "admin", "password": "123456"}),
                  "admin 登录成功", 200)
    t.token = (body.get("data") or {}).get("token")
    t.check("登录返回 token", bool(t.token))
    t.bad(t.call("POST", "/api/auth/login", {"userName": "no_such_user", "password": "x"}),
          "不存在的账号被拒绝")
    status, _, _ = t.call("GET", "/api/modules")
    t.eq("无 token 访问受保护接口 → HTTP 401", status, 401)

    # 注意：密码错误累计 5 次会锁定账号 10 分钟（AuthService.MAX_FAIL_COUNT），
    # 故「错误密码」类用例一律用一次性账号（在「3. 用户管理」中创建），绝不消耗 admin 的失败计数
    body = t.code(t.call("GET", "/api/user/info", token=t.token), "获取登录用户信息", 200)
    info = body.get("data") or {}
    t.check("用户信息含 R_SUPER 角色", "R_SUPER" in (info.get("roles") or []), "roles=%s" % info.get("roles"))
    t.check("用户信息含权限码 buttons", len(info.get("buttons") or []) > 0,
            "buttons=%d" % len(info.get("buttons") or []))

    # ---------------------------------------------------------- 1 组织
    t.section_start("1. 组织管理（部门 / 小组）")
    org_root = org_dept = org_dept2 = org_team = None

    t.code(t.call("POST", "/api/system/org", {
        "parentId": 0, "name": "%s总公司" % R, "code": "%s_ROOT" % R, "sort": 1, "status": 1}, t.token),
        "创建根组织（parentId=0）", 200)
    t.code(t.call("POST", "/api/system/org", {
        "parentId": 0, "name": "%s非法编码" % R, "code": "bad code!", "sort": 2, "status": 1}, t.token),
        "非法部门编码被拒绝", 400)
    t.code(t.call("POST", "/api/system/org", {
        "parentId": 0, "name": "", "code": "%s_BAD" % R, "sort": 2, "status": 1}, t.token),
        "部门名称必填校验", 400)

    body = t.code(t.call("GET", "/api/system/org/tree", token=t.token), "组织树接口", 200)
    root_node = find_node(body.get("data"), "%s总公司" % R)
    t.check("组织树包含新建根组织", root_node is not None)
    if root_node:
        org_root = root_node["id"]

    if org_root:
        t.code(t.call("POST", "/api/system/org", {
            "parentId": org_root, "name": "%s研发中心" % R, "code": "%s_RD" % R, "sort": 1, "status": 1}, t.token),
            "创建二级部门", 200)
        t.code(t.call("POST", "/api/system/org", {
            "parentId": org_root, "name": "%s产品中心" % R, "code": "%s_PD" % R, "sort": 2, "status": 1}, t.token),
            "创建第二个二级部门", 200)

    body = t.code(t.call("GET", "/api/system/org/tree", token=t.token), "组织树刷新", 200)
    n1 = find_node(body.get("data"), "%s研发中心" % R)
    n2 = find_node(body.get("data"), "%s产品中心" % R)
    org_dept, org_dept2 = (n1 or {}).get("id"), (n2 or {}).get("id")
    t.check("二级部门创建成功且层级正确（level=2）",
            n1 and n2 and n1.get("level") == 2 and n2.get("level") == 2,
            "n1.level=%s n2.level=%s" % ((n1 or {}).get("level"), (n2 or {}).get("level")))
    t.check("二级部门挂在根组织下", n1 and n1.get("parentId") == org_root,
            "parentId=%s" % (n1 or {}).get("parentId"))

    if org_dept:
        t.code(t.call("POST", "/api/system/org", {
            "parentId": org_dept, "name": "%s前端组" % R, "code": "%s_FE" % R, "sort": 1, "status": 1}, t.token),
            "创建三级小组", 200)

    body = t.code(t.call("GET", "/api/system/org/tree", token=t.token), "组织树刷新（三级）", 200)
    n3 = find_node(body.get("data"), "%s前端组" % R)
    org_team = (n3 or {}).get("id")
    t.check("三级小组存在且 level=3", n3 and n3.get("level") == 3, "level=%s" % (n3 or {}).get("level"))

    if org_dept:
        t.code(t.call("PUT", "/api/system/org", {
            "id": org_dept, "parentId": org_root, "name": "%s研发中心(改)" % R,
            "code": "%s_RD" % R, "sort": 1, "status": 1}, t.token), "编辑部门（改名）", 200)
        body = t.call("GET", "/api/system/org/tree", token=t.token)[1]
        t.check("部门改名生效", find_node(body.get("data"), "%s研发中心(改)" % R) is not None)
        t.call("PUT", "/api/system/org", {
            "id": org_dept, "parentId": org_root, "name": "%s研发中心" % R,
            "code": "%s_RD" % R, "sort": 1, "status": 1}, t.token)
        body = t.call("GET", "/api/system/org/tree", token=t.token)[1]
        t.check("部门名恢复", find_node(body.get("data"), "%s研发中心" % R) is not None)

    # ---------------------------------------------------------- 2 角色
    t.section_start("2. 角色管理与授权")
    role_id = role_none_id = None
    role_code = role_none_code = None

    t.code(t.call("POST", "/api/system/role", {
        "name": "%s测试角色" % R, "code": "%s_ROLE" % R, "dataScope": 1,
        "remark": "E2E", "status": 1}, t.token), "创建角色", 200)
    t.code(t.call("POST", "/api/system/role", {
        "name": "%s空权限角色" % R, "code": "%s_NOPERM" % R, "dataScope": 5,
        "remark": "E2E", "status": 1}, t.token), "创建空权限角色", 200)
    t.code(t.call("POST", "/api/system/role", {
        "name": "%s非法编码" % R, "code": "bad code", "dataScope": 1, "status": 1}, t.token),
        "非法角色编码被拒绝", 400)

    body = t.code(t.call("GET", "/api/system/role/page", token=t.token,
                         params={"current": 1, "size": 50, "name": R}), "角色分页检索", 200)
    for r0 in (body.get("data") or {}).get("records") or []:
        # 只认「本次运行创建的角色」：code 必须精确等于测试前缀，避免误认系统角色
        if r0.get("code") == "%s_ROLE" % R:
            role_id = r0["id"]
            role_code = r0.get("code")
        if r0.get("code") == "%s_NOPERM" % R:
            role_none_id = r0["id"]
            role_none_code = r0.get("code")
    t.check("角色列表检索到两个新建角色", bool(role_id and role_none_id),
            "role_id=%s role_none_id=%s" % (role_id, role_none_id))

    body = t.code(t.call("GET", "/api/system/role/permission-tree", token=t.token), "权限树接口", 200)
    perm = collect_permissions(body.get("data"))
    t.check("权限树含权限点", len(perm) > 0, "count=%d" % len(perm))

    want_codes = ["module:view", "module:edit", "topic:view", "topic:edit", "project:view",
                  "project:edit", "task:view", "task:create", "task:edit", "doc:view", "doc:edit"]
    target = [perm[c] for c in want_codes if c in perm]
    if role_id:
        t.code(t.call("PUT", "/api/system/role/permissions", {
            "roleId": role_id, "permissionIds": target, "dataScope": 1}, t.token), "给角色分配权限", 200)
        body = t.code(t.call("GET", "/api/system/role/%d/permissions" % role_id, token=t.token),
                      "角色权限回读", 200)
        got = set((body.get("data") or {}).get("permissionIds") or [])
        t.check("权限回读与分配一致", got == set(target),
                "want=%d got=%d" % (len(target), len(got)))
    if role_none_id:
        t.code(t.call("PUT", "/api/system/role/permissions", {
            "roleId": role_none_id, "permissionIds": [], "dataScope": 5}, t.token),
            "空权限角色清空权限", 200)

    # ---------------------------------------------------------- 3 用户
    t.section_start("3. 用户管理（建档 / 组织 / 角色 / 密码）")
    u_main = u_noperm = u_sub = None
    lock_user = "%s_locktest" % R.lower()

    t.code(t.call("POST", "/api/system/user", {
        "name": "%s锁定测试" % R, "employeeNo": "%s009" % R, "mobile": "13900000009",
        "email": "e2e9@bsp.com", "status": 1, "orgIds": [org_dept], "primaryOrgId": org_dept,
        "username": lock_user, "password": "LockTest123", "roleIds": []}, t.token),
        "创建用户（无角色）", 200)
    t.code(t.call("POST", "/api/system/user", {
        "name": "%s缺部门" % R, "employeeNo": "%s010" % R, "mobile": "13900000010",
        "status": 1, "orgIds": [], "primaryOrgId": None,
        "username": "%s_norg" % R.lower(), "password": "NoOrg12345", "roleIds": []}, t.token),
        "用户必填主属部门校验", 400)
    t.bad(t.call("POST", "/api/auth/login", {"userName": lock_user, "password": "wrong-pass"}),
          "错误密码被拒绝")
    t.check("错误一次后正确密码仍可登录（未误锁）", t.login(lock_user, "LockTest123") is not None)

    t.code(t.call("POST", "/api/system/user", {
        "name": "%s主用户" % R, "employeeNo": "%s001" % R, "mobile": "13900000001",
        "email": "e2e1@bsp.com", "status": 1, "orgIds": [org_team] if org_team else [],
        "primaryOrgId": org_team, "username": "%s_main" % R.lower(),
        "password": "E2eTest123", "roleIds": [role_id] if role_id else []}, t.token),
        "创建用户（关联组织 + 角色）", 200)
    t.code(t.call("POST", "/api/system/user", {
        "name": "%s无权限用户" % R, "employeeNo": "%s002" % R, "mobile": "13900000002",
        "email": "e2e2@bsp.com", "status": 1, "orgIds": [org_dept] if org_dept else [],
        "primaryOrgId": org_dept, "username": "%s_noperm" % R.lower(),
        "password": "E2eTest123", "roleIds": [role_none_id] if role_none_id else []}, t.token),
        "创建无权限角色用户", 200)

    body = t.code(t.call("GET", "/api/system/user/page", token=t.token,
                         params={"current": 1, "size": 50, "name": R}), "用户分页检索", 200)
    for u in (body.get("data") or {}).get("records") or []:
        if u.get("name") == "%s主用户" % R:
            u_main = u["id"]
        if u.get("name") == "%s无权限用户" % R:
            u_noperm = u["id"]
    t.check("用户列表检索到新建用户", bool(u_main and u_noperm),
            "u_main=%s u_noperm=%s" % (u_main, u_noperm))

    if u_main:
        body = t.code(t.call("GET", "/api/system/user/%d" % u_main, token=t.token), "用户详情", 200)
        d = body.get("data") or {}
        t.check("用户详情含所属组织", org_team in (d.get("orgIds") or []), "orgIds=%s" % d.get("orgIds"))
        t.check("用户详情含角色", role_id in (d.get("roleIds") or []), "roleIds=%s" % d.get("roleIds"))

        t.code(t.call("POST", "/api/system/user", {
            "name": "%s下属用户" % R, "employeeNo": "%s003" % R, "mobile": "13900000003",
            "email": "e2e3@bsp.com", "status": 1, "orgIds": [org_team] if org_team else [],
            "primaryOrgId": org_team, "managerUserId": u_main, "username": "%s_sub" % R.lower(),
            "password": "E2eTest123", "roleIds": [role_id] if role_id else []}, t.token),
            "创建下属用户（带直属上级）", 200)
        body = t.call("GET", "/api/system/user/page", token=t.token,
                      params={"current": 1, "size": 50, "name": R})[1]
        for u in (body.get("data") or {}).get("records") or []:
            if u.get("name") == "%s下属用户" % R:
                u_sub = u["id"]
        t.check("下属用户已创建", bool(u_sub))

        t.code(t.call("PUT", "/api/system/user/reset-password", {
            "userId": u_main, "password": "Short1"}, t.token), "重置密码（弱密码）被拒绝", 400)
        t.code(t.call("PUT", "/api/system/user/reset-password", {
            "userId": u_main, "password": "NewPass12345"}, t.token), "重置密码", 200)
        t.check("新密码可登录", t.login("%s_main" % R.lower(), "NewPass12345") is not None)
        t.check("旧密码失效", t.login("%s_main" % R.lower(), "E2eTest123") is None)

        # 删除用户（2026-09-11 用户需求：物理删除）
        t.code(t.call("DELETE", "/api/system/user/%d" % 1, token=t.token),
               "删除当前登录账号被拒绝", 400)
        t.code(t.call("POST", "/api/system/user", {
            "name": "%s待删用户" % R, "employeeNo": "%s099" % R, "status": 1,
            "orgIds": [org_team] if org_team else [], "primaryOrgId": org_team,
            "username": "%s_del" % R.lower(), "password": "DelTest123", "roleIds": []}, t.token),
            "创建待删除用户", 200)
        body = t.call("GET", "/api/system/user/page", token=t.token,
                      params={"current": 1, "size": 100, "name": "%s待删用户" % R})[1]
        del_id = ((body.get("data") or {}).get("records") or [{}])[0].get("id")
        t.check("待删除用户已创建", bool(del_id))
        if del_id:
            t.check("删除前该账号可登录", t.login("%s_del" % R.lower(), "DelTest123") is not None)
            t.code(t.call("DELETE", "/api/system/user/%d" % del_id, token=t.token), "删除用户", 200)
            t.check("删除后账号无法登录", t.login("%s_del" % R.lower(), "DelTest123") is None)
            body = t.call("GET", "/api/system/user/page", token=t.token,
                          params={"current": 1, "size": 100, "name": "%s待删用户" % R})[1]
            t.eq("删除后列表无该用户", len((body.get("data") or {}).get("records") or []), 0)

        t.code(t.call("PUT", "/api/system/user/status", {"userId": u_main, "status": 0}, t.token),
               "停用用户", 200)
        t.check("停用后无法登录", t.login("%s_main" % R.lower(), "NewPass12345") is None)
        t.code(t.call("PUT", "/api/system/user/status", {"userId": u_main, "status": 1}, t.token),
               "启用用户", 200)
        t.check("启用后可登录", t.login("%s_main" % R.lower(), "NewPass12345") is not None)

    # ---------------------------------------------------------- 4 权限判定
    t.section_start("4. 权限判定与越权拦截")
    token_main = t.login("%s_main" % R.lower(), "NewPass12345")
    token_noperm = t.login("%s_noperm" % R.lower(), "E2eTest123")
    t.check("有权限用户可登录", token_main is not None)
    t.check("无权限用户可登录", token_noperm is not None)

    if token_noperm:
        t.eq("无 module:view 访问模块列表 → 403",
             t.call("GET", "/api/modules", token=token_noperm)[1].get("code"), 403)
        t.eq("无 topic:view 访问专题列表 → 403",
             t.call("GET", "/api/topics", token=token_noperm)[1].get("code"), 403)
        t.eq("无 project:view 访问项目列表 → 403",
             t.call("GET", "/api/projects", token=token_noperm)[1].get("code"), 403)
        t.eq("无 module:edit 新增模块 → 403",
             t.call("POST", "/api/modules", {"name": "x", "product": "y", "category": "z",
                                             "dept": "d", "team": "t", "owner": "o",
                                             "status": "活跃"}, token_noperm)[1].get("code"), 403)
        t.eq("无 system:org:create 新增部门 → 403",
             t.call("POST", "/api/system/org", {"parentId": 0, "name": "x", "code": "xn1",
                                                "sort": 1, "status": 1}, token_noperm)[1].get("code"), 403)
        t.eq("组织树为业务表单开放接口，登录即可读 → 200",
             t.call("GET", "/api/system/org/tree", token=token_noperm)[1].get("code"), 200)
        t.eq("无 log:view 访问操作日志 → 403",
             t.call("GET", "/api/system/log/page", token=token_noperm)[1].get("code"), 403)

    if token_main:
        t.eq("有 module:view 访问模块列表 → 200",
             t.call("GET", "/api/modules", token=token_main)[1].get("code"), 200)
        # 组织树/人员下拉为「业务表单依赖」的开放接口（02/V6.37 口径）：登录即可读，写操作仍判权
        t.eq("组织树对登录用户开放（业务表单依赖）",
             t.call("GET", "/api/system/org/tree", token=token_main)[1].get("code"), 200)
        t.eq("无 system:org:create 新增部门 → 403",
             t.call("POST", "/api/system/org", {"parentId": 0, "name": "x", "code": "x1",
                                                "sort": 1, "status": 1}, token_main)[1].get("code"), 403)

    # ---------------------------------------------------------- 5 模块
    t.section_start("5. 模块管理（增删改查 / 校验 / 筛选）")
    m_id = None
    body = t.code(t.call("GET", "/api/modules/options", token=t.token), "模块候选值接口", 200)
    od = body.get("data") or {}
    t.check("候选含归属产品字典", len(od.get("products") or []) > 0, "products=%s" % od.get("products"))
    t.check("候选含模块状态字典", len(od.get("statuses") or []) > 0, "statuses=%s" % od.get("statuses"))
    t.check("候选含新建的部门", ("%s研发中心" % R) in (od.get("depts") or []), "depts=%s" % od.get("depts"))
    t.check("候选含新建的小组", ("%s前端组" % R) in (od.get("teams") or []), "teams=%s" % od.get("teams"))

    prod = (od.get("products") or [None])[0]
    cat = (od.get("categories") or [None])[0]
    st = (od.get("statuses") or [None])[0]

    t.code(t.call("POST", "/api/modules", {
        "name": "", "product": prod, "category": cat, "dept": "x", "team": "y",
        "owner": "系统管理员", "status": st}, t.token), "模块名称必填校验", 400)
    t.code(t.call("POST", "/api/modules", {
        "name": "%s模块A" % R, "product": "不存在的产品", "category": cat, "dept": "x", "team": "y",
        "owner": "系统管理员", "status": st}, t.token), "非法归属产品被拒绝", 400)
    # 2026-09-11 起模块状态不由表单填写：此处刻意不传 status，验证「缺省默认非活跃」
    t.code(t.call("POST", "/api/modules", {
        "name": "%s模块A" % R, "product": prod, "category": cat, "dept": "%s研发中心" % R,
        "team": "%s前端组" % R, "owner": "系统管理员",
        "rd": ["系统管理员"], "test": "系统管理员", "cost": "CO-E2E-1", "desc": "E2E"}, t.token),
        "创建模块（不传状态）", 200)
    t.code(t.call("POST", "/api/modules", {
        "name": "%s模块A" % R, "product": prod, "category": cat, "dept": "x", "team": "y",
        "owner": "系统管理员", "status": st}, t.token), "同名模块被拒绝", 400)

    body = t.code(t.call("GET", "/api/modules", token=t.token,
                         params={"current": 1, "size": 50, "kw": "%s模块A" % R}), "模块列表检索", 200)
    recs = (body.get("data") or {}).get("records") or []
    t.eq("按名称检索命中 1 条", len(recs), 1)
    if recs:
        m_id = recs[0]["id"]
        t.eq("新建模块待办数为 0", recs[0].get("todoCount"), 0)
        t.eq("模块状态按待办自动流转为非活跃", recs[0].get("status"), "非活跃")
        t.check("模块行含归属产品字段", "product" in recs[0])

    if m_id:
        t.code(t.call("PUT", "/api/modules/%s" % m_id, {
            "id": m_id, "name": "%s模块A改" % R, "product": prod, "category": cat,
            "dept": "%s研发中心" % R, "team": "%s前端组" % R, "owner": "系统管理员",
            "rd": ["系统管理员"], "desc": "E2E 改"}, t.token), "编辑模块（不传状态）", 200)
        body = t.code(t.call("GET", "/api/modules/%s" % m_id, token=t.token), "模块详情", 200)
        t.eq("模块改名生效", (body.get("data") or {}).get("name"), "%s模块A改" % R)

    # ---------------------------------------------------------- 6 专题
    t.section_start("6. 专题管理（增删改查 / 无归属产品与分类）")
    tp_id = None
    body = t.code(t.call("GET", "/api/topics/options", token=t.token), "专题候选值接口", 200)
    tod = body.get("data") or {}
    t.check("专题候选不含 products/categories",
            "products" not in tod and "categories" not in tod, "keys=%s" % list(tod.keys()))
    tst = (tod.get("statuses") or [None])[0]

    t.code(t.call("POST", "/api/topics", {
        "name": "", "dept": "x", "team": "y", "owner": "系统管理员", "status": tst}, t.token),
        "专题名称必填校验", 400)
    t.code(t.call("POST", "/api/topics", {
        "name": "%s专题A" % R, "dept": "%s研发中心" % R, "team": "%s前端组" % R,
        "owner": "系统管理员", "rd": ["系统管理员"], "cost": "CO-E2E-T1"}, t.token),
        "创建专题（不传状态）", 200)
    t.code(t.call("POST", "/api/topics", {
        "name": "%s专题A" % R, "dept": "x", "team": "y", "owner": "系统管理员", "status": tst}, t.token),
        "同名专题被拒绝", 400)

    body = t.code(t.call("GET", "/api/topics", token=t.token,
                         params={"current": 1, "size": 50, "kw": "%s专题A" % R}), "专题列表检索", 200)
    recs = (body.get("data") or {}).get("records") or []
    t.eq("按名称检索命中 1 条", len(recs), 1)
    if recs:
        tp_id = recs[0]["id"]
        t.check("专题行不含 product/category 字段",
                "product" not in recs[0] and "category" not in recs[0], "keys=%s" % list(recs[0].keys()))
        t.eq("新建专题状态流转为非活跃", recs[0].get("status"), "非活跃")

    # ---------------------------------------------------------- 7 项目与子系统
    t.section_start("7. 项目管理与子系统")
    p_id = sub_id = None
    body = t.code(t.call("GET", "/api/projects/options", token=t.token), "项目候选值接口", 200)
    pod = body.get("data") or {}
    p_prod = (pod.get("products") or [None])[0]
    p_st = (pod.get("statuses") or [None])[0]

    t.code(t.call("POST", "/api/projects", {
        "name": "%s项目A" % R, "cost": "CO-E2E-P1", "level": "A", "product": p_prod,
        "dept": "%s研发中心" % R, "team": "%s前端组" % R, "owner": "系统管理员",
        "status": p_st, "budget": 100, "startDate": "2026-09-01", "endDate": "2026-12-31"}, t.token),
        "创建项目", 200)
    t.code(t.call("POST", "/api/projects", {
        "name": "%s项目A" % R, "cost": "CO-E2E-P2", "level": "B", "product": p_prod,
        "dept": "d", "team": "t", "owner": "o", "status": p_st}, t.token), "同名项目被拒绝", 400)
    t.code(t.call("POST", "/api/projects", {
        "name": "%s非法级别" % R, "cost": "CO-E2E-P3", "level": "Z", "product": p_prod,
        "dept": "d", "team": "t", "owner": "o", "status": p_st}, t.token), "非法重要级别被拒绝", 400)
    t.code(t.call("POST", "/api/projects", {
        "name": "%s非法状态" % R, "cost": "CO-E2E-P4", "level": "A", "product": p_prod,
        "dept": "d", "team": "t", "owner": "o", "status": "不存在的状态"}, t.token),
        "非法项目状态被拒绝", 400)

    body = t.code(t.call("GET", "/api/projects", token=t.token,
                         params={"current": 1, "size": 50, "kw": "%s项目A" % R}), "项目列表检索", 200)
    recs = (body.get("data") or {}).get("records") or []
    t.eq("按名称检索命中 1 条", len(recs), 1)
    if recs:
        p_id = recs[0]["id"]
        t.eq("新项目待办为 0", recs[0].get("todoCount"), 0)

    if p_id:
        t.code(t.call("POST", "/api/projects/%s/subsystems" % p_id, {
            "name": "%s子系统A" % R, "owner": "系统管理员", "sort": 1, "cost": "CO-E2E-S1",
            "dept": "%s研发中心" % R, "team": "%s前端组" % R, "status": "在建"}, t.token),
            "创建子系统（含扩字段）", 200)
        body = t.code(t.call("GET", "/api/subsystems", token=t.token, params={"projectId": p_id}),
                      "子系统清单", 200)
        slist = body.get("data") or []
        t.eq("子系统清单命中 1 条", len(slist), 1)
        if slist:
            sub_id = slist[0]["id"]
            t.eq("子系统成本对象落库", slist[0].get("cost"), "CO-E2E-S1")
            t.code(t.call("PUT", "/api/subsystems/%s" % sub_id, {
                "id": sub_id, "projectId": p_id, "name": "%s子系统A改" % R, "owner": "系统管理员",
                "sort": 1, "cost": slist[0].get("cost"), "dept": "%s研发中心" % R,
                "team": "%s前端组" % R, "status": "在建"}, t.token), "编辑子系统", 200)
            body = t.call("GET", "/api/subsystems", token=t.token, params={"projectId": p_id})[1]
            s2 = (body.get("data") or [{}])[0]
            t.eq("子系统改名生效", s2.get("name"), "%s子系统A改" % R)
            t.eq("编辑未覆盖成本对象", s2.get("cost"), "CO-E2E-S1")

    # ---------------------------------------------------------- 8 任务
    t.section_start("8. 任务管理（归属 / 两级 / PATCH / 父状态推导）")
    task_proj = task_mod = task_topic = sub_task = None
    body = t.code(t.call("GET", "/api/tasks/options", token=t.token), "任务候选值接口", 200)
    belong = (body.get("data") or {}).get("belongOptions") or []
    t.check("归属候选含项目/模块/专题三类",
            {"project", "module", "topic"}.issubset({b["type"] for b in belong}),
            "types=%s" % sorted({b["type"] for b in belong}))
    t.check("归属候选含新建的项目/模块/专题",
            any(b["id"] == p_id for b in belong) and any(b["id"] == m_id for b in belong)
            and any(b["id"] == tp_id for b in belong), "")

    base_task = {"priority": "高", "assignee": "系统管理员", "planDate": "2026-09-30",
                 "status": "待处理", "group": "研发组", "hours": 8}
    t.code(t.call("POST", "/api/tasks", dict(base_task, title="%s项目任务" % R, projectId=p_id), t.token),
           "创建项目任务", 200)
    t.code(t.call("POST", "/api/tasks", dict(base_task, title="%s模块任务" % R, moduleId=m_id), t.token),
           "创建模块任务", 200)
    t.code(t.call("POST", "/api/tasks", dict(base_task, title="%s专题任务" % R, topicId=tp_id), t.token),
           "创建专题任务", 200)
    # 2026-09-11 用户需求：归属改为必选（原先允许全空 = 计划外临时任务）
    t.code(t.call("POST", "/api/tasks", dict(base_task, title="%s无归属任务" % R), t.token),
           "新增任务缺归属被拒绝", 400)
    t.code(t.call("POST", "/api/tasks", dict(base_task, title="%s缺小组任务" % R, group=None,
                                             moduleId=m_id), t.token),
           "新增任务缺归属小组被拒绝", 400)
    t.code(t.call("POST", "/api/tasks", dict(base_task, title=""), t.token), "任务标题必填校验", 400)
    t.code(t.call("POST", "/api/tasks", dict(base_task, title="%s非法优先级" % R, priority="超高",
                                             moduleId=m_id), t.token), "非法优先级被拒绝", 400)

    body = t.call("GET", "/api/tasks", token=t.token, params={"current": 1, "size": 0, "kw": R})[1]
    flat = flatten_tasks((body.get("data") or {}).get("records"))
    for x in flat:
        if x["title"] == "%s项目任务" % R:
            task_proj = x["id"]
        if x["title"] == "%s模块任务" % R:
            task_mod = x["id"]
        if x["title"] == "%s专题任务" % R:
            task_topic = x["id"]
    t.check("三类归属任务均已创建", all([task_proj, task_mod, task_topic]),
            "proj=%s mod=%s topic=%s" % (task_proj, task_mod, task_topic))
    proj_row = next((x for x in flat if x["id"] == task_proj), None)
    t.check("归属标签为「项目·xxx」",
            proj_row and str(proj_row.get("belongLabel", "")).startswith("项目·"),
            "belongLabel=%s" % (proj_row or {}).get("belongLabel"))
    mod_row = next((x for x in flat if x["id"] == task_mod), None)
    t.check("归属标签为「模块·xxx」",
            mod_row and str(mod_row.get("belongLabel", "")).startswith("模块·"),
            "belongLabel=%s" % (mod_row or {}).get("belongLabel"))

    if task_proj:
        t.code(t.call("POST", "/api/tasks", dict(base_task, title="%s子任务" % R,
                                                 projectId=p_id, parentId=task_proj), t.token),
               "创建子任务", 200)
        body = t.call("GET", "/api/tasks", token=t.token, params={"current": 1, "size": 0, "kw": R})[1]
        flat = flatten_tasks((body.get("data") or {}).get("records"))
        sub_task = next((x["id"] for x in flat if x["title"] == "%s子任务" % R), None)
        t.check("子任务已创建", bool(sub_task))

        if sub_task:
            t.code(t.call("POST", "/api/tasks", dict(base_task, title="%s孙任务" % R,
                                                     projectId=p_id, parentId=sub_task), t.token),
                   "三级任务被拒绝（仅两级）", 400)

        t.code(t.call("PATCH", "/api/tasks/%s" % task_proj, {"field": "status", "value": "已完成"}, t.token),
               "PATCH 父任务为已完成", 200)
        row = find_task(t, R, task_proj)
        t.check("子任务未完成 → 父任务被回推为进行中",
                row and row.get("status") == "进行中", "parent.status=%s" % (row or {}).get("status"))

        if sub_task:
            t.code(t.call("PATCH", "/api/tasks/%s" % sub_task, {"field": "status", "value": "已完成"}, t.token),
                   "PATCH 子任务为已完成", 200)
            row = find_task(t, R, task_proj)
            t.check("全部子任务完成 → 父任务自动变已完成",
                    row and row.get("status") == "已完成", "parent.status=%s" % (row or {}).get("status"))
            t.check("子任务随父行内嵌（树形）", row and len(row.get("children") or []) == 1,
                    "children=%s" % len((row or {}).get("children") or []))
            t.code(t.call("PATCH", "/api/tasks/%s" % sub_task, {"field": "status", "value": "待处理"}, t.token),
                   "子任务回退为待处理", 200)
            row = find_task(t, R, task_proj)
            t.check("子任务回退 → 父任务回到进行中",
                    row and row.get("status") == "进行中", "parent.status=%s" % (row or {}).get("status"))

        t.code(t.call("PATCH", "/api/tasks/%s" % task_proj, {"field": "hours", "value": 12}, t.token),
               "PATCH 工时", 200)
        body = t.code(t.call("PATCH", "/api/tasks/%s" % task_proj, {"field": "hours", "value": 12}, t.token),
                      "重复写同值", 200)
        t.eq("重复写同值返回 changed=false", (body.get("data") or {}).get("changed"), False)
        t.code(t.call("PATCH", "/api/tasks/%s" % task_proj, {"field": "priority", "value": "不存在"}, t.token),
               "PATCH 非法优先级被拒绝", 400)
        t.code(t.call("PATCH", "/api/tasks/NOT_EXIST", {"field": "hours", "value": 1}, t.token),
               "PATCH 不存在的任务被拒绝", 400)

    # ---------------------------------------------------------- 9 文档
    t.section_start("9. 文档中心")
    doc_id = None
    t.code(t.call("POST", "/api/docs", {"title": "%s无归属" % R, "content": "x"}, t.token),
           "文档归属必选校验", 400)
    t.code(t.call("POST", "/api/docs", {"title": "%s文档A" % R, "moduleId": m_id,
                                        "content": "# 标题\n\nE2E 正文内容"}, t.token), "创建模块文档", 200)
    t.code(t.call("POST", "/api/docs", {"title": "%s专题文档" % R, "topicId": tp_id,
                                        "content": "专题文档正文"}, t.token), "创建专题文档", 200)
    body = t.code(t.call("GET", "/api/docs", token=t.token,
                         params={"current": 1, "size": 50, "kw": "%s文档A" % R}), "文档列表检索", 200)
    recs = (body.get("data") or {}).get("records") or []
    t.check("文档检索命中", len(recs) >= 1)
    if recs:
        doc_id = recs[0]["id"]
        t.check("文档归属标签为「模块·xxx」",
                str(recs[0].get("belongLabel") or "").startswith("模块·"),
                "label=%s" % recs[0].get("belongLabel"))
        t.check("文档字数已统计", (recs[0].get("wordCount") or 0) > 0,
                "wordCount=%s" % recs[0].get("wordCount"))
    body = t.call("GET", "/api/docs", token=t.token,
                  params={"current": 1, "size": 50, "moduleId": m_id})[1]
    t.check("对象内文档过滤（moduleId）命中", len((body.get("data") or {}).get("records") or []) >= 1)
    if doc_id:
        t.code(t.call("PUT", "/api/docs/%s" % doc_id, {"id": doc_id, "title": "%s文档A改" % R,
                                                       "moduleId": m_id, "content": "改后正文"}, t.token),
               "编辑文档", 200)

    # ---------------------------------------------------------- 10 待办口径与流转
    t.section_start("10. 待办口径与状态自动流转")
    if m_id:
        body = t.code(t.call("GET", "/api/modules/%s" % m_id, token=t.token), "模块详情", 200)
        d = body.get("data") or {}
        t.eq("模块待办 = 直属未完成任务数", d.get("todoCount"), 1)
        t.eq("模块状态流转为活跃", d.get("status"), "活跃")
        t.eq("模块文档数统计", d.get("docCount"), 1)
    if tp_id:
        body = t.call("GET", "/api/topics/%s" % tp_id, token=t.token)[1]
        d = body.get("data") or {}
        t.eq("专题待办 = 1", d.get("todoCount"), 1)
        t.eq("专题状态流转为活跃", d.get("status"), "活跃")
    if p_id and task_proj and sub_task:
        body = t.call("GET", "/api/projects", token=t.token,
                      params={"current": 1, "size": 50, "kw": "%s项目A" % R})[1]
        row = ((body.get("data") or {}).get("records") or [{}])[0]
        t.eq("项目待办 = 仅直属任务（父 + 子）", row.get("todoCount"), 2)
        child_rows = row.get("children") or []
        t.eq("子系统待办独立统计（0，不计父项目任务）",
             (child_rows[0].get("todoCount") if child_rows else None), 0)
        t.eq("子系统任务数独立统计（0）",
             (child_rows[0].get("taskCount") if child_rows else None), 0)

    # ---------------------------------------------------------- 11 CSV
    t.section_start("11. Excel(.xlsx) 导入导出")

    def load_xlsx(raw):
        """把导出的字节读成 openpyxl 工作簿（同时验证是合法 xlsx）"""
        import io as _io
        from openpyxl import load_workbook
        return load_workbook(_io.BytesIO(raw))

    status, raw, headers = t.call("GET", "/api/export/modules", token=t.token, raw=True,
                                  params={"token": t.token})
    t.eq("模块导出 HTTP 200", status, 200)
    t.check("模块导出为 xlsx（zip 魔数 PK）", raw[:2] == b"PK", "head=%r" % raw[:4])
    t.check("模块导出 Content-Type 为 xlsx",
            "spreadsheetml.sheet" in (headers.get("Content-Type") or ""),
            "type=%s" % headers.get("Content-Type"))
    try:
        wb = load_xlsx(raw)
        head = [c.value for c in wb.active[1]]
        t.check("模块导出首行含归属产品", "归属产品" in head, "head=%s" % head)
    except Exception as e:
        t.check("模块导出可被 Excel 解析", False, str(e))

    status, raw2, _ = t.call("GET", "/api/export/topics", token=t.token, raw=True,
                             params={"token": t.token})
    t.eq("专题导出 HTTP 200", status, 200)
    try:
        wb2 = load_xlsx(raw2)
        head2 = [c.value for c in wb2.active[1]]
        t.check("专题导出首行不含归属产品", "归属产品" not in head2, "head=%s" % head2)
        t.check("专题导出首行为专题名称", head2 and head2[0] == "专题名称", "head=%s" % head2)
    except Exception as e:
        t.check("专题导出可被 Excel 解析", False, str(e))

    status, raw3, _ = t.call("GET", "/api/export/projects", token=t.token, raw=True,
                             params={"token": t.token})
    t.eq("项目导出 HTTP 200", status, 200)
    try:
        wb3 = load_xlsx(raw3)
        t.check("项目导出可被 Excel 解析", wb3.active.max_row >= 1)
    except Exception as e:
        t.check("项目导出可被 Excel 解析", False, str(e))

    status, _, _ = t.call("GET", "/api/export/modules", raw=True)
    t.eq("导出无 token → HTTP 401", status, 401)

    # 导入：用 openpyxl 生成真正的 xlsx
    from openpyxl import Workbook
    wb_in = Workbook()
    ws = wb_in.active
    ws.append(["模块名称", "归属产品", "模块分类", "负责部门", "负责小组", "模块负责人",
               "研发负责人", "测试负责人", "成本对象", "模块状态", "描述"])
    ws.append(["%s导入模块" % R, "不存在产品", "不存在分类", "研发中心", "前端组", "系统管理员",
               "张军、王强", "", "CO-IMP", "活跃", "E2E 导入"])
    ws.append(["%s模块A改" % R, "不存在产品", "不存在分类", "研发中心", "前端组", "系统管理员",
               "", "", "CO-IMP2", "活跃", "同名跳过"])
    buf = io.BytesIO()
    wb_in.save(buf)

    res = t.upload("/api/import/modules", "modules.xlsx", buf.getvalue(), t.token)
    t.eq("模块导入成功返回 200", res.get("code"), 200)
    data = res.get("data") or {}
    t.eq("模块导入新建 1 条", data.get("created"), 1)
    t.eq("模块导入跳过同名 1 条（skipped）", data.get("skipped"), 1)
    body = t.call("GET", "/api/modules", token=t.token,
                  params={"current": 1, "size": 50, "kw": "%s导入模块" % R})[1]
    recs = (body.get("data") or {}).get("records") or []
    if recs:
        t.check("导入时非法字典值被忽略（产品/分类置空）",
                not recs[0].get("product") and not recs[0].get("category"),
                "product=%s category=%s" % (recs[0].get("product"), recs[0].get("category")))
        t.check("导入时多值字段按「、」拆分",
                (recs[0].get("rd") or []) == ["张军", "王强"], "rd=%s" % recs[0].get("rd"))

    # 上传非 xlsx 应给出可读报错
    res_bad = t.upload("/api/import/modules", "bad.xlsx", "not-an-excel".encode(), t.token)
    t.check("上传非 Excel 文件被拒绝且提示可读",
            res_bad.get("code") != 200 and "xlsx" in str(res_bad.get("msg", "")),
            "code=%s msg=%s" % (res_bad.get("code"), res_bad.get("msg")))

    # ---------------------------------------------------------- 12 可见性
    t.section_start("12. 数据可见性（参与者及其上级 / view-all）")
    if token_main and m_id:
        body = t.call("GET", "/api/modules", token=token_main,
                      params={"current": 1, "size": 50, "kw": R})[1]
        n = len((body.get("data") or {}).get("records") or [])
        t.eq("非参与者看不到该模块（可见性过滤生效）", n, 0)
        t.eq("越权访问模块详情 → 403",
             t.call("GET", "/api/modules/%s" % m_id, token=token_main)[1].get("code"), 403)
    if m_id:
        body = t.call("GET", "/api/modules", token=t.token,
                      params={"current": 1, "size": 50, "kw": R})[1]
        t.check("管理员（module:view-all）可见该模块",
                len((body.get("data") or {}).get("records") or []) >= 1)

    # ---------------------------------------------------------- 13 个人中心
    t.section_start("13. 个人中心（资料 / 改密码）")
    body = t.code(t.call("GET", "/api/user/profile", token=t.token), "读取个人资料", 200)
    t.eq("个人资料姓名正确", (body.get("data") or {}).get("name"), "系统管理员")
    # 改密码用一次性账号：① 不污染 admin 口令；② 弱口令策略要求 ≥8 位含字母数字
    tok_lock = t.login(lock_user, "LockTest123")
    t.check("一次性账号可登录", tok_lock is not None)
    t.code(t.call("PUT", "/api/user/password", {"oldPassword": "wrong", "newPassword": "Abcd1234"}, tok_lock),
           "原密码错误被拒绝", 400)
    t.code(t.call("PUT", "/api/user/password", {"oldPassword": "LockTest123", "newPassword": "weak"}, tok_lock),
           "弱密码被拒绝", 400)
    t.code(t.call("PUT", "/api/user/password", {"oldPassword": "LockTest123", "newPassword": "LockTest456"},
                  tok_lock), "修改密码", 200)
    t.check("新密码可登录", t.login(lock_user, "LockTest456") is not None)
    t.check("旧密码失效", t.login(lock_user, "LockTest123") is None)

    # ---------------------------------------------------------- 14 日志 / 菜单
    t.section_start("13.5 用户级界面设置（按用户存库）")
    # 先快照本人原设置，用例结束还原（数据目录与真实使用共享，避免用例写入污染管理员偏好）
    original_settings = dict(t.call("GET", "/api/user/settings", token=t.token)[1].get("data") or {})
    body = t.code(t.call("GET", "/api/user/settings", token=t.token), "读取本人设置", 200)
    t.check("初始为对象", isinstance(body.get("data"), dict))
    t.code(t.call("PUT", "/api/user/settings",
                  {"systemThemeColor": "#60C041", "showWorkTab": True}, t.token), "保存本人设置", 200)
    body = t.call("GET", "/api/user/settings", token=t.token)[1]
    t.eq("回读主题色一致", (body.get("data") or {}).get("systemThemeColor"), "#60C041")
    t.eq("回读功能页签开关一致", (body.get("data") or {}).get("showWorkTab"), True)
    # 用户隔离：另一个用户读写互不影响
    if token_noperm:
        body = t.call("GET", "/api/user/settings", token=token_noperm)[1]
        t.check("另一用户读到的设置独立（不含前一用户的值）",
                (body.get("data") or {}).get("systemThemeColor") != "#60C041",
                "data=%s" % body.get("data"))
        t.code(t.call("PUT", "/api/user/settings", {"systemThemeColor": "#F9901F"}, token_noperm),
               "另一用户保存自己的设置", 200)
        body = t.call("GET", "/api/user/settings", token=t.token)[1]
        t.eq("另一用户保存后不影响本人设置",
             (body.get("data") or {}).get("systemThemeColor"), "#60C041")
    # 还原快照（保持管理员真实偏好不变）
    t.code(t.call("PUT", "/api/user/settings", original_settings, t.token), "还原本人原设置", 200)
    body = t.call("GET", "/api/user/settings", token=t.token)[1]
    t.eq("还原后与快照一致", (body.get("data") or {}).get("systemThemeColor"),
         original_settings.get("systemThemeColor"))

    t.section_start("13.6 基础数据：枚举项拖动排序")
    # ------------------------------------------------------------------
    # 临时字典内自建自清（不触碰内置字典）：排序 / 校验 / 缺陷回归
    tmp_dict_code = "e2e_dict_reorder"
    t.code(t.call("POST", "/api/system/dict",
                  {"code": tmp_dict_code, "name": "%s排序验证字典" % R, "description": "临时", "sort": 99},
                  t.token), "建临时字典", 200)
    body = t.call("GET", "/api/system/dict/page", token=t.token,
                  params={"current": 1, "size": 100})[1]
    tmp_dict = next((d for d in (body.get("data") or {}).get("records") or []
                     if d.get("code") == tmp_dict_code), None)
    t.check("按 code 查回临时字典（不猜 id）", tmp_dict is not None)
    if tmp_dict:
        d_id = tmp_dict["id"]
        item_codes = ["E2E_RA", "E2E_RB", "E2E_RC"]
        for code in item_codes:
            t.code(t.call("POST", "/api/system/dict/item",
                          {"dictId": d_id, "code": code, "name": "%s-%s" % (R, code)}, t.token),
                   "新增枚举项 %s（不传 sort）" % code, 200)
        items = (t.call("GET", "/api/system/dict/%d/items" % d_id, token=t.token)[1].get("data") or [])
        t.eq("新项按创建先后追加（sort 1..n）",
             [(i["code"], i["sort"]) for i in items], [(c, n + 1) for n, c in enumerate(item_codes)])
        ids = [i["id"] for i in items]
        rev = list(reversed(ids))
        t.code(t.call("POST", "/api/system/dict/%d/items/reorder" % d_id, {"ids": rev}, t.token),
               "拖动排序（倒序）", 200)
        now = (t.call("GET", "/api/system/dict/%d/items" % d_id, token=t.token)[1].get("data") or [])
        t.eq("顺序按传入 id 重写", [i["id"] for i in now], rev)
        t.eq("sort 重写为 1..n", [i["sort"] for i in now], [1, 2, 3])
        t.code(t.call("POST", "/api/system/dict/%d/items/reorder" % d_id, {"ids": rev[:-1]}, t.token),
               "漏传一项被拒", 400)
        t.code(t.call("POST", "/api/system/dict/%d/items/reorder" % d_id, {"ids": rev + [999999]}, t.token),
               "多传不存在 id 被拒", 400)
        first = now[0]
        t.code(t.call("PUT", "/api/system/dict/item",
                      {"id": first["id"], "dictId": d_id, "code": first["code"], "name": "E2E-改名A"},
                      t.token), "编辑枚举项（不传 sort）", 200)
        now2 = (t.call("GET", "/api/system/dict/%d/items" % d_id, token=t.token)[1].get("data") or [])
        t.eq("编辑后顺序不变", [i["id"] for i in now2], rev)
        # 缺陷回归（2026-09-11）：ext 校验失败返回 400 时，显示名不得在内存镜像里残留脏值
        t.code(t.call("POST", "/api/system/dict/field",
                      {"dictId": d_id, "fieldCode": "req_note", "fieldName": "必填备注",
                       "fieldType": "string", "isRequired": 1}, t.token), "临时字典加必填扩展字段", 200)
        t.code(t.call("PUT", "/api/system/dict/item",
                      {"id": first["id"], "dictId": d_id, "code": first["code"], "name": "E2E-不应生效"},
                      t.token), "缺少必填扩展字段被拒", 400)
        same = next((i for i in (t.call("GET", "/api/system/dict/%d/items" % d_id,
                                        token=t.token)[1].get("data") or []) if i["id"] == first["id"]), None)
        t.eq("校验失败后显示名未被改动（缺陷回归）", same and same["name"], "E2E-改名A")
        # 清理：先删项再删字典（字典删除要求无启用枚举项）
        for i in (t.call("GET", "/api/system/dict/%d/items" % d_id,
                         token=t.token)[1].get("data") or []):
            t.call("DELETE", "/api/system/dict/item/%d" % i["id"], token=t.token)
        t.code(t.call("DELETE", "/api/system/dict/%d" % d_id, token=t.token), "删除临时字典", 200)

    t.section_start("14. 操作日志与菜单顺序")
    body = t.code(t.call("GET", "/api/system/log/page", token=t.token,
                         params={"current": 1, "size": 50}), "操作日志分页接口", 200)
    logs = (body.get("data") or {}).get("records") or []
    mods = sorted({l.get("module") for l in logs})
    t.check("写操作已留痕（含 module/topic/project）",
            {"module", "topic", "project"}.issubset(set(mods)), "modules=%s" % mods)
    t.check("日志含操作人/对象/动作/详情五要素",
            all(l.get("operator") and l.get("target") and l.get("action") is not None for l in logs[:10]),
            "sample=%s" % (logs[0] if logs else None))
    body = t.call("GET", "/api/system/log/page", token=t.token,
                  params={"current": 1, "size": 50, "filters": '{"module":{"op":"in","value":["topic"]}}'})[1]
    t.check("日志按模块筛选生效",
            all(l.get("module") == "topic" for l in (body.get("data") or {}).get("records") or []))

    t.code(t.call("PUT", "/api/menu/order", {"paths": ["/task", "/module", "/topic"]}, t.token),
           "保存菜单顺序", 200)
    body = t.code(t.call("GET", "/api/menu/order", token=t.token), "菜单顺序回读", 200)
    t.eq("菜单顺序与保存一致", body.get("data"), ["/task", "/module", "/topic"])

    # ---------------------------------------------------------- 15 级联删除
    t.section_start("14.5 批量删除（仅超级管理员）")
    # 用一条临时文档验证批量删除链路（不影响其它断言）
    from openpyxl import Workbook as _WB  # noqa: F401  (仅为保持导入区整洁)
    t.code(t.call("POST", "/api/docs", {"title": "%s批量删除文档1" % R, "moduleId": m_id,
                                        "content": "x"}, t.token), "创建批量删除文档1", 200)
    t.code(t.call("POST", "/api/docs", {"title": "%s批量删除文档2" % R, "moduleId": m_id,
                                        "content": "y"}, t.token), "创建批量删除文档2", 200)
    body = t.call("GET", "/api/docs", token=t.token,
                  params={"current": 1, "size": 50, "kw": "%s批量删除文档" % R})[1]
    ids = [d["id"] for d in ((body.get("data") or {}).get("records") or [])]
    t.eq("待批量删除的文档 2 条", len(ids), 2)
    res = t.call("POST", "/api/docs/batch-delete", {"ids": ids}, t.token)[1]
    t.eq("批量删除接口返回 200", res.get("code"), 200)
    t.eq("批量删除计数正确", (res.get("data") or {}).get("deleted"), 2)
    body = t.call("GET", "/api/docs", token=t.token,
                  params={"current": 1, "size": 50, "kw": "%s批量删除文档" % R})[1]
    t.eq("批量删除后列表无残留", len((body.get("data") or {}).get("records") or []), 0)
    # 无 batch:delete 权限的角色（普通员工）应被拒绝
    token_emp = t.login("%s_noperm" % R.lower(), "E2eTest123")
    if token_emp:
        code = t.call("POST", "/api/docs/batch-delete", {"ids": ["not-exist"]}, token_emp)[1].get("code")
        t.eq("无 batch:delete 权限调用批量删除 → 403", code, 403)

    t.section_start("15. 级联删除")
    if m_id:
        body = t.call("GET", "/api/modules/%s" % m_id, token=t.token)[1]
        d = body.get("data") or {}
        t.eq("删除前模块任务数 1", d.get("totalTaskCount"), 1)
        body = t.code(t.call("DELETE", "/api/modules/%s" % m_id, token=t.token), "删除模块", 200)
        rd = body.get("data") or {}
        t.eq("模块级联删除任务 1 个", rd.get("tasks"), 1)
        t.eq("模块级联删除文档 1 个", rd.get("docs"), 1)
        body = t.call("GET", "/api/tasks", token=t.token,
                      params={"current": 1, "size": 0, "moduleId": m_id})[1]
        t.eq("模块级联：其任务已删除", len((body.get("data") or {}).get("records") or []), 0)
        t.bad(t.call("GET", "/api/modules/%s" % m_id, token=t.token), "模块已不存在")
        m_id = None

    if tp_id:
        body = t.code(t.call("DELETE", "/api/topics/%s" % tp_id, token=t.token), "删除专题", 200)
        rd = body.get("data") or {}
        t.eq("专题级联删除任务 1 个", rd.get("tasks"), 1)
        t.eq("专题级联删除文档 1 个", rd.get("docs"), 1)
        tp_id = None

    if p_id:
        body = t.code(t.call("DELETE", "/api/projects/%s" % p_id, token=t.token), "删除项目", 200)
        rd = body.get("data") or {}
        t.eq("项目级联删除子系统 1 个", rd.get("subsystems"), 1)
        t.eq("项目级联删除任务 2 个（父 + 子）", rd.get("tasks"), 2)
        t.eq("项目级联删除文档 0 个", rd.get("docs"), 0)
        body = t.call("GET", "/api/subsystems", token=t.token, params={"projectId": p_id})[1]
        t.eq("项目级联：子系统已删除", len(body.get("data") or []), 0)
        p_id = None

    # ---------------------------------------------------------- 16 异常回归
    t.section_start("16. 异常入参与边界回归")
    t.bad(t.call("GET", "/api/modules/NOT_EXIST", token=t.token), "不存在的模块 id 被拒绝")
    t.bad(t.call("GET", "/api/topics/NOT_EXIST", token=t.token), "不存在的专题 id 被拒绝")
    t.bad(t.call("GET", "/api/projects/NOT_EXIST", token=t.token), "不存在的项目 id 被拒绝")
    t.bad(t.call("DELETE", "/api/tasks/NOT_EXIST", token=t.token), "删除不存在的任务被拒绝")
    t.bad(t.call("PUT", "/api/subsystems/NOT_EXIST", {"id": "NOT_EXIST", "projectId": "x",
                                                      "name": "x"}, t.token), "编辑不存在的子系统被拒绝")
    body = t.call("GET", "/api/modules", token=t.token,
                  params={"current": 1, "size": 50,
                          "filters": '{"owner":{"op":"in","value":["不存在的人"]}}'})[1]
    t.eq("未知筛选值返回空集而不是报错", (body.get("data") or {}).get("total"), 0)
    t.eq("非白名单筛选字段被忽略",
           t.call("GET", "/api/modules", token=t.token,
                  params={"current": 1, "size": 50,
                          "filters": '{"notAField":{"op":"like","value":"x"}}'})[1].get("code"), 200)
    body = t.call("GET", "/api/modules", token=t.token,
                  params={"current": 1, "size": 50, "status": "活跃,非活跃"})[1]
    t.eq("多选筛选（逗号分隔 OR）返回 200", body.get("code"), 200)
    body = t.call("GET", "/api/modules", token=t.token, params={"current": 1, "size": 25})[1]
    t.eq("分页 size 生效", (body.get("data") or {}).get("size"), 25)

    # ---------------------------------------------------------- 17 清理
    if not args.keep:
        t.section_start("17. 测试数据清理")
        body = t.call("GET", "/api/system/user/page", token=t.token,
                      params={"current": 1, "size": 100, "name": R})[1]
        test_users = [u["id"] for u in ((body.get("data") or {}).get("records") or [])]
        # 2026-09-11 起用户支持物理删除：清理改为真删（原先只能停用）
        for uid in test_users:
            t.code(t.call("DELETE", "/api/system/user/%d" % uid, token=t.token),
                   "删除测试用户 #%s" % uid, 200)
        body = t.call("GET", "/api/system/user/page", token=t.token,
                      params={"current": 1, "size": 100})[1]
        left = [u for u in ((body.get("data") or {}).get("records") or [])
                if str(u.get("name") or "").startswith(R)]
        t.eq("测试用户已全部删除（列表无残留）", len(left), 0)
        # 删除前必须确认 code 带测试前缀：角色 ID 为 max+1 分配、删除后会被复用，
        # 仅凭 id 删除有误删系统角色的风险（2026-09-11 实测教训，已加固）
        # 导入用例创建的模块也要清（否则第二次跑会变成「同名跳过」）
        body = t.call("GET", "/api/modules", token=t.token,
                      params={"current": 1, "size": 50, "kw": "%s导入模块" % R})[1]
        for rec in ((body.get("data") or {}).get("records") or []):
            t.code(t.call("DELETE", "/api/modules/%s" % rec["id"], token=t.token),
                   "删除导入用例模块 %s" % rec["id"], 200)

        for label, rid, rcode in (("测试角色", role_id, role_code),
                                  ("空权限角色", role_none_id, role_none_code)):
            if not rid:
                continue
            if not (rcode or "").startswith("%s_" % R):
                t.check("拒绝删除非测试角色 #%s（code=%s）" % (rid, rcode), False,
                        "code 不以 %s_ 开头，已跳过删除" % R)
                continue
            t.code(t.call("DELETE", "/api/system/role/%d" % rid, token=t.token), "删除%s" % label, 200)
        # 组织删除受「部门下存在人员」约束；而用户模块只有停用、无物理删除接口，
        # 故测试组织留待停服后由 scripts/post-e2e-cleanup.py 按前缀一并清理（如实报告，不算失败）
        remaining = []
        for label, oid in (("前端组", org_team), ("研发中心", org_dept), ("产品中心", org_dept2),
                           ("总公司", org_root)):
            if not oid:
                continue
            # 同上：只删本次运行创建的组织（名称带测试前缀）
            is_mine = t.call("GET", "/api/system/org/tree", token=t.token)[1]
            if not _org_named(is_mine.get("data"), oid, R):
                remaining.append("%s(非本次创建，跳过)" % label)
                continue
            res = t.call("DELETE", "/api/system/org/%d" % oid, token=t.token)
            if res[1].get("code") != 200:
                remaining.append("%s(%s)" % (label, res[1].get("msg")))
        print("      组织清理：%s" % ("全部删除" if not remaining else "留待停服清理 -> " + "；".join(remaining)))
        t.leftover = {"orgs": remaining}

    failed = t.summary()
    print("\n遗留（用户模块无物理删除接口，仅停用）：%s" % t.leftover)
    return failed


def find_task(t, run_id, task_id):
    body = t.call("GET", "/api/tasks", token=t.token,
                  params={"current": 1, "size": 0, "kw": run_id})[1]
    for row in flatten_tasks((body.get("data") or {}).get("records")):
        if row.get("id") == task_id:
            return row
    return None


if __name__ == "__main__":
    sys.exit(1 if main() else 0)
