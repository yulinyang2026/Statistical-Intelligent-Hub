# -*- coding: utf-8 -*-
"""
修复「孤儿用户」—— 需在**后端停止后**运行

背景（2026-09-11 修复的缺陷）：`UserService.create()` 原先先写 user 行、后做密码强度与主管校验，
校验失败时 user 行已经落库但没有对应登录账号。此后任何重试都会被工号/手机号/登录账号唯一性校验挡住，
现象就是「新增用户一直报用户名已存在 / 手机号已存在」。

根因已在 UserService 中修复（校验全部前置）。本脚本用于清理**历史上已产生的孤儿用户行**：
删除在 `user.json` 中存在、但在 `account.json` 中没有任何账号的**非 admin** 用户，
并顺带清掉它们可能残留的 user-org / user-role 绑定。

用法：
  1) 停止后端
  2) cd backend && python scripts/repair-orphan-users.py [--dry-run]
  3) 重启后端
"""
import argparse
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "data", "system")
SEED = os.path.join(ROOT, "src", "main", "resources", "seed")
ADMIN_USER_ID = 1


def read(p):
    with io.open(p, encoding="utf-8") as f:
        return json.load(f)


def write(p, d):
    with io.open(p, "w", encoding="utf-8", newline="") as f:
        f.write(json.dumps(d, ensure_ascii=False, indent=2))


def repair(base, label, dry_run):
    users = read(os.path.join(base, "user.json"))
    accounts = read(os.path.join(base, "account.json"))
    has_account = {a.get("userId") for a in accounts}
    orphans = [u for u in users if u.get("id") not in has_account and u.get("id") != ADMIN_USER_ID]
    if not orphans:
        print("  [%s] 无孤儿用户" % label)
        return
    for u in orphans:
        print("  [%s] 孤儿用户 id=%s 姓名=%s 手机号=%s 工号=%s"
              % (label, u.get("id"), u.get("name"), u.get("mobile"), u.get("employeeNo")))
    if dry_run:
        print("  [%s] --dry-run：未写入" % label)
        return
    drop = {u["id"] for u in orphans}
    write(os.path.join(base, "user.json"), [u for u in users if u["id"] not in drop])
    for fn in ("user-org.json", "user-role.json"):
        p = os.path.join(base, fn)
        if not os.path.exists(p):
            continue
        rows = read(p)
        keep = [x for x in rows if x.get("userId") not in drop]
        if len(keep) != len(rows):
            write(p, keep)
            print("  [%s] %s 同步清理 %d 条绑定" % (label, fn, len(rows) - len(keep)))
    print("  [%s] 已删除 %d 个孤儿用户" % (label, len(drop)))


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    print("修复孤儿用户（请确保后端已停止）")
    repair(DATA, "data", args.dry_run)
    repair(SEED, "seed", args.dry_run)
    print("完成。")
