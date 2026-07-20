import { afterEach, describe, expect, it, vi } from 'vitest';
import { createStudioApplication } from '../app-studio-api';

const input = {
  applicationType: 'KNOWLEDGE_QA',
  name: '法务助手',
  description: '受控法务问答',
  modelConfigId: 'MODEL-1',
  knowledgeBaseIds: ['KB-1'],
  systemPrompt: '仅根据授权知识回答',
};

function envelope(body: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    header: { code: 'SUCCESS', message: 'success', traceId: 'trace-create' },
    body,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
}

afterEach(() => vi.restoreAllMocks());

describe('app studio mutation response contracts', () => {
  it('rejects a create response that omits server-confirmed access metadata', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => envelope({
      applicationId: 'APP-1',
      applicationCode: 'APP-APP-1',
      name: '法务助手',
      modelConfigId: 'MODEL-1',
      knowledgeBaseIds: ['KB-1'],
      publicationStatus: 'DRAFT',
      createdAt: '2026-07-22T12:00:00Z',
    }));

    await expect(createStudioApplication(input)).rejects.toThrow('访问能力');
  });
});
