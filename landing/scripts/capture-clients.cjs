const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright');
const fs=require('node:fs'),path=require('node:path');
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});
 const output=path.resolve(__dirname,'../public/screenshots');fs.mkdirSync(output,{recursive:true});
 try {
  for(const theme of ['light','dark']){
   const page=await browser.newPage({viewport:{width:1440,height:1000},reducedMotion:'reduce'});
   const errors=[];page.on('pageerror',e=>errors.push(e.message));
   await page.goto(`http://127.0.0.1:5174/?client-preview=dashboard&theme=${theme}`,{waitUntil:'networkidle'});
   await page.getByText('已获得学分 / 方案总学分').waitFor();
   await page.evaluate(()=>document.fonts.ready);
   if(errors.length)throw new Error(errors.join('\n'));
   await page.screenshot({path:path.join(output,`desktop-${theme}.png`)});
   await page.close();
  }
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
