import { Button, Field, Select, Text } from "@fluentui/react-components";
import { useCallback, useEffect, useState } from "react";
import type { ControlPlaneClient, ModelConfiguration } from "../../api/control-plane-client";
import { PagedResourceNavigation } from "./PagedResourceNavigation";

/**
 * 索引任务的受控嵌入模型选择器。
 *
 * <p>仅显示当前服务端页中已启用且已配置凭据的嵌入模型；调用方保有路由、权限和创建索引职责。本组件不会根据排序自动选择模型。</p>
 */
export function EmbeddingModelConfigurationPicker({
  client,
  page,
  selectedId,
  disabled,
  onChange,
  onFailure,
}: {
  client: ControlPlaneClient;
  page: number;
  selectedId?: string;
  disabled: boolean;
  onChange: (page: number, selectedId?: string) => void;
  onFailure: (error: unknown) => boolean;
}) {
  const [configurations, setConfigurations] = useState<ModelConfiguration[]>([]);
  const [hasPrevious, setHasPrevious] = useState(false);
  const [hasNext, setHasNext] = useState(false);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    try {
      const result = await client.listModelConfigurations(page);
      setConfigurations(result.items);
      setHasPrevious(result.hasPrevious);
      setHasNext(result.hasNext);
      setTotal(result.totalElements);
    } catch (error) {
      setConfigurations([]);
      setHasPrevious(false);
      setHasNext(false);
      setTotal(0);
      if (!onFailure(error)) {
        setFailed(true);
      }
    } finally {
      setLoading(false);
    }
  }, [client, onFailure, page]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timeout);
  }, [load]);

  const eligible = configurations.filter((configuration) => (
    configuration.modelType === "EMBEDDING"
      && configuration.status === "ACTIVE"
      && configuration.credentialConfigured
  ));

  return (
    <div className="governed-file-form">
      <Field label="嵌入模型配置" required>
        <Select
          value={selectedId ?? ""}
          disabled={disabled || loading || failed}
          onChange={(_, data) => onChange(page, data.value || undefined)}
        >
          <option value="">请选择可用嵌入模型</option>
          {eligible.map((configuration) => (
            <option key={configuration.id} value={configuration.id}>{configuration.displayName}</option>
          ))}
        </Select>
      </Field>
      <PagedResourceNavigation
        label="模型配置"
        page={page}
        total={total}
        hasPrevious={hasPrevious}
        hasNext={hasNext}
        disabled={disabled || loading}
        onPrevious={() => onChange(page - 1, undefined)}
        onNext={() => onChange(page + 1, undefined)}
      />
      {loading ? <Text size={200}>正在读取可用嵌入模型。</Text> : null}
      {failed ? <div className="task-actions"><Text size={200}>模型配置暂时不可用。</Text><Button appearance="subtle" disabled={disabled} onClick={() => void load()}>重新加载模型配置</Button></div> : null}
      {!loading && !failed && eligible.length === 0 ? <Text size={200}>当前页没有可用嵌入模型，请翻页或先完成模型配置。</Text> : null}
    </div>
  );
}
