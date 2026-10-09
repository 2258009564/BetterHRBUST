import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { ref, reactive, computed } from 'vue';
const A='2401234567', B='2407654321';
const ids={ [A]:'101', [B]:'202' };
const store=new Map([['saved_student_number',B],['better_hrbust_has_session','true'],
 ['better_hrbust_cached_profile',JSON.stringify({studentNumber:A,internalId:ids[A],realName:'测试甲'})],
 ['better_hrbust_cache_scores',JSON.stringify([{courseName:'甲课程'}])]]);
let server=A, wrongIdentity=false, hold=null;
const cleaners=[];
const profile = owner => ({studentNumber:owner,realName:owner===A?'测试甲':'测试乙'});
const api={
 login:async username=>{server=wrongIdentity?A:username;return {success:true};},
 logout:async()=>{server='';},
 getStudentContext:async()=>({studentId:ids[server],year:'2026',term:'1',courses:[]}),
 getPersonalInfo:async()=>profile(server),
 getScores:()=>{const owner=server;if(hold&&owner===A)return new Promise(resolve=>{hold.resolve=resolve;});return Promise.resolve({scores:[{courseName:owner+'课程'}]});},
 getCurriculumPlan:async()=>({groups:[]}),getExams:async()=>[],
 getCalendarInfo:async()=>({notices:[]}),getTimetable:async()=>({cells:[],unarranged:[]})
};
const env={ref,reactive,computed,academicApi:api,Date,console,
 storageGetItem:key=>store.get(key)||null,storageSetItem:(key,value)=>store.set(key,String(value)),storageRemoveItem:key=>store.delete(key),
 registerDataCacheCleaner:fn=>cleaners.push(fn),clearRegisteredDataCaches:()=>cleaners.forEach(fn=>fn()),registerCourseColors:()=>{}};
function load(file, additions={}) {
 const source=readFileSync(new URL('../src/composables/'+file,import.meta.url),'utf8').replace(/^import[\s\S]*?;\r?\n/gm,'').replace(/^export /gm,'');
 const context=vm.createContext({...env,...additions});vm.runInContext(source,context);return context;
}
const session=load('useSession.js').useSession();
const data=load('useAcademicData.js',{useSession:()=>session}).useAcademicData();
assert.equal(session.studentNumber.value,A,'记住的输入学号不能覆盖已验证档案身份');
assert.equal(data.scores.value[0].courseName,'甲课程');
await session.prepareLoginAccount(B);
assert.equal(data.scores.value.length,0);assert.equal(session.userProfile.realName,'');
assert.equal((await session.login({username:B,password:'test',captcha:'0000'})).success,true);
await data.syncAll();
assert.equal(session.userProfile.realName,'测试乙');assert.equal(data.scores.value[0].courseName,B+'课程');
await session.prepareLoginAccount(A);
assert.equal((await session.login({username:A,password:'test',captcha:'0000'})).success,true);
await data.syncAll();assert.equal(data.scores.value[0].courseName,A+'课程');
hold={};const stale=data.syncAll();
while(!hold.resolve)await new Promise(resolve=>setTimeout(resolve,0));
await session.prepareLoginAccount(B);
assert.equal((await session.login({username:B,password:'test',captcha:'0000'})).success,true);
await data.syncAll();
hold.resolve({scores:[{courseName:'迟到的甲课程'}]});await stale;
assert.equal(data.scores.value[0].courseName,B+'课程','旧请求不能覆盖新账号');
server=A;
assert.equal(await session.checkAuth({light:true}),false,'外部会话切换不能拼接旧档案和新内部ID');
assert.equal(data.scores.value.length,0);
await session.logout();wrongIdentity=true;
const mismatch=await session.login({username:B,password:'test',captcha:'0000'});
assert.equal(mismatch.success,false);assert.match(mismatch.message,/账号.*不一致/);
assert.equal(session.isLoggedIn.value,false);assert.equal(session.userProfile.realName,'');
console.log('通过：A→B→A、记住学号与身份区分、缓存清理、旧同步丢弃及错误服务端身份拒绝');
