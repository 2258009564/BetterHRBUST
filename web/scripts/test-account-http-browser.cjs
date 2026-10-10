const assert = require('node:assert/strict');
const {createServer} = require('node:http');
const {readFileSync} = require('node:fs');
const path = require('node:path');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright');
const A='2401234567',B='2407654321';
const host=process.env.ACCOUNT_TEST_HOST || 'jwzx.hrbust.edu.cn';
const sessions = new Map([['initial-a',{owner:A,captcha:true}]]);
let sequence=0, posts=0, profileReads=0;
const loginHtml='<form action="j_acegi_security_check"><input name="j_captcha"></form>';
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jZAAAAABJRU5ErkJggg==','base64');
const server=createServer(async(req,res)=>{
 const url=new URL(req.url,'http://local');
 if(url.pathname==='/favicon.ico'){res.writeHead(204);res.end();return;}
 let key=(req.headers.cookie||'').match(/JSESSIONID=([^;]+)/)?.[1];
 if(!sessions.has(key)){key='session-'+(++sequence);sessions.set(key,{owner:'',captcha:false});res.setHeader('Set-Cookie',`JSESSIONID=${key}; Path=/academic; HttpOnly; SameSite=Lax`);}
 const session=sessions.get(key);
 if(process.env.DEBUG_ACCOUNT_HTTP) console.log(req.method,url.pathname,session.owner===A?'A':session.owner===B?'B':'anonymous');
 const send=(code,text,type='text/html; charset=utf-8',cache='no-store')=>{res.writeHead(code,{'Content-Type':type,'Cache-Control':cache});res.end(text);};
 const redirect=path=>{res.writeHead(302,{Location:'https://jwzx.hrbust.edu.cn/academic/'+path,'Cache-Control':'no-store'});res.end();};
 if(url.pathname==='/academic/j_acegi_logout')return send(404,'wrong logout endpoint');
 if(url.pathname==='/academic/logout_security_check'){
  sessions.delete(key);res.setHeader('Set-Cookie','JSESSIONID=; Max-Age=0; Path=/academic; HttpOnly');return redirect('index.jsp');
 }
 if(url.pathname==='/academic/common/security/login.jsp'||url.pathname==='/academic/index.jsp')return send(200,loginHtml);
 if(url.pathname==='/academic/getCaptcha.do'){session.captcha=true;return send(200,png,'image/png');}
 if(url.pathname==='/academic/j_acegi_security_check'){
  posts++;let text='';for await(const chunk of req)text+=chunk;const body=new URLSearchParams(text);
  if(!session.owner&&session.captcha&&body.get('j_password')==='test'&&[A,B].includes(body.get('j_username')))session.owner=body.get('j_username');
  return redirect('student/currcourse/currcourse.jsdo');
 }
 if(url.pathname==='/academic/student/currcourse/currcourse.jsdo'){
  if(!session.owner)return redirect('common/security/login.jsp');
  return send(200,`<script>var studentid="${session.owner===A?'101':'202'}";var year="2026";</script><select name="term"><option selected value="1">1</option></select>`);
 }
 if(url.pathname==='/academic/showPersonalInfo.do'){
  if(!session.owner)return redirect('common/security/login.jsp');profileReads++;
  return send(200,`<table class="form"><tr><th>用户名</th><td>${session.owner}</td></tr><tr><th>真实姓名</th><td>Student ${session.owner===A?'A':'B'}</td></tr></table>`,'text/html; charset=utf-8','private, max-age=3600');
 }
 return send(404,'unused endpoint');
});
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const base=`http://${host}:${server.address().port}/academic/`;
 const browser=await chromium.launch({channel:'msedge',headless:true,args:['--no-proxy-server','--host-resolver-rules=MAP jwzx.hrbust.edu.cn 127.0.0.1']});
 try {
  // 先执行修复前的真实请求层，证明旧退出地址导致 A 的会话仍存在。
  const before=await browser.newPage();await before.context().addCookies([{name:'JSESSIONID',value:'initial-a',domain:host,path:'/academic'}]);
  await before.goto(base+'student/currcourse/currcourse.jsdo');
  const old=await before.evaluate(async()=>{
    const exit=await fetch('/academic/j_acegi_logout',{credentials:'include'});
    await fetch('/academic/j_acegi_security_check',{method:'POST',credentials:'include',redirect:'manual',
      body:new URLSearchParams({j_username:'2407654321',j_password:'test',j_captcha:'0000'})});
    return {status:exit.status,html:await (await fetch('/academic/showPersonalInfo.do',{credentials:'include'})).text()};
  });
  assert.equal(old.status,404);assert.match(old.html,new RegExp(A),'原退出接口404时，提交B仍返回A');await before.close();
  posts=0;profileReads=0;
  const page=await browser.newPage({viewport:{width:1440,height:1050}});const https=[];page.on('requestfailed',r=>{if(r.url().startsWith('https://jwzx.hrbust.edu.cn')&&r.failure()?.errorText!=='net::ERR_ABORTED')https.push(r.url());});page.on('response',r=>{if(r.url().startsWith('https://jwzx.hrbust.edu.cn'))https.push(r.url());});
  await page.context().addCookies([{name:'JSESSIONID',value:'initial-a',domain:host,path:'/academic'}]);
  await page.goto(base+'student/currcourse/currcourse.jsdo');
  await page.evaluate(async()=>{
   await fetch('/academic/showPersonalInfo.do');
   localStorage.setItem('better_hrbust_has_session','true');localStorage.setItem('saved_student_number','2401234567');
   localStorage.setItem('better_hrbust_cached_profile',JSON.stringify({studentNumber:'2401234567',internalId:'101',realName:'Student A'}));
   const d=new Date();const date=`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
   localStorage.setItem('better_hrbust_cache_sync_meta',JSON.stringify({lastSyncDate:date}));
  });
  await page.addScriptTag({content:readFileSync(path.resolve(__dirname,'../dist-userscript/better-hrbust.user.js'),'utf8')});
  async function switchTo(target,label){
   await page.locator('aside [title*="切换账号"]').click();
   await page.getByPlaceholder(/请输入您的学号/).fill(target);
   await page.getByPlaceholder('请输入哈理工教务在线密码').fill('test');
   await page.waitForFunction(()=>{const img=document.querySelector('main img[alt="验证码"]');return img&&img.complete&&img.naturalWidth>0;});
   await page.getByPlaceholder('请输入 4 位验证码').fill('0000');
   await page.getByRole('button',{name:/^(登录教务在线|重新认证 \/ 切换账号登录)$/}).click();
   await page.locator('aside').getByText(label,{exact:true}).waitFor({timeout:15000});
   assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('better_hrbust_cached_profile')).studentNumber),target);
  }
  await switchTo(B,'Student B');await switchTo(A,'Student A');
  assert.equal(posts,2,'每次切换只提交一次登录凭证');assert.deepEqual(https,[],'退出及未登录跳转不得访问HTTPS');assert.ok(profileReads>=3,'新身份读取不能复用缓存的旧档案');
  await page.locator('aside').getByRole('button', {name:'概览',exact:true}).click();
  for (const width of [1536,1280,960,768,390]) {
   await page.setViewportSize({width,height:1000});
   await page.waitForTimeout(150);
   assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1), `概览不应在有效宽度${width}px产生页面横向溢出`);
  }
  console.log('通过：真实HTTP/Cookie复现旧退出404，修复后A→B→A、旧档案缓存、302跳转、验证码新会话和单次登录POST');
 }finally{await browser.close();server.close();}
})().catch(e=>{console.error(e);server.close();process.exitCode=1;});
