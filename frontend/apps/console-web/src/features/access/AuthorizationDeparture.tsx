import { useEffect, useRef, useState } from "react";
import { Button, Dialog, DialogSurface, DialogTitle } from "@fluentui/react-components";
import type { ControlPlaneClient } from "../../api/control-plane-client";

export type RegisterAuthorizationDeparture = (guard: (proceed: () => void) => void) => () => void;

/** 编辑离开保护只处理当前会话草稿；在途写入结束前不能卸载保存拥有者。 */
export function useAuthorizationDeparture(client: ControlPlaneClient, dirty: boolean, busy: boolean,
  register: RegisterAuthorizationDeparture, discard: () => void) {
  const [departure, setDeparture] = useState<(() => void)>();
  const [failure, setFailure] = useState("");
  const trigger = useRef<HTMLElement | null>(null);
  const epoch = client.currentSessionRevision();
  const latest = useRef({ busy, discard });
  useEffect(() => { latest.current = { busy, discard }; }, [busy, discard]);
  const current = () => client.hasSession() && client.currentSessionRevision() === epoch;
  useEffect(() => {
    if (!dirty && !busy) return;
    return register(proceed => {
      if (!client.hasSession() || client.currentSessionRevision() !== epoch || latest.current.busy) return;
      trigger.current = document.activeElement as HTMLElement;
      setFailure(""); setDeparture(() => proceed);
    });
  }, [busy, client, dirty, epoch, register]);
  function close() {
    setDeparture(undefined); setFailure("");
    window.requestAnimationFrame(() => { if (current() && trigger.current?.isConnected) trigger.current.focus(); });
  }
  function leave() {
    if (!current() || !departure || latest.current.busy) return;
    try { latest.current.discard(); setDeparture(undefined); departure(); }
    catch { setFailure("无法清除草稿，仍保留当前修改。"); }
  }
  return <Dialog open={Boolean(departure)} onOpenChange={(_, data) => { if (!data.open) close(); }}>
    <DialogSurface className="member-account-dialog member-unsaved-dialog" aria-labelledby="authorization-unsaved-title" backdrop={{ className: "member-account-backdrop" }}>
      <DialogTitle id="authorization-unsaved-title" className="member-dialog-title" action={null}>修改尚未保存</DialogTitle>
      <div className="member-dialog-content"><p>留在当前页继续编辑，或放弃本页修改后离开。</p><p role="alert">{failure}</p></div>
      <div className="member-dialog-actions"><Button onClick={close}>保留当前</Button><Button appearance="primary" onClick={leave}>放弃修改并离开</Button></div>
    </DialogSurface>
  </Dialog>;
}
