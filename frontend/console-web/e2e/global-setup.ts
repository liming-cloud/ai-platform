import { provisionDeployAcceptance } from './fixtures/deploy-provision';

declare const process: { readonly env: Readonly<Record<string, string | undefined>> };

export default async function globalSetup() {
  if (process.env.E2E_SKIP_DEPLOY_PROVISION === 'true') return;
  await provisionDeployAcceptance();
}
