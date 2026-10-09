import { test } from 'node:test';
import assert from 'node:assert/strict';
import { evaluationBody, evaluationSignature, findUniqueEvaluationTask } from '../src/services/academic/evaluation.js';
const form = { encoding: 'utf-8', fields: [['token', 'nonce+=='], ['course', '42']],
  groups: [{ name: 'rating', question: '教学态度', options: [{ value: '4', label: '优秀' }, { value: '3', label: '良好' }] }],
  comments: [{ name: 'comment', question: '改进建议', maxLength: 100 }] };
test('提交用户选择的选项和评语，保留隐藏令牌而非固定好评', () => {
  const result = new URLSearchParams(evaluationBody(form, { ratings: ['1'], comments: ['我的建议 & 意见'] }));
  assert.equal(result.get('rating'), '3'); assert.equal(result.get('comment'), '我的建议 & 意见');
  assert.equal(result.get('token'), 'nonce+==');
});
test('空选项、空评语及超长评语在提交前拒绝', () => {
  for (const ratings of [[''], [null], ['4']]) assert.throws(() => evaluationBody(form, { ratings, comments: ['建议'] }), /选择选项/);
  assert.throws(() => evaluationBody(form, { ratings: ['0'], comments: [''] }), /文字评价/);
  assert.throws(() => evaluationBody(form, { ratings: ['0'], comments: ['长'.repeat(101)] }), /长度限制/);
});
test('GBK 问卷按原表单编码提交中文，无法表示的字符拒绝提交', () => {
  const encoded = evaluationBody({ ...form, encoding: 'gbk' }, { ratings: ['1'], comments: ['教学建议'] });
  const raw = new URLSearchParams(encoded).get('rating'); assert.equal(raw, '3');
  const component = encoded.match(/comment=([^&]*)/)[1];
  const bytes = Uint8Array.from(component.match(/%[0-9A-F]{2}/g).map(byte => parseInt(byte.slice(1), 16)));
  assert.equal(new TextDecoder('gbk').decode(bytes), '教学建议');
  assert.throws(() => evaluationBody({ ...form, encoding: 'gbk' }, { ratings: ['0'], comments: ['建议😀'] }), /无法编码/);
});
test('问卷题目和选项标签改变时结构签名改变，令牌刷新不改变签名', () => {
  assert.equal(evaluationSignature(form), evaluationSignature({ ...form, fields: [['token', 'new']] }));
  assert.notEqual(evaluationSignature(form), evaluationSignature({ ...form, groups: [{ ...form.groups[0], question: '另一评价项目' }] }));
});
test('重复教师和课程不能依赖行顺序核对，避免把另一条已完成记录当作提交成功', () => {
  const task = { teacher: '测试教师', course: '测试课程', pending: true };
  assert.throws(() => findUniqueEvaluationTask([task, { ...task, completed: true }], task), /多条评价记录/);
  assert.equal(findUniqueEvaluationTask([task], task), task);
  assert.equal(findUniqueEvaluationTask([], task), undefined);
});
