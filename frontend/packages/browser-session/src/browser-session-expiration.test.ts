import { describe, expect, it } from "vitest";
import {
  BrowserSessionExpirationMonitor,
  type BrowserSessionTimer,
} from "./browser-session-expiration";

describe("BrowserSessionExpirationMonitor", () => {
  it("到期时只触发当前会话的重新验证，并在替换或关闭后取消旧定时器", () => {
    const timer = new FakeTimer();
    const monitor = new BrowserSessionExpirationMonitor(timer);
    const elapsed: string[] = [];

    monitor.schedule(3, () => elapsed.push("旧会话"));
    monitor.schedule(5, () => elapsed.push("当前会话"));
    timer.elapse(3_000);
    monitor.close();
    timer.elapse(5_000);

    expect(elapsed).toEqual([]);
    expect(timer.scheduledDelays).toEqual([3_000, 5_000]);
  });

  it("只在当前会话到期后触发一次重新验证", () => {
    const timer = new FakeTimer();
    const monitor = new BrowserSessionExpirationMonitor(timer);
    const elapsed: string[] = [];

    monitor.schedule(2, () => elapsed.push("当前会话"));
    timer.elapse(1_999);
    expect(elapsed).toEqual([]);

    timer.elapse(1);
    expect(elapsed).toEqual(["当前会话"]);
  });
});

class FakeTimer implements BrowserSessionTimer {
  readonly scheduledDelays: number[] = [];
  private readonly tasks = new Map<number, { deadline: number; action: () => void }>();
  private nextId = 1;
  private now = 0;

  set(delayMillis: number, action: () => void): number {
    const id = this.nextId++;
    this.scheduledDelays.push(delayMillis);
    this.tasks.set(id, { deadline: this.now + delayMillis, action });
    return id;
  }

  clear(id: number): void {
    this.tasks.delete(id);
  }

  elapse(delayMillis: number): void {
    this.now += delayMillis;
    for (const [id, task] of [...this.tasks]) {
      if (task.deadline <= this.now) {
        this.tasks.delete(id);
        task.action();
      }
    }
  }
}
