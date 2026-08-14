import type { ModelConfiguration } from "../../api/control-plane-client";

/**
 * 索引页中嵌入模型配置列表的本地读取状态。
 *
 * <p>模型列表读取只是已发布数据源后的辅助读取。失败必须保留在选择器内，以便重试，不能把已完成的发布工作流改写为全局失败。</p>
 */
export interface ModelConfigurationPage {
  configurations: ModelConfiguration[];
  hasPrevious: boolean;
  hasNext: boolean;
  total: number;
  loading: boolean;
  failed: boolean;
}

/** 索引页首次加载嵌入模型配置时的保守状态。 */
export const initialModelConfigurationPage: ModelConfigurationPage = {
  configurations: [],
  hasPrevious: false,
  hasNext: false,
  total: 0,
  loading: true,
  failed: false,
};

/** 把服务器分页结果转换为可选择的本地读取状态。 */
export function loadedModelConfigurationPage(result: {
  items: ModelConfiguration[];
  hasPrevious: boolean;
  hasNext: boolean;
  totalElements: number;
}): ModelConfigurationPage {
  return {
    configurations: result.items,
    hasPrevious: result.hasPrevious,
    hasNext: result.hasNext,
    total: result.totalElements,
    loading: false,
    failed: false,
  };
}

/**
 * 生成仅影响当前列表的可恢复读取失败状态。
 *
 * <p>清空过期页内容，避免操作者据此选择已经失效的模型配置。</p>
 */
export function failedModelConfigurationPage(): ModelConfigurationPage {
  return {
    ...initialModelConfigurationPage,
    loading: false,
    failed: true,
  };
}
