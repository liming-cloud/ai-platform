const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { spawn } = require("node:child_process");
const { test } = require("node:test");

const scripts = ["form", "pagination", "partial"];
const artifacts = { "failure-layout.png": "合成截图证据", "run.log": "合成日志", "evidence.json": '{"synthetic":true}' };
const helper = path.join(__dirname, "browser-test-cache.cjs");

function fixture(t) {
  const root = fs.mkdtempSync("/private/tmp/browser-cache-test-");
  fs.chmodSync(root, 0o700);
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  // 替身只占据 Vite／Playwright 的外部 I/O 边界，实际执行三个生产测试入口及其退出逻辑。
  fs.writeFileSync(path.join(root, "vite.mjs"), `
    import fs from "node:fs";
    import path from "node:path";
    export async function createServer(options) {
      const cache = options.cacheDir ?? path.join(options.root, ".vite");
      fs.mkdirSync(path.join(cache, "deps"), { recursive: true });
      fs.writeFileSync(path.join(cache, "deps", "generated.js"), "可重建依赖");
      let interval;
      return {
        httpServer: { address: () => ({ port: 3000 }) },
        async listen() { interval = setInterval(() => {}, 1000); },
        async close() {
          clearInterval(interval);
          await new Promise(resolve => setTimeout(resolve, 10));
          fs.mkdirSync(cache, { recursive: true });
          fs.writeFileSync(path.join(cache, "last-write.js"), "关闭期间的最后一次缓存写入");
          fs.writeFileSync(path.join(options.root, "server-closed.log"), "closed");
        }
      };
    }
  `);
  fs.writeFileSync(path.join(root, "react.mjs"), "export default () => ({});\n");
  fs.writeFileSync(path.join(root, "preload.cjs"), `
    const fs = require("node:fs"), path = require("node:path"), Module = require("node:module");
    const root = process.env.CACHE_TEST_ROOT, mode = process.env.CACHE_TEST_MODE;
    const mkdtemp = fs.mkdtempSync, write = fs.writeFileSync;
    fs.mkdtempSync = function(prefix, ...rest) {
      if (!/wp0019-(form|pagination|partial)-source-/.test(prefix)) return mkdtemp(prefix, ...rest);
      const temporary = mkdtemp(path.join(root, "artifacts-"));
      write(path.join(root, "created-path.txt"), temporary);
      fs.mkdirSync(path.join(temporary, ".vite", "deps"), { recursive: true });
      write(path.join(temporary, ".vite", "deps", "generated.js"), "可重建依赖");
      for (const [name, value] of Object.entries(${JSON.stringify(artifacts)})) write(path.join(temporary, name), value);
      return temporary;
    };
    fs.writeFileSync = function(file, ...rest) {
      if (mode === "setup-error" && path.basename(file) === "harness.tsx") throw new Error("合成启动失败");
      return write(file, ...rest);
    };
    const load = Module._load;
    Module._load = function(id, ...rest) {
      if (id === "playwright/package.json") return { version: "1.62.1" };
      if (id === "playwright") return { chromium: { async launch() {
        if (mode === "launch-error") throw new Error("合成浏览器启动失败");
        process.stdout.write("CACHE_TEST_READY\\n");
        return new Promise(() => {});
      } } };
      return load.call(this, id, ...rest);
    };
    const createRequire = Module.createRequire;
    Module.createRequire = function(filename) {
      const result = createRequire(filename);
      if (String(filename).endsWith("frontend/package.json")) result.resolve = id => path.join(root, id === "vite" ? "vite.mjs" : "react.mjs");
      return result;
    };
  `);
  return root;
}

function runChild(root, args, mode) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, args, {
      env: { ...process.env, CACHE_TEST_ROOT: root, CACHE_TEST_MODE: mode },
      stdio: ["ignore", "pipe", "pipe"],
    });
    let output = "", signalled = false;
    const timeout = setTimeout(() => { child.kill("SIGKILL"); reject(new Error("缓存退出回归超时")); }, 12000);
    child.stdout.on("data", data => {
      output += data;
      if (!signalled && mode.startsWith("SIG") && output.includes("CACHE_TEST_READY")) {
        signalled = true; child.kill(mode);
      }
    });
    child.stderr.on("data", data => { output += data; });
    child.once("error", error => { clearTimeout(timeout); reject(error); });
    child.once("close", (code, signal) => { clearTimeout(timeout); resolve({ code, signal, output }); });
  });
}

