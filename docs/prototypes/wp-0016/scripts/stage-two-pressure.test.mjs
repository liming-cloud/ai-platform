import test from 'node:test';
import assert from 'node:assert/strict';
import {createStressFixtures} from '../stress-fixtures.js';
import {directoryPage} from '../directory-pagination.js';

test('独立压力夹具使用既有分页计算并保持序号边界',()=>{
  const fixtures=createStressFixtures();
  for(const [name,count] of Object.entries({documents:1000,members:1000,assistants:200,versions:100,records:10000})){
    assert.equal(fixtures[name].length,count);
    for(const size of [10,20,50,100]){
      const page=directoryPage(count,2,size);
      const visible=fixtures[name].slice(page.start,page.end);
      assert.ok(visible.length<=size);
      assert.equal(visible[0].sequence,page.start+1);
    }
  }
  const earlier=fixtures.records.filter(item=>item.sequence<101).slice(-20);
  assert.equal(earlier[0].sequence,81);
  assert.equal(earlier.at(-1).sequence,100);
});
