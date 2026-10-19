const fs = require("node:fs");
const path = require("node:path");

/** 只回收本轮 Vite 缓存；截图、日志和验收证据继续留在隔离目录。 */
function createBrowserTestCache(temporary, closeResources) {
  const cacheDir = path.join(temporary, ".vite");
  const removeCache = () => fs.rmSync(cacheDir, { recursive: true, force: true });
  let closing;
  let stopping = false;
  // Vite 关闭期间仍可能完成缓存写入，必须等资源关闭后再删除；关闭失败也不能跳过删除。
  const close = () => closing ??= Promise.resolve().then(closeResources).finally(removeCache);
  // 启动失败、未捕获异常及显式退出也经过同步兜底；退出回调不能依赖异步 I/O。
  process.once("exit", removeCache);
  const stop = code => {
    if (stopping) return;
    stopping = true;
    // 资源关闭若悬挂，仍在五秒内退出并由同步兜底删除缓存，不把进程永久留在后台。
    const timeout = setTimeout(() => process.exit(code), 5000);
    timeout.unref();
    close().catch(() => { process.stderr.write("浏览器测试资源关闭失败。\n"); }).finally(() => {
      clearTimeout(timeout);
      process.exit(code);
    });
  };
  process.on("SIGINT", () => stop(130));
  process.on("SIGTERM", () => stop(143));
  process.on("SIGHUP", () => stop(129));
  return { cacheDir, close };
}

module.exports = { createBrowserTestCache };