function assertPreserved(temporary) {
  assert.equal(fs.existsSync(path.join(temporary, ".vite")), false, "退出后不得残留可重建缓存");
  for (const [name, value] of Object.entries(artifacts)) assert.equal(fs.readFileSync(path.join(temporary, name), "utf8"), value);
}

for (const script of scripts) {
  for (const mode of ["setup-error", "launch-error", "SIGINT", "SIGTERM", "SIGHUP"]) {
    test(`${script} ${mode} 退出清理缓存并保留验收产物`, async t => {
      const root = fixture(t);
      const result = await runChild(root, ["--require", path.join(root, "preload.cjs"),
        path.join(__dirname, `platform-connection-${script}.browser.cjs`)], mode);
      assert.ok(fs.existsSync(path.join(root, "created-path.txt")), result.output);
      const temporary = fs.readFileSync(path.join(root, "created-path.txt"), "utf8");
      assertPreserved(temporary);
      assert.equal(result.code, ({ SIGINT: 130, SIGTERM: 143, SIGHUP: 129 })[mode] ?? 1, result.output);
      if (mode !== "setup-error") assert.equal(fs.readFileSync(path.join(temporary, "server-closed.log"), "utf8"), "closed");
    });
  }
}

for (const mode of ["normal", "close-error", "uncaught", "rejection", "explicit-exit"]) {
  test(`${mode} 退出清理缓存，保留原失败结果和证据`, async t => {
    const root = fixture(t), temporary = path.join(root, "artifacts");
    fs.mkdirSync(path.join(temporary, ".vite", "deps"), { recursive: true });
    fs.writeFileSync(path.join(temporary, ".vite", "deps", "generated.js"), "缓存");
    for (const [name, value] of Object.entries(artifacts)) fs.writeFileSync(path.join(temporary, name), value);
    const result = await runChild(root, ["--unhandled-rejections=strict", "--eval", `
      const fs = require("node:fs"), path = require("node:path");
      const { createBrowserTestCache } = require(${JSON.stringify(helper)});
      const temporary = ${JSON.stringify(temporary)};
      const cache = createBrowserTestCache(temporary, async () => {
        await new Promise(resolve => setTimeout(resolve, 10));
        fs.writeFileSync(path.join(temporary, ".vite", "late.js"), "关闭完成前的写入");
        if (${JSON.stringify(mode)} === "close-error") throw new Error("合成资源关闭失败");
      });
      if (${JSON.stringify(mode)} === "explicit-exit") process.exit(7);
      else if (${JSON.stringify(mode)} === "uncaught") setImmediate(() => { throw new Error("合成未捕获异常"); });
      else if (${JSON.stringify(mode)} === "rejection") Promise.reject(new Error("合成未处理拒绝"));
      else cache.close().catch(() => { process.exitCode = 1; });
    `], mode);
    assertPreserved(temporary);
    assert.equal(result.code, mode === "normal" ? 0 : mode === "explicit-exit" ? 7 : 1, result.output);
  });
}

test("信号退出遇到资源关闭悬挂时仍有界回收缓存", async t => {
  const root = fixture(t), temporary = path.join(root, "artifacts");
  fs.mkdirSync(path.join(temporary, ".vite", "deps"), { recursive: true });
  fs.writeFileSync(path.join(temporary, ".vite", "deps", "generated.js"), "缓存");
  for (const [name, value] of Object.entries(artifacts)) fs.writeFileSync(path.join(temporary, name), value);
  const result = await runChild(root, ["--eval", `
    require(${JSON.stringify(helper)}).createBrowserTestCache(${JSON.stringify(temporary)}, () => new Promise(() => {}));
    setInterval(() => {}, 1000);
    process.stdout.write("CACHE_TEST_READY\\n");
  `], "SIGTERM");
  assertPreserved(temporary);
  assert.equal(result.code, 143, result.output);
});
