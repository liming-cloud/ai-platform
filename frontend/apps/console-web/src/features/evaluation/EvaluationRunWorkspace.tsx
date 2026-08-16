import {
  Badge, Button, Field, MessageBar, MessageBarBody, MessageBarTitle,
  Radio, RadioGroup, Spinner, Text, Title3,
} from "@fluentui/react-components";
import { StatePanel } from "@foundation/design-system";
import { type FormEvent, useEffect, useState } from "react";
import {
  type ControlPlaneClient, type EvaluationAssistantCandidate,
  type EvaluationDatasetCandidate, type EvaluationRun, presentApiError,
} from "../../api/control-plane-client";

/** 评测控制台只允许选择已有业务资源并查看指标结论，私有评测内容始终不进入浏览器。 */
export function EvaluationRunWorkspace({ client, sessionRevision, onSessionChanged }: {
  client: ControlPlaneClient;
  sessionRevision: number;
  onSessionChanged: () => void;
}) {
  if (!client.hasSession()) {
    return <StatePanel kind="stale" title="浏览器会话已经失效"
      description="受保护的评测任务已清除，请返回统一登录入口。"
      actionLabel="返回登录入口" onAction={() => window.location.assign("/")} />;
  }
  return <EvaluationRunForm key={sessionRevision} client={client} onSessionChanged={onSessionChanged} />;
}

/** 会话修订变化时重建局部选择和运行结果，避免旧租户资源残留。 */
function EvaluationRunForm({ client, onSessionChanged }: {
  client: ControlPlaneClient;
  onSessionChanged: () => void;
}) {
  const [datasets, setDatasets] = useState<EvaluationDatasetCandidate[]>([]);
  const [assistants, setAssistants] = useState<EvaluationAssistantCandidate[]>([]);
  const [datasetReference, setDatasetReference] = useState("");
  const [assistantVersionReference, setAssistantVersionReference] = useState("");
  const [datasetPage, setDatasetPage] = useState(1);
  const [assistantPage, setAssistantPage] = useState(1);
  const [datasetHasNext, setDatasetHasNext] = useState(false);
  const [assistantHasNext, setAssistantHasNext] = useState(false);
  const [run, setRun] = useState<EvaluationRun>();
  const [failure, setFailure] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let active = true;
    const timeout = window.setTimeout(() => {
      setLoading(true);
      setFailure(undefined);
      void Promise.all([
      client.listEvaluationDatasetCandidates(datasetPage),
      client.listEvaluationAssistantCandidates(assistantPage),
      ]).then(([datasetResult, assistantResult]) => {
      if (!active) return;
      setDatasets(datasetResult.items);
      setAssistants(assistantResult.items);
      setDatasetHasNext(datasetResult.hasNext);
      setAssistantHasNext(assistantResult.hasNext);
      setDatasetReference((current) => datasetResult.items.some((item) => item.datasetReference === current)
        ? current : datasetResult.items[0]?.datasetReference ?? "");
      setAssistantVersionReference((current) => assistantResult.items.some((item) => item.assistantVersionId === current)
        ? current : assistantResult.items[0]?.assistantVersionId ?? "");
      }).catch((error: unknown) => {
      if (active) setFailure(presentApiError(error, "加载评测资源").description);
      }).finally(() => {
      if (active) setLoading(false);
      });
    }, 0);
    return () => { active = false; window.clearTimeout(timeout); };
  }, [assistantPage, client, datasetPage]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!client.hasSession()) {
      setFailure("浏览器会话已经失效，请返回统一登录入口。");
      return;
    }
    if (!datasetReference || !assistantVersionReference) {
      setFailure("请先选择一个已发布数据集和可用助手，再运行评测。");
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      setRun(await client.executeEvaluationRun(datasetReference, assistantVersionReference));
    } catch (error) {
      setFailure(presentApiError(error, "运行评测").description);
    } finally {
      setSubmitting(false);
    }
  }

  return <div className="evaluation-run-workspace">
    <div className="governance-toolbar">
      <Text>选择当前租户已发布的数据集和助手；正文、模型输出与证据明细不会进入浏览器。</Text>
      <Button appearance="subtle" onClick={() => void logout(client, onSessionChanged)}>退出登录</Button>
    </div>
    <section className="evaluation-run-section" aria-labelledby="evaluation-run-heading">
      <div className="task-heading">
        <Title3 id="evaluation-run-heading">发起受限评测</Title3>
        <Text>系统仅展示可理解的资源摘要，内部定位信息由客户端安全传递。</Text>
      </div>
      {loading ? <Spinner label="正在加载可用评测资源" /> :
        <form className="evaluation-run-form" onSubmit={(event) => void submit(event)}>
          <Field label="已发布数据集" required>
            <RadioGroup value={datasetReference} onChange={(_, data) => setDatasetReference(data.value)}>
              {datasets.map((dataset) => <Radio key={dataset.datasetReference}
                value={dataset.datasetReference}
                label={`${dataset.entryCount} 条合成样本 · ${formatTime(dataset.createdAt)}`} />)}
            </RadioGroup>
          </Field>
          <Pager page={datasetPage} hasNext={datasetHasNext} onChange={setDatasetPage} label="数据集" />
          <Field label="可用助手" required>
            <RadioGroup value={assistantVersionReference} onChange={(_, data) => setAssistantVersionReference(data.value)}>
              {assistants.map((assistant) => <Radio key={assistant.assistantVersionId}
                value={assistant.assistantVersionId}
                label={`${assistant.displayName} · 发布于 ${formatTime(assistant.publishedAt)}`} />)}
            </RadioGroup>
          </Field>
          <Pager page={assistantPage} hasNext={assistantHasNext} onChange={setAssistantPage} label="助手" />
          {datasets.length === 0 || assistants.length === 0 ?
            <MessageBar intent="warning"><MessageBarBody>当前没有完整的评测资源，请先发布数据集和助手。</MessageBarBody></MessageBar> : null}
          <Button appearance="primary" type="submit"
            disabled={submitting || datasets.length === 0 || assistants.length === 0}>
            {submitting ? "正在运行评测" : "运行评测"}
          </Button>
        </form>}
    </section>
    {failure ? <MessageBar intent="error"><MessageBarBody>
      <MessageBarTitle>评测运行未完成</MessageBarTitle>{failure}
    </MessageBarBody></MessageBar> : null}
    {run ? <EvaluationRunResult run={run} /> : null}
  </div>;
}

function Pager({ page, hasNext, onChange, label }: {
  page: number; hasNext: boolean; onChange: (page: number) => void; label: string;
}) {
  return <div className="resource-selector-pager" aria-label={`${label}分页`}>
    <Button appearance="subtle" disabled={page === 1} onClick={() => onChange(page - 1)}>上一页</Button>
    <Text size={200}>第 {page} 页</Text>
    <Button appearance="subtle" disabled={!hasNext} onClick={() => onChange(page + 1)}>下一页</Button>
  </div>;
}

function EvaluationRunResult({ run }: { run: EvaluationRun }) {
  return <section className="evaluation-run-section" aria-labelledby="evaluation-result-heading">
    <div className="operations-heading">
      <Title3 id="evaluation-result-heading">评测结论</Title3>
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
    <Text size={200}>评测完成于 {formatTime(run.completedAt)}。</Text>
  </section>;
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>;
}

function formatRate(value: number): string { return `${(value * 100).toFixed(1)}%`; }

function formatTime(value: string): string {
  return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

async function logout(client: ControlPlaneClient, onSessionChanged: () => void) {
  try { await client.logout(); } finally { onSessionChanged(); }
}
