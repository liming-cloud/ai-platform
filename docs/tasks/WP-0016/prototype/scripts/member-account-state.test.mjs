import test from 'node:test';
import assert from 'node:assert/strict';
import { createMemoryStorage, createPrototypeStore, PROTOTYPE_STATE_KEY, DEFAULT_TENANT_ID as tenant } from '../prototype-state.js';

const actor = 'account-qiao';
const credentials = { password: 'Initial-only-2026!', passwordConfirmation: 'Initial-only-2026!' };
const input = { loginName: 'member.new', ...credentials, conversationFeatures: ['assistantSelection', 'conversation'], assistantIds: ['assistant-employee-service'] };
const reset = { password: 'Reset-only-2026!', passwordConfirmation: 'Reset-only-2026!', revokeSessions: true };

test('租户管理员直接开通普通账号，刷新与本人登录均使用同一身份', () => {
  const storage = createMemoryStorage();
  const store = createPrototypeStore(storage);
  const added = store.addMember(tenant, input, actor);
  assert.equal(added.ok, true);
  assert.equal(added.member.role, 'ordinaryMember');
  assert.equal(added.account.platformAdministrator, false);
  assert.equal(createPrototypeStore(storage).loginQuery({ tenantCode: 'spruce-demo', username: input.loginName, password: credentials.password }).ok, true);
  assert.doesNotMatch(storage.dump(), /Initial-only|password|secret|credential/i);
});

test('身份、功能或助手不合法时不留下半个开户结果', () => {
  for (const invalid of [{ loginName: 'qiao.demo' }, { passwordConfirmation: 'different' }, { conversationFeatures: ['modelGovernance'] }, { assistantIds: ['unknown'] }]) {
    const storage = createMemoryStorage();
    const store = createPrototypeStore(storage);
    const before = store.getSnapshot();
    assert.equal(store.addMember(tenant, { ...input, ...invalid }, actor).ok, false);
    assert.deepEqual(store.getSnapshot(), before);
    assert.equal(storage.dump(), '');
  }
});

test('平台、普通成员、空身份不能借租户接口创建或治理账号', () => {
  for (const invalidActor of ['account-platform-admin', 'account-shen', undefined]) {
    const store = createPrototypeStore();
    const before = store.getSnapshot();
    assert.equal(store.addMember(tenant, input, invalidActor).reason, 'FORBIDDEN');
    assert.equal(store.resetMemberPassword(tenant, 'account-shen', reset, invalidActor).reason, 'FORBIDDEN');
    assert.equal(store.setMemberStatus(tenant, 'account-shen', 'disabled', invalidActor).reason, 'FORBIDDEN');
    assert.deepEqual(store.getSnapshot(), before);
  }
});

test('租户管理员不能重置管理员、游离账号、跨租户共享身份', () => {
  const storage = createMemoryStorage();
  const state = createPrototypeStore().getSnapshot();
  state.tenants.push({ ...structuredClone(state.tenants[0]), id: 'other', code: 'other' });
  storage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(state));
  const store = createPrototypeStore(storage);
  for (const id of ['account-qiao', 'account-platform-admin', 'account-lin', 'account-shen']) {
    assert.equal(store.resetMemberPassword(tenant, id, reset, actor).ok, false);
  }
  assert.equal(store.setMemberStatus(tenant, 'account-qiao', 'disabled', actor).ok, false);
});

test('重置撤销旧会话；停用仅改变本租户成员资格且可恢复', () => {
  const storage = createMemoryStorage();
  const member = createPrototypeStore(storage);
  member.loginQuery({ tenantCode: 'spruce-demo', username: 'shen.demo', password: 'demo-only' });
  const store = createPrototypeStore(storage);
  assert.equal(store.resetMemberPassword(tenant, 'account-shen', reset, actor).ok, true);
  assert.equal(member.getSession().authenticated, false);
  assert.equal(store.loginQuery({ tenantCode: 'spruce-demo', username: 'shen.demo', password: reset.password }).ok, true);
  assert.equal(store.setMemberStatus(tenant, 'account-shen', 'disabled', actor).ok, true);
  assert.equal(store.getSession().authenticated, false);
  assert.equal(store.getSnapshot().accounts.find(a => a.id === 'account-shen').status, 'available');
  assert.equal(store.setMemberStatus(tenant, 'account-shen', 'active', actor).ok, true);
  assert.equal(store.loginQuery({ tenantCode: 'spruce-demo', username: 'shen.demo', password: reset.password }).ok, true);
});

test('存储异常时开户、重置和停用全部回滚', () => {
  const memory = createMemoryStorage();
  const store = createPrototypeStore({ ...memory, setItem() { throw Error('模拟空间不足'); } });
  const before = store.getSnapshot();
  for (const action of [() => store.addMember(tenant, input, actor), () => store.resetMemberPassword(tenant, 'account-shen', reset, actor), () => store.setMemberStatus(tenant, 'account-shen', 'disabled', actor)]) {
    assert.equal(action().reason, 'STORAGE_UNAVAILABLE');
    assert.deepEqual(store.getSnapshot(), before);
  }
});

test('管理员账号、成员资格或租户停用，以及跨租户管理均失败关闭', () => {
  const changes = [
    state => { state.accounts.find(a => a.id === actor).status = 'suspended'; },
    state => { state.tenants[0].members.find(m => m.accountId === actor).status = 'disabled'; },
    state => { state.tenants[0].status = 'suspended'; },
    state => { state.tenants[0].members = state.tenants[0].members.filter(m => m.accountId !== actor); state.tenants.push({ ...structuredClone(state.tenants[0]), id: 'other', members: [{ accountId: actor, role: 'tenantAdministrator', status: 'active' }] }); },
  ];
  for (const change of changes) {
    const storage = createMemoryStorage();
    const state = createPrototypeStore().getSnapshot();
    change(state);
    storage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(state));
    const store = createPrototypeStore(storage);
    const before = storage.dump();
    assert.equal(store.addMember(tenant, input, actor).reason, 'FORBIDDEN');
    assert.equal(store.resetMemberPassword(tenant, 'account-shen', reset, actor).reason, 'FORBIDDEN');
    assert.equal(store.setMemberStatus(tenant, 'account-shen', 'disabled', actor).reason, 'FORBIDDEN');
    assert.equal(storage.dump(), before);
  }
});
