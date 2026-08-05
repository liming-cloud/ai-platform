import {
  Badge,
  Button,
  Field,
  Input,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  Text,
  Title3,
} from "@fluentui/react-components";
import { StatePanel } from "@foundation/design-system";
import { type FormEvent, useEffect, useState } from "react";
import {
  ApiRequestError,
  type ControlPlaneClient,
  type EvaluationRun,
} from "../../api/control-plane-client";

interface Failure {
  message: string;
  traceId: string;
}

/**
 * 评测控制台只允许选择已有业务资源并查看指标结论，私有评测内容始终不进入浏览器。
 */
export function EvaluationRunWorkspace({
  client,
  sessionRevision,
  onSessionChanged,
}: {
  client: ControlPlaneClient;
  sessionRevision: number;
  onSessionChanged: () => void;
}) {
  const [datasetId, setDatasetId] = useState("");
  const [assistantVersionId, setAssistantVersionId] = useState("");
  const [run, setRun] = useState<EvaluationRun>();
  const [failure, setFailure] = useState<Failure>();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    setRun(undefined);
    setFailure(undefined);
    setSubmitting(false);
  }, [sessionRevision]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!client.hasSession()) {
      setFailure({ message: "浏览器会话已经失效，请返回统一登录入口。", traceId: "" });
      return;
    }
    if (!isPositiveIdentifier(datasetId) || !isPositiveIdentifier(assistantVersionId)) {
      setFailure({ message: "请填写正整数形式的数据集和助手版本标识。", traceId: "" });
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      setRun(await client.executeEvaluationRun(datasetId, assistantVersionId));
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setSubmitting(false);
    }
  }

  if (!client.hasSession()) {
    return (
      <StatePanel
        kind="stale"
        title="浏览器会话已经失效"
        description="受保护的评测任务已清除，请返回统一登录入口。"
        actionLabel="返回登录入口"
        onAction={() => window.location.assign("/")}
      />
    );
  }

  return (
    <div className="evaluation-run-workspace">
      <div className="governance-toolbar">
        <Text>
          运行使用服务端私有数据集和受控助手版本；此页只展示聚合指标与质量门结论。
        </Text>
        <Button appearance="subtle" onClick={() => void logout(client, onSessionChanged)}>
          退出登录
        </Button>
      </div>
      <section className="evaluation-run-section" aria-labelledby="evaluation-run-heading">
        <div className="task-heading">
          <Title3 id="evaluation-run-heading">发起受限评测</Title3>
          <Text>输入已登记的数据集和助手版本标识；正文、样本、模型输出和证据明细不会显示。</Text>
        </div>
        <form className="evaluation-run-form" onSubmit={(event) => void submit(event)}>
          <Field label="数据集标识" required>
            <Input value={datasetId} onChange={(_, data) => setDatasetId(data.value)} inputMode="numeric" />
          </Field>
          <Field label="助手版本标识" required>
            <Input value={assistantVersionId} onChange={(_, data) => setAssistantVersionId(data.value)} inputMode="numeric" />
          </Field>
          <Button appearance="primary" type="submit" disabled={submitting}>
            {submitting ? "正在运行评测" : "运行评测"}
          </Button>
        </form>
      </section>
      {failure ? (
        <MessageBar intent="error">
          <MessageBarBody>
            <MessageBarTitle>评测运行未完成</MessageBarTitle>
            {failure.message}{failure.traceId ? `（追踪标识：${failure.traceId}）` : ""}
          </MessageBarBody>
        </MessageBar>
      ) : null}
      {run ? <EvaluationRunResult run={run} /> : null}
    </div>
  );
}

function EvaluationRunResult({ run }: { run: EvaluationRun }) {
  return (
    <section className="evaluation-run-section" aria-labelledby="evaluation-result-heading">
      <div className="operations-heading">
        <div>
          <Title3 id="evaluation-result-heading">评测结论</Title3>
          <Text>运行标识：{run.id}</Text>
        </div>
        <Badge appearance="filled" color={run.status === "PASSED" ? "success" : "danger"}>
          {run.status === "PASSED" ? "通过质量门" : "未通过质量门"}
        </Badge>
      </div>
      <dl className="evaluation-run-metrics">
        <Metric label="召回率（@10）" value={formatRate(run.recallAt10)} />
        <Metric label="引用正确率" value={formatRate(run.citationCorrectness)} />
        <Metric label="拒答正确率" value={formatRate(run.correctRefusalRate)} />
        <Metric label="失败样本" value={String(run.failedEntryCount)} />
        <Metric label="未授权证据" value={run.unauthorizedEvidence ? "有" : "无"} />
        <Metric label="指标可计算" value={run.metricsComputable ? "是" : "否"} />
      </dl>
      <Text size={200}>
        质量门：{run.gatePolicyVersion}；指标实现：{run.metricsImplementationVersion}；完成于：{formatTime(run.completedAt)}。
      </Text>
    </section>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function isPositiveIdentifier(value: string): boolean {
  return /^[1-9][0-9]*$/.test(value);
}

function formatRate(value: number): string {
  return `${(value * 100).toFixed(1)}%`;
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "medium",
    timeStyle: "medium",
  }).format(new Date(value));
}

function toFailure(error: unknown): Failure {
  if (error instanceof ApiRequestError) {
    return { message: error.message, traceId: error.traceId };
  }
  return { message: "请求处理失败，请稍后重试。", traceId: "" };
}

async function logout(client: ControlPlaneClient, onSessionChanged: () => void) {
  try {
    await client.logout();
  } finally {
    onSessionChanged();
  }
}
