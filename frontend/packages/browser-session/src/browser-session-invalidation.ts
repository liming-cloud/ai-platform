/** 可替换的 `BroadcastChannel` 最小端口，便于测试且不传递浏览器会话本身。 */
export interface BrowserSessionInvalidationPort {
  onmessage: ((event: MessageEvent<unknown>) => void) | null;
  postMessage(message: unknown): void;
  close(): void;
}

type BrowserSessionInvalidationPortFactory = (
  name: string,
) => BrowserSessionInvalidationPort;

const INVALIDATION_MESSAGE = Object.freeze({ type: "session-invalidated" });

/**
 * 同源页签间只同步会话已失效这一事实。消息不得携带 token、身份、角色或任何能力内容。
 */
export class BrowserSessionInvalidationChannel {
  private readonly listeners = new Set<() => void>();
  private readonly port: BrowserSessionInvalidationPort;

  constructor(
    name = "foundation-browser-session",
    createPort: BrowserSessionInvalidationPortFactory = (channelName) =>
      new BroadcastChannel(channelName),
  ) {
    this.port = createPort(name);
    this.port.onmessage = (event) => {
      if (!isInvalidationMessage(event.data)) {
        return;
      }
      for (const listener of this.listeners) {
        listener();
      }
    };
  }

  publishInvalidation(): void {
    this.port.postMessage(INVALIDATION_MESSAGE);
  }

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  close(): void {
    this.listeners.clear();
    this.port.close();
  }
}

function isInvalidationMessage(value: unknown): boolean {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const message = value as Record<string, unknown>;
  return Object.keys(message).length === 1 && message.type === "session-invalidated";
}
