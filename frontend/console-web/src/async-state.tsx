import {
  Button,
  MessageBar,
  MessageBarBody,
  Skeleton,
  SkeletonItem,
  Text,
} from '@fluentui/react-components';
import type { RequestStatus } from './types';

interface AsyncStateProps {
  status: RequestStatus;
  error?: Error;
  loadingLabel?: string;
  onRetry?: () => void;
  retryLabel?: string;
}

export function AsyncState({
  status,
  error,
  loadingLabel = '正在读取实时数据',
  onRetry,
  retryLabel = '重试',
}: AsyncStateProps) {
  if (status === 'loading') {
    return <section className="async-state" role="status" aria-label={loadingLabel} aria-live="polite">
      <div className="async-state-copy">
        <Text weight="semibold">{loadingLabel}</Text>
        <Text size={200}>请稍候，数据就绪后会自动更新。</Text>
      </div>
      <Skeleton className="async-state-skeleton" data-testid="async-state-skeleton" aria-hidden="true" animation="wave">
        <SkeletonItem size={16} />
        <SkeletonItem size={16} />
        <SkeletonItem size={16} />
      </Skeleton>
    </section>;
  }

  if (status === 'error') {
    return <MessageBar intent="error" role="alert">
      <MessageBarBody>
        <div className="async-state-error">
          <span>{error?.message || '操作失败，请稍后重试。'}</span>
          {onRetry && <Button size="small" appearance="transparent" onClick={onRetry}>{retryLabel}</Button>}
        </div>
      </MessageBarBody>
    </MessageBar>;
  }

  return null;
}
