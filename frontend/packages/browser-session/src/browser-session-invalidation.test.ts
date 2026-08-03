import { describe, expect, it } from "vitest";
import {
  BrowserSessionInvalidationChannel,
  type BrowserSessionInvalidationPort,
} from "./browser-session-invalidation";

describe("BrowserSessionInvalidationChannel", () => {
  it("只广播会话失效事实，不携带 token、角色或身份详情", () => {
    const port = new FakePort();
    const channel = new BrowserSessionInvalidationChannel(
      "foundation-browser-session",
      () => port,
    );
    const received: number[] = [];
    channel.subscribe(() => received.push(1));

    channel.publishInvalidation();
    port.receive({ type: "session-invalidated", token: "must-not-pass" });
    port.receive({ type: "session-invalidated" });

    expect(port.messages).toEqual([{ type: "session-invalidated" }]);
    expect(received).toEqual([1]);
  });
});

class FakePort implements BrowserSessionInvalidationPort {
  onmessage: ((event: MessageEvent<unknown>) => void) | null = null;
  readonly messages: unknown[] = [];

  postMessage(message: unknown): void {
    this.messages.push(message);
  }

  close(): void {}

  receive(data: unknown): void {
    this.onmessage?.({ data } as MessageEvent<unknown>);
  }
}
