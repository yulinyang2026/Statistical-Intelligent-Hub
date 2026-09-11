# -*- coding: utf-8 -*-
"""
E2E 测试遗留清理 —— 需在**后端停止后**运行

用户模块没有物理删除接口（只有停用/离职），E2E 测试创建的用户会留在库里。
本脚本从数据文件中移除带 E2E 前缀的用户及其账号/角色绑定/组织绑定，
并清理测试留下的角色与组织，使系统回到干净基线。

用法：
  1) 停止后端（8080/8091 实例）
  2. cd backend && python scripts/post-e2e-cleanup.py
  3. 重启后端
"""
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "data", "system")
SEED = os.path.join(ROOT, "src", "main", "resources", "seed")
PATTERN = re.compile(r"^E2E")  # 默认前缀，可用 --prefix 覆盖


def read(p):
    with io.open(p, encoding="utf-8") as f:
        return json.load(f)


def write(p, d):
    with io.open(p, "w", encoding="utf-8", newline="") as f:
        f.write(json.dumps(d, ensure_ascii=False, indent=2))


def clean(base, label):
    changed = []

    users = read(os.path.join(base, "user.json"))
    keep_users = [u for u in users if not PATTERN.match(str(u.get("name") or ""))]
    drop_ids = {u["id"] for u in users if PATTERN.match(str(u.get("name") or ""))}
    if drop_ids:
        write(os.path.join(base, "user.json"), keep_users)
        changed.append("user.json -%d" % len(drop_ids))

        accounts = read(os.path.join(base, "account.json"))
        keep = [a for a in accounts if a.get("userId") not in drop_ids]
        if len(keep) != len(accounts):
            write(os.path.join(base, "account.json"), keep)
            changed.append("account.json -%d" % (len(accounts) - len(keep)))

        # user-setting.json 一并清理：设置按 userId 关联，用户删除后残留会在 id 复用时串到新用户
        for fn in ("user-role.json", "user-org.json", "user-setting.json"):
            p = os.path.join(base, fn)
            if not os.path.exists(p):
                continue
            rows = read(p)
            keep = [x for x in rows if x.get("userId") not in drop_ids]
            if len(keep) != len(rows):
                write(p, keep)
                changed.append("%s -%d" % (fn, len(rows) - len(keep)))

    # 测试角色（编码前缀）
    p = os.path.join(base, "role.json")
    roles = read(p)
    role_ids = {r["id"] for r in roles if PATTERN.match(str(r.get("code") or ""))}
    if role_ids:
        write(p, [r for r in roles if r["id"] not in role_ids])
        rp = read(os.path.join(base, "role-permission.json"))
        keep = [x for x in rp if x.get("roleId") not in role_ids]
        write(os.path.join(base, "role-permission.json"), keep)
        changed.append("role.json -%d（含权限绑定）" % len(role_ids))

    # 业务实体（名称/标题带测试前缀）——模块/专题/项目/子系统/任务/文档
    for fn, name_key in (("module.json", "name"), ("topic.json", "name"), ("project.json", "name"),
                         ("subsystem.json", "name"), ("task.json", "title"), ("doc.json", "title")):
        p = os.path.join(base, fn)
        if not os.path.exists(p):
            continue
        rows = read(p)
        keep = [x for x in rows if not PATTERN.match(str(x.get(name_key) or ""))]
        if len(keep) != len(rows):
            write(p, keep)
            changed.append("%s -%d" % (fn, len(rows) - len(keep)))

    # 测试组织（名称前缀）
    p = os.path.join(base, "org.json")
    orgs = read(p)
    if orgs:
        keep = [o for o in orgs if not PATTERN.match(str(o.get("name") or ""))]
        if len(keep) != len(orgs):
            write(p, keep)
            changed.append("org.json -%d" % (len(orgs) - len(keep)))

    # 测试字典（编码前缀，忽略大小写——字典编码为小写蛇形）：连同其字段与枚举项一并清理
    # （字典无硬删接口，用例跑完只会留下 deleted=1 的软删行）
    p = os.path.join(base, "dict.json")
    if os.path.exists(p):
        dicts = read(p)
        hit = [d for d in dicts
               if re.match(PATTERN.pattern, str(d.get("code") or ""), re.IGNORECASE)]
        if hit:
            hit_ids = {d["id"] for d in hit}
            write(p, [d for d in dicts if d["id"] not in hit_ids])
            changed.append("dict.json -%d" % len(hit_ids))
            for fn in ("dict-field.json", "dict-item.json"):
                fp = os.path.join(base, fn)
                if not os.path.exists(fp):
                    continue
                rows = read(fp)
                keep = [r for r in rows if r.get("dictId") not in hit_ids]
                if len(keep) != len(rows):
                    write(fp, keep)
                    changed.append("%s -%d" % (fn, len(rows) - len(keep)))

    print("  [%s] %s" % (label, "；".join(changed) if changed else "无遗留"))


if __name__ == "__main__":
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--prefix", default="E2E", help="按名称/编码前缀清理测试数据（默认 E2E）")
    _args = ap.parse_args()
    PATTERN = re.compile("^" + re.escape(_args.prefix))
    print("清理测试遗留数据（前缀 %s，请确保后端已停止）" % _args.prefix)
    clean(DATA, "data")
    clean(SEED, "seed")
    print("完成。")
    sys.exit(0)
