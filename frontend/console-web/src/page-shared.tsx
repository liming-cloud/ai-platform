import { Text, Title2 } from '@fluentui/react-components';
import { AsyncState } from './async-state';
import type { RequestStatus } from './types';

export type Navigate = (path: string) => void;

export function PageHeading({ title, description }: { title: string; description: string; marker?: string }) {
  return (
    <header className="page-heading">
      <div><Title2 as="h1">{title}</Title2><Text>{description}</Text></div>
    </header>
  );
}

export function RequestFeedback({
  status,
  error,
  onRetry,
  loadingLabel,
}: {
  status: RequestStatus;
  error?: Error;
  onRetry?: () => void;
  loadingLabel?: string;
}) {
  return <AsyncState status={status} error={error} onRetry={onRetry} loadingLabel={loadingLabel} />;
}
