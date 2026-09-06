// 通过已安装 OpenDesign 的 MCP 保存当前原型，再逐文件核对其预览服务返回的内容。
const fs = require("node:fs");
const path = require("node:path");
const os = require("node:os");
const crypto = require("node:crypto");
const { spawn } = require("node:child_process");
const root = path.resolve(__dirname, "..");
const project = "249f81f2-6f1a-46ff-988c-da3cdedb35e7";
const daemon = process.env.OPENDESIGN_DAEMON_URL || "http://127.0.0.1:65153";
const output = process.env.OPENDESIGN_SAVE_REPORT || path.join(os.tmpdir(), "wp0016-opendesign-save.json");
const lock = require("../open-design.lock.json");
const digest = content => crypto.createHash("sha256").update(content).digest("hex");

async function main() {
  if (!["127.0.0.1", "localhost"].includes(new URL(daemon).hostname)) throw Error("只允许当前计算机的 OpenDesign 服务");
  const child = spawn(lock.mcp.command, [...lock.mcp.args, "--daemon-url", daemon], {
    env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", OD_SIDECAR_IPC_PATH: process.env.OD_SIDECAR_IPC_PATH || "/tmp/open-design/ipc/release-stable/daemon.sock" },
    stdio: ["pipe", "pipe", "ignore"],
  });
  let sequence = 0;
  let buffer = "";
  const pending = new Map();
  child.stdout.on("data", chunk => {
    buffer += chunk;
    let newline;
    while ((newline = buffer.indexOf("\n")) >= 0) {
      const line = buffer.slice(0, newline); buffer = buffer.slice(newline + 1);
      let message;
      try { message = JSON.parse(line); } catch { continue; }
      const request = pending.get(message.id);
      if (!request) continue;
      pending.delete(message.id); clearTimeout(request.timeout);
      message.error ? request.reject(Error(JSON.stringify(message.error))) : request.resolve(message.result);
    }
  });
  child.on("exit", () => { for (const request of pending.values()) { clearTimeout(request.timeout); request.reject(Error("OpenDesign MCP 提前退出")); } pending.clear(); });
  child.on("error", error => { for (const request of pending.values()) { clearTimeout(request.timeout); request.reject(error); } pending.clear(); });
  const send = message => child.stdin.write(JSON.stringify({ jsonrpc: "2.0", ...message }) + "\n");
  const request = (method, params) => new Promise((resolve, reject) => {
    const id = ++sequence;
    const timeout = setTimeout(() => { pending.delete(id); reject(Error(`OpenDesign 请求超时：${method}`)); }, 20000);
    pending.set(id, { resolve, reject, timeout }); send({ id, method, params });
  });
  const call = async (name, args) => {
    const result = await request("tools/call", { name, arguments: args });
    if (result.isError) throw Error(`${name} 失败：${JSON.stringify(result.content)}`);
    return result;
  };
  try {
    await request("initialize", { protocolVersion: "2024-11-05", capabilities: {}, clientInfo: { name: "wp0016-prototype-save", version: "1" } });
    send({ method: "notifications/initialized" });
    const result = await call("get_project", { project });
    const metadata = JSON.parse(result.content.find(item => item.type === "text").text);
    const resolvedDir = metadata.resolvedDir || metadata.metadata?.baseDir;
    if (fs.realpathSync(resolvedDir) !== fs.realpathSync(root)) throw Error("OpenDesign 项目目录与本工作包不一致，停止保存");
    const files = [];
    const collect = directory => {
      for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
        if (entry.name === "artifacts" || entry.name.startsWith(".")) continue;
        const file = path.join(directory, entry.name);
        if (entry.isDirectory()) collect(file);
        else if (entry.isFile() && /\.(?:html|js|mjs|cjs|css|json|md|svg|txt)$/.test(entry.name)) files.push(path.relative(root, file));
      }
    };
    collect(root);
    const verified = [];
    for (const file of files.sort()) {
      const bytes = fs.readFileSync(path.join(root, file));
      await call("write_file", { project, path: file, content: bytes.toString("utf8"), encoding: "utf8" });
      const response = await fetch(`${daemon}/api/projects/${project}/raw/${file.split(path.sep).map(encodeURIComponent).join("/")}`, { signal: AbortSignal.timeout(15000), cache: "no-store" });
      if (!response.ok || digest(Buffer.from(await response.arrayBuffer())) !== digest(bytes)) throw Error(`OpenDesign 保存后内容校验失败：${file}`);
      verified.push({ path: file, bytes: bytes.length, sha256: digest(bytes) });
    }
    const report = { status: "PASS", generatedAt: new Date().toISOString(), project, name: metadata.name, version: lock.release.version, resolvedDir, previewUrl: metadata.previewUrl, studioUrl: metadata.studioUrl, savedBy: "OpenDesign MCP write_file", verification: "逐文件比较原始预览响应与提交前代码的 SHA-256", files: verified, screenshotCount: 0 };
    fs.writeFileSync(output, JSON.stringify(report, null, 2) + "\n");
    console.log(JSON.stringify({ status: report.status, fileCount: verified.length, output, previewUrl: report.previewUrl }, null, 2));
  } finally { child.stdin.end(); child.kill(); }
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
