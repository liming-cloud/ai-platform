import { Button, Text, Title2 } from '@fluentui/react-components';
import { LockClosed20Regular } from '@fluentui/react-icons';
import type { Navigate } from './page-shared';

export function ErrorPage({ kind, navigate, fallbackPath }: { kind: '403' | '404' | 'feature'; navigate: Navigate; fallbackPath: string }) {
  const copy = kind === '403'
    ? ['无权访问', '当前账号没有该页面或资源的访问权限。']
    : kind === 'feature'
      ? ['功能未启用', '当前租户尚未启用此产品能力，请联系租户管理员。']
      : ['页面不存在', '请求的地址不存在或已调整。'];
  return <main className="error-page"><LockClosed20Regular /><Title2>{copy[0]}</Title2><Text>{copy[1]}</Text><Button appearance="primary" onClick={() => navigate(fallbackPath)}>返回默认入口</Button></main>;
}
