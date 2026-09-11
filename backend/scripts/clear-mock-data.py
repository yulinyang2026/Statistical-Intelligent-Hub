# -*- coding: utf-8 -*-
"""
清除演示（mock）数据 —— 一次性运维脚本，2026-09-11

清什么：
  · 业务实体：module / topic / project / subsystem / task / doc
  · 日志与 AI 会话：log / chat-session / chat-message
  · 已废弃的归属垫底数据源：belong-project / belong-subsystem / belong-topic（直接删文件）
  · 菜单自定义顺序：menu-order
  · 演示组织树：org（由测试用例重新创建）
  · 演示用户与账号：除 admin(id=1) 外的全部；user-role 仅留 admin→R_SUPER；user-org 清空

留什么（系统基线，登录与表单依赖）：
  · admin 用户 + 账号（123456）+ R_SUPER 绑定
  · 5 个系统角色 + 40 个权限点 + 角色权限绑定
  · 4 个内置字典 + 字段 + 枚举项

运行数据（backend/data/system/）与种子（backend/src/main/resources/seed/）同步处理，
处理前整体备份到 backend/_mock-backup-20260911/。

用法：cd backend && python scripts/clear-mock-data.py [--force]
      · 检测到已有业务数据（组织/模块/专题/项目/任务/文档/非 admin 用户）时会**拒绝执行**，需显式 --force
      · 只想清测试遗留请用 post-e2e-cleanup.py --prefix <前缀>
"""
import argparse
import io
import json
import os
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUNTIME = os.path.join(ROOT, "data", "system")
SEED = os.path.join(ROOT, "src", "main", "resources", "seed")
BACKUP = os.path.join(ROOT, "_mock-backup-20260911")

ADMIN_USER_ID = 1
ADMIN_ROLE_ID = 1  # R_SUPER

# 清空为 []
# 注意：log.json（操作日志）**不在清理范围**——审计留痕属保护性数据，需用户显式授权后方可清理，
#       如需一并清空，由用户执行：echo "[]" > backend/data/system/log.json
EMPTY_FILES = [
    "module.json", "topic.json", "project.json", "subsystem.json", "task.json", "doc.json",
    "chat-session.json", "chat-message.json", "menu-order.json", "org.json",
    "user-org.json",
]
# 直接删除（代码已不再读取）
DELETE_FILES = ["belong-project.json", "belong-subsystem.json", "belong-topic.json"]


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return json.load(f)


def write(path, data):
    with io.open(path, "w", encoding="utf-8", newline="") as f:
        f.write(json.dumps(data, ensure_ascii=False, indent=2))


def backup():
    if os.path.isdir(BACKUP):
        print("备份目录已存在，跳过备份：%s" % BACKUP)
        return
    os.makedirs(BACKUP)
    for src, name in ((RUNTIME, "data-system"), (SEED, "seed")):
        shutil.copytree(src, os.path.join(BACKUP, name))
    print("已备份 -> %s" % BACKUP)


def clear_dir(base, label):
    for fn in EMPTY_FILES:
        p = os.path.join(base, fn)
        if os.path.exists(p):
            write(p, [])
            print("  [%s] %-24s -> []" % (label, fn))
    for fn in DELETE_FILES:
        p = os.path.join(base, fn)
        if os.path.exists(p):
            os.remove(p)
            print("  [%s] %-24s -> 删除文件" % (label, fn))

    # 用户：仅留 admin
    p = os.path.join(base, "user.json")
    users = read(p)
    keep = [u for u in users if u.get("id") == ADMIN_USER_ID]
    for u in keep:
        # 清掉指向演示组织树/上下级的悬空引用
        for field in ("deptId", "groupId", "managerUserId"):
            u[field] = None
    write(p, keep)
    print("  [%s] %-24s %d -> %d（仅留 admin）" % (label, "user.json", len(users), len(keep)))

    # 账号：仅留 admin，重置为初始口令占位符（启动时重新 BCrypt 哈希）并解除登录失败锁定
    # （连续失败 5 次会锁 10 分钟，E2E 用例曾把 admin 锁住 / 改掉口令，这里做确定性复位）
    p = os.path.join(base, "account.json")
    accounts = read(p)
    keep = [a for a in accounts if a.get("userId") == ADMIN_USER_ID]
    for a in keep:
        a["credential"] = "SEED:123456"
        a["failCount"] = 0
        a["lockUntil"] = None
        a["status"] = 1
    write(p, keep)
    print("  [%s] %-24s %d -> %d（仅留 admin，口令复位为 123456）"
          % (label, "account.json", len(accounts), len(keep)))

    # 用户-角色：仅留 admin → R_SUPER
    p = os.path.join(base, "user-role.json")
    urs = read(p)
    keep = [x for x in urs if x.get("userId") == ADMIN_USER_ID and x.get("roleId") == ADMIN_ROLE_ID]
    write(p, keep)
    print("  [%s] %-24s %d -> %d（仅留 admin→R_SUPER）" % (label, "user-role.json", len(urs), len(keep)))

    # 用户界面设置：仅留 admin（按 userId 关联，用户清掉后残留会在 id 复用时串到新用户）
    p = os.path.join(base, "user-setting.json")
    if os.path.exists(p):
        rows = read(p)
        keep = [x for x in rows if x.get("userId") == ADMIN_USER_ID]
        write(p, keep)
        print("  [%s] %-24s %d -> %d（仅留 admin）" % (label, "user-setting.json", len(rows), len(keep)))


def has_real_data():
    """检测是否已有真实业务数据（用户自建）：有则默认拒绝清库，避免误删"""
    for fn, label in (("org.json", "组织"), ("module.json", "模块"), ("topic.json", "专题"),
                      ("project.json", "项目"), ("task.json", "任务"), ("doc.json", "文档")):
        path = os.path.join(RUNTIME, fn)
        if os.path.exists(path) and len(read(path)) > 0:
            return "%s %d 条" % (label, len(read(path)))
    users = read(os.path.join(RUNTIME, "user.json"))
    extra = [u for u in users if u.get("id") != ADMIN_USER_ID]
    if extra:
        return "非管理员用户 %d 个" % len(extra)
    return None


def main():
    ap = argparse.ArgumentParser(description="清空演示/测试数据，复位到可登录基线")
    ap.add_argument("--force", action="store_true", help="已有业务数据时仍强制清空（危险）")
    args = ap.parse_args()

    existing = has_real_data()
    if existing and not args.force:
        print("检测到已有数据（%s），已中止清库。" % existing)
        print("  确认要清空请加 --force；若只是想清理测试遗留，请用 post-e2e-cleanup.py --prefix <前缀>")
        return

    backup()
    print("清理运行数据目录 %s" % RUNTIME)
    clear_dir(RUNTIME, "data")
    print("清理种子目录 %s" % SEED)
    clear_dir(SEED, "seed")
    print("\n完成。基线保留：admin 账号 / 5 角色 / 40 权限点 / 角色绑定 / 4 字典。")


if __name__ == "__main__":
    main()
