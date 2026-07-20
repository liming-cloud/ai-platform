import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FluentProvider } from '@fluentui/react-components';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AsyncState } from '../async-state';
import { enterpriseLightTheme } from '../theme';

afterEach(cleanup);

describe('AsyncState', () => {
  it('announces loading work and presents a stable skeleton region', () => {
    render(
      <FluentProvider theme={enterpriseLightTheme}>
        <AsyncState status="loading" loadingLabel="正在读取租户配置" />
      </FluentProvider>,
    );

    expect(screen.getByRole('status', { name: '正在读取租户配置' })).toBeInTheDocument();
    expect(screen.getByTestId('async-state-skeleton')).toBeInTheDocument();
  });

  it('offers a retry action when recovery is available', async () => {
    const retry = vi.fn();
    render(
      <FluentProvider theme={enterpriseLightTheme}>
        <AsyncState status="error" error={new Error('租户列表读取失败')} onRetry={retry} />
      </FluentProvider>,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('租户列表读取失败');
    await userEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(retry).toHaveBeenCalledTimes(1);
  });
});
