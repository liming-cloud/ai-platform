#!/usr/bin/env python3

import re
import sys
from pathlib import Path


CONSOLE_SOURCE = Path("frontend/apps/console-web/src")
LOAD_MORE_IDENTIFIER = re.compile(r"\b(?:loadMore|loadingMore)\b")
PAGE_ITEM_APPEND = re.compile(r"\.\.\.\s*page\.items\b")
DIALOG_TOKEN = re.compile(r"</?Dialog\b[^>]*>")


def source_files(root):
    source_root = Path(root) / CONSOLE_SOURCE
    if not source_root.is_dir():
        return []
    return sorted(path for path in source_root.rglob("*.tsx") if path.is_file())


def has_nested_dialog(source):
    depth = 0
    for token in DIALOG_TOKEN.finditer(source):
        text = token.group(0)
        if text.startswith("</"):
            depth = max(0, depth - 1)
            continue
        if text.rstrip().endswith("/>"):
            continue
        if depth > 0:
            return True
        depth += 1
    return False


def scan(root):
    errors = []
    for path in source_files(root):
        source = path.read_text(encoding="utf-8")
        relative = path.relative_to(root).as_posix()
        if LOAD_MORE_IDENTIFIER.search(source):
            errors.append(f"禁止浏览器累加分页：{relative} 包含加载更多状态或操作")
        if PAGE_ITEM_APPEND.search(source):
            errors.append(f"禁止浏览器累加分页：{relative} 追加 page.items")
        if has_nested_dialog(source):
            errors.append(f"禁止嵌套 Dialog：{relative}")
    return errors


def verify(root):
    errors = scan(root)
    if errors:
        raise RuntimeError("\n".join(errors))
    print("前端产品门禁通过：未发现浏览器累加分页或嵌套 Dialog")


def main(argv):
    if len(argv) != 2 or argv[1] != "verify":
        raise SystemExit("用法：frontend_product_guard.py verify")
    verify(Path.cwd())


if __name__ == "__main__":
    main(sys.argv)
