#!/usr/bin/env python3

import re
import sys
from pathlib import Path


SPA_SOURCES = (
    Path("frontend/apps/console-web/src"),
    Path("frontend/packages/query-workspace/src"),
)
LOAD_MORE_IDENTIFIER = re.compile(r"\b(?:loadMore|loadingMore)\b")
PAGE_ITEM_APPEND = re.compile(r"\.\.\.\s*page\.items\b")
DIALOG_TOKEN = re.compile(r"</?Dialog\b[^>]*>")
USER_FACING_ATTRIBUTE = re.compile(
    r"\b(?:label|aria-label|placeholder|title)\s*=\s*"
    r"(?:[\"'`]([^\"'`]*)[\"'`]|\{[\"'`]([^\"'`]*)[\"'`]\})",
    re.IGNORECASE,
)
INTERNAL_INPUT_TERM = re.compile(
    r"(?:subject\s*id|tenant\s*id|membership\s*id|model\s*id|"
    r"version(?:\s*(?:id|number))?|result\s*code|trace\s*id|"
    r"主体标识|租户标识|成员标识|模型标识|内部标识|版本号|版本标识|结果码|追踪标识)",
    re.IGNORECASE,
)
INTERNAL_RESPONSE_FIELD = (
    r"(?:assistantVersionId|questionDigest|resultCode|traceId|details)"
)
VISIBLE_INTERNAL_EXPRESSION = re.compile(
    rf">\s*\{{[A-Za-z_$][\w$]*\.{INTERNAL_RESPONSE_FIELD}\b"
    rf"(?:\.[A-Za-z_$][\w$]*\([^}}]*\))?\}}\s*<"
)
USER_FACING_INTERNAL_EXPRESSION = re.compile(
    rf"\b(?:label|aria-label|placeholder|title)\s*=\s*\{{[^}}]*"
    rf"\.{INTERNAL_RESPONSE_FIELD}\b[^}}]*\}}",
    re.IGNORECASE,
)
VISIBLE_INTERNAL_LITERAL = re.compile(
    r">[^<]*(?:助手版本|问题摘要|结果码|追踪标识|异常类型|厂商原始响应)[^<]*<"
)
EDITABLE_CONTROL_TAG = re.compile(
    r"<(?:Input|Textarea|SpinButton|SearchBox|input|textarea)\b[^>]*>",
    re.DOTALL,
)
INTERNAL_EDITABLE_BINDING = re.compile(
    r"\b(?:name|value|defaultValue)\s*=\s*"
    r"(?:[\"'](?:subjectId|tenantId|membershipId|modelId|assistantVersionId|"
    r"versionNumber|resultCode|version)[\"']|"
    r"\{[^}]*(?<![\w$])(?:subjectId|tenantId|membershipId|modelId|assistantVersionId|"
    r"versionNumber|resultCode)(?![\w$])[^}]*\})",
    re.IGNORECASE,
)
HIDDEN_CONTROL = re.compile(r"\btype\s*=\s*[\"']hidden[\"']", re.IGNORECASE)


def source_files(root):
    files = []
    for relative_root in SPA_SOURCES:
        source_root = Path(root) / relative_root
        if source_root.is_dir():
            files.extend(path for path in source_root.rglob("*.tsx") if path.is_file())
    return sorted(files)


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
        for match in USER_FACING_ATTRIBUTE.finditer(source):
            prompt = match.group(1) or match.group(2) or ""
            if INTERNAL_INPUT_TERM.search(prompt):
                errors.append(f"禁止内部标识输入：{relative} 包含 {prompt!r}")
        for match in EDITABLE_CONTROL_TAG.finditer(source):
            control = match.group(0)
            if not HIDDEN_CONTROL.search(control) and INTERNAL_EDITABLE_BINDING.search(control):
                errors.append(f"禁止内部标识编辑控件：{relative}")
        if (
            VISIBLE_INTERNAL_EXPRESSION.search(source)
            or USER_FACING_INTERNAL_EXPRESSION.search(source)
            or VISIBLE_INTERNAL_LITERAL.search(source)
        ):
            errors.append(f"禁止内部响应信息：{relative} 渲染内部标识、摘要或结果字段")
    return errors


def verify(root):
    errors = scan(root)
    if errors:
        raise RuntimeError("\n".join(errors))
    print("前端产品门禁通过：分页、Dialog、内部标识输入与响应信息边界均符合要求")


def main(argv):
    if len(argv) != 2 or argv[1] != "verify":
        raise SystemExit("用法：frontend_product_guard.py verify")
    verify(Path.cwd())


if __name__ == "__main__":
    main(sys.argv)
