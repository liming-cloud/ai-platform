const { test } = require("node:test");
const assert = require("node:assert/strict");
const { verifyAssistants, verifyPreview } = require("../development/acceptance-member-authorization.cjs");

test("助手验收拒绝秘密额外字段重复标识及不精确版本", () => {
  const valid = { membershipId: "1", loginName: "synthetic-member", status: "ENABLED", readOnly: false, assistantIds: ["2"], authorizationRevision: 3 };
  assert.doesNotThrow(() => verifyAssistants(valid));
  for (const change of [{ subjectId: "4" }, { tokenValue: "synthetic-only" }, { authorizationRevision: -1 }, { assistantIds: ["2", "2"] }, { membershipId: 1 }]) assert.throws(() => verifyAssistants({ ...valid, ...change }));
});
test("真实预览需要明确计数与四项检查，固定通过文案不能代替事实", () => {
  const valid = { passed: true, allowCount: 8, denyCount: 3992, leakCount: 0,
    checks: ["assistantSelection", "conversation", "ownHistory", "authorizedCitations"].map(feature => ({ feature, allowCount: 2, denyCount: 998 })) };
  assert.doesNotThrow(() => verifyPreview(valid));
  for (const change of [{ leakCount: undefined }, { leakCount: 1 }, { allowCount: 32 }, { checks: [] }, { checks: [valid.checks[0], valid.checks[0], valid.checks[0], valid.checks[0]] }]) assert.throws(() => verifyPreview({ ...valid, ...change }));
});
