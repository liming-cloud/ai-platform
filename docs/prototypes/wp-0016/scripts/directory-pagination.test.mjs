import { test } from 'node:test';
import assert from 'node:assert/strict';
import { directoryPage } from '../directory-pagination.js';

test('目录分页：缺省每页 20、边界归一、空目录和有效页码', () => {
  assert.deepEqual(directoryPage(0), { page: 1, size: 20, pages: 1, start: 0, end: 0, numbers: [1] });
  assert.deepEqual(directoryPage(43, 9, 10), { page: 5, size: 10, pages: 5, start: 40, end: 43, numbers: [1, 2, 3, 4, 5] });
  assert.equal(directoryPage(150, -2, 13).size, 20);
  assert.equal(directoryPage(150, 'bad', 100).page, 1);
  assert.deepEqual(directoryPage(1000, 25, 20).numbers, [1, '…', 24, 25, 26, '…', 50]);
});
