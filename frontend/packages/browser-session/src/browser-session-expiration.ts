/** 浏览器会话到期前的最小定时 seam，避免各产品入口自行处理失效时序。 */
export interface BrowserSessionTimer {
  set(delayMillis: number, action: () => void): number;
  clear(id: number): void;
}

/**
 * 在服务端声明的最大会话年龄到达时重新验证 HttpOnly cookie。
 * 不读取 cookie、token 或身份事实；实际授权仍由服务端恢复结果决定。
 */
export class BrowserSessionExpirationMonitor {
  private scheduledId: number | undefined;

  constructor(private readonly timer: BrowserSessionTimer = browserTimer) {}

  schedule(maximumSessionAgeSeconds: number, onElapsed: () => void): void {
    if (!Number.isSafeInteger(maximumSessionAgeSeconds) || maximumSessionAgeSeconds <= 0) {
      throw new Error("浏览器会话最大年龄不正确");
    }
    this.close();
    this.scheduledId = this.timer.set(maximumSessionAgeSeconds * 1_000, () => {
      this.scheduledId = undefined;
      onElapsed();
    });
  }

  close(): void {
    if (this.scheduledId !== undefined) {
      this.timer.clear(this.scheduledId);
      this.scheduledId = undefined;
    }
  }
}

const browserTimer: BrowserSessionTimer = {
  set(delayMillis, action) {
    return window.setTimeout(action, delayMillis);
  },
  clear(id) {
    window.clearTimeout(id);
  },
};
