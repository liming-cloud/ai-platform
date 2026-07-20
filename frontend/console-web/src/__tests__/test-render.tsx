import type { ReactElement } from 'react';
import { PortalMountNodeProvider } from '@fluentui/react-components';
import { render } from '@testing-library/react';

const TEST_RENDER_HOST = 'data-test-render-host';

export function renderWithIsolatedPortal(ui: ReactElement) {
  const host = document.createElement('div');
  const root = document.createElement('div');
  const portal = document.createElement('div');
  host.setAttribute(TEST_RENDER_HOST, 'true');
  host.append(root, portal);
  document.body.append(host);
  const wrap = (element: ReactElement) => (
    <PortalMountNodeProvider value={portal}>{element}</PortalMountNodeProvider>
  );
  const result = render(wrap(ui), { container: root });
  const rerender = result.rerender;
  return {
    ...result,
    rerender: (element: ReactElement) => rerender(wrap(element)),
  };
}

export function removeIsolatedPortalHosts() {
  document.querySelectorAll(`[${TEST_RENDER_HOST}="true"]`).forEach((host) => host.remove());
}
