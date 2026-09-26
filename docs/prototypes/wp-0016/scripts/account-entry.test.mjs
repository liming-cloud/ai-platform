import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createPrototypeStore, createMemoryStorage } from '../prototype-state.js';
const actor = 'account-platform-admin';
const password = 'Initial-only-2026!';
const administrator = { loginName: 'north.admin', password, passwordConfirmation: password };
const input = { organizationName: '北辰组织', tenantCode: 'north-demo', administrator, confirmed: true };

test('新建租户与新管理员原子提交，管理员可用租户入口登录，凭据不落盘', () => {
  const storage = createMemoryStorage();
  const store = createPrototypeStore(storage);
  const result = store.createTenant(input, actor);
  assert.equal(result.ok, true);
  assert.equal(result.tenant.members[0].accountId, result.tenant.administratorAccountId);
  assert.equal(store.getSnapshot().accounts.at(-1).platformAdministrator, false);
  assert.equal(store.loginConsole({ username: administrator.loginName, password, tenantCode: input.tenantCode, entry: 'tenant' }).ok, true);
  assert.doesNotMatch(storage.dump(), /Initial-only|password|secret|credential/i);
});

test('开通拒绝重名、口令不一致、角色越界；存储失败不能留下孤儿账号', () => {
  for (const [patch, operator] of [
    [{ administrator: { ...administrator, loginName: 'qiao.demo' } }, actor],
    [{ administrator: { ...administrator, passwordConfirmation: 'different' } }, actor],
    [{}, 'account-qiao'],
  ]) {
    const store = createPrototypeStore(createMemoryStorage());
    const before = store.getSnapshot();
    assert.equal(store.createTenant({ ...input, ...patch }, operator).ok, false);
    assert.deepEqual(store.getSnapshot(), before);
  }
  const storage = createMemoryStorage();
  const store = createPrototypeStore({ ...storage, setItem() { throw Error('合成写入失败'); } });
  const before = store.getSnapshot();
  assert.equal(store.createTenant(input, actor).reason, 'STORAGE_UNAVAILABLE');
  assert.deepEqual(store.getSnapshot(), before);
});

test('平台创建只产生平台账户，错误入口不建立另一种角色会话', () => {
  const store = createPrototypeStore(createMemoryStorage());
  const result = store.createAccount({ ...administrator, loginName: 'platform.north' }, actor);
  assert.equal(result.account.platformAdministrator, true);
  assert.equal(store.loginConsole({ username: 'platform.north', password, entry: 'tenant' }).ok, false);
  assert.equal(store.loginConsole({ username: 'qiao.demo', password: 'demo-only', entry: 'platform' }).ok, false);
  assert.equal(store.getSession().authenticated, false);
});
