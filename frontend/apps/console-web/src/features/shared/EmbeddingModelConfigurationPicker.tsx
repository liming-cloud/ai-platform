import { Button, Field, Select, Text } from "@fluentui/react-components";
import { useCallback, useEffect, useState } from "react";
import type { ControlPlaneClient } from "../../api/control-plane-client";
import {
  failedModelConfigurationPage,
  initialModelConfigurationPage,
  loadedModelConfigurationPage,
} from "./embedding-model-configuration-page";
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
  onSessionExpired,
  onSelectionAvailabilityChange,
}: {
  client: ControlPlaneClient;
  page: number;
  selectedId?: string;
  disabled: boolean;
  onChange: (page: number, selectedId?: string) => void;
  /** 仅在已确认浏览器会话失效时清理父任务状态。 */
  onSessionExpired?: (error: unknown) => boolean;
  onSelectionAvailabilityChange?: (available: boolean) => void;
}) {
  const [modelConfigurationPage, setModelConfigurationPage] = useState(
    initialModelConfigurationPage,
  );

  const load = useCallback(async () => {
    setModelConfigurationPage((current) => ({
      ...current,
      loading: true,
      failed: false,
    }));
    try {
      const result = await client.listModelConfigurations(page);
      setModelConfigurationPage(loadedModelConfigurationPage(result));
    } catch (error) {
      if (!onSessionExpired?.(error)) {
        setModelConfigurationPage(failedModelConfigurationPage());
      }
    }
  }, [client, onSessionExpired, page]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timeout);
  }, [load]);

  const eligible = modelConfigurationPage.configurations.filter((configuration) => (
    configuration.modelType === "EMBEDDING"
      && configuration.status === "ACTIVE"
      && configuration.credentialConfigured
  ));

  useEffect(() => {
    onSelectionAvailabilityChange?.(
      !modelConfigurationPage.loading
        && !modelConfigurationPage.failed
        && eligible.some((configuration) => configuration.id === selectedId),
    );
  }, [eligible, modelConfigurationPage.failed, modelConfigurationPage.loading, onSelectionAvailabilityChange, selectedId]);

  return (
    <div className="governed-file-form">
      <Field label="嵌入模型配置" required>
        <Select
          value={selectedId ?? ""}
          disabled={disabled || modelConfigurationPage.loading || modelConfigurationPage.failed}
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
        total={modelConfigurationPage.total}
        hasPrevious={modelConfigurationPage.hasPrevious}
        hasNext={modelConfigurationPage.hasNext}
        disabled={disabled || modelConfigurationPage.loading}
        onPrevious={() => onChange(page - 1, undefined)}
        onNext={() => onChange(page + 1, undefined)}
      />
      {modelConfigurationPage.loading ? <Text size={200}>正在读取可用嵌入模型。</Text> : null}
      {modelConfigurationPage.failed ? <div className="task-actions"><Text size={200}>模型配置暂时不可用。</Text><Button appearance="subtle" disabled={disabled} onClick={() => void load()}>重新加载模型配置</Button></div> : null}
      {!modelConfigurationPage.loading && !modelConfigurationPage.failed && eligible.length === 0 ? <Text size={200}>当前页没有可用嵌入模型，请翻页或先完成模型配置。</Text> : null}
    </div>
  );
}
