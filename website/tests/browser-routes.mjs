/** Actual local native HTTP static routing + remote Chromium. No database or external providers. */
import assert from 'node:assert/strict';
import {readdir,mkdir,writeFile} from 'node:fs/promises';
import {resolve,relative,dirname,join} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHttpServer} from '../../backend/src/http/server.js';
import {AppError} from '../../backend/src/shared/errors.js';
const {chromium}=await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);
async function htmlFiles(root) {
 const files=[];for(const item of await readdir(root,{withFileTypes:true})) {
  if(item.isDirectory())files.push(...await htmlFiles(join(root,item.name)));
  else if(item.name==='index.html')files.push(join(root,item.name));
 }return files;
}
const root=resolve('apps/website'),routes=(await htmlFiles(root)).map(f=>'/'+relative(root,dirname(f)).replaceAll('\\','/')).sort();
const server=createHttpServer({app:{identity:{authenticate:async()=>{throw new AppError('AUTH_REQUIRED',401);}},handlers:{health:()=>({status:'ok'})}},config:{production:true,serveWeb:true,publicOrigin:'https://mzn-api.onrender.com',adminOrigin:'https://mzn-api.onrender.com'},logger:()=>{}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
const origin='http://127.0.0.1:'+server.address().port;
const browser=await chromium.launch({headless:true});
const output=resolve(process.env.WEBSITE_EVIDENCE_DIRECTORY??'/tmp/ridenow-website-browser-evidence');await mkdir(output,{recursive:true});
const cases=[],errors=[],failures=[];
try {
 const page=await browser.newPage({reducedMotion:'reduce'});page.on('pageerror',e=>errors.push(e.message));
 await page.route('**/*',route=>new URL(route.request().url()).origin===origin?route.continue():route.abort('blockedbyclient'));
 page.on('response',response=>{if(response.status()>=400&&!response.url().includes('/missing-qa-page'))failures.push({url:new URL(response.url()).pathname,status:response.status()});});
 for(const width of [360,768,1440]) {
  await page.setViewportSize({width,height:900});
  for(const path of routes) {
   const response=await page.goto(origin+path,{waitUntil:'networkidle'});assert.equal(response.status(),200,path);
   const metrics=await page.evaluate(async()=>{await document.fonts.ready;return {width:innerWidth,scroll:document.documentElement.scrollWidth,title:document.title,heading:document.querySelector('h1')?.textContent.trim(),failedImages:[...document.images].filter(i=>i.loading!=='lazy'&&i.complete&&!i.naturalWidth).map(i=>i.getAttribute('src'))};});
   assert.ok(metrics.heading,path+' heading');assert.ok(metrics.title.includes('RideNow'),path+' title');assert.deepEqual(metrics.failedImages,[],path+' image resources');
   if(metrics.scroll>width+1) {
    await page.screenshot({path:join(output,'overflow.jpg'),type:'jpeg',quality:65,fullPage:true});
    const overflow=await page.evaluate(()=>[...document.querySelectorAll('body *')].filter(e=>{const r=e.getBoundingClientRect();return r.width>0&&r.right>innerWidth+1;}).slice(0,15).map(e=>({tag:e.tagName,classes:e.className,right:e.getBoundingClientRect().right,width:e.getBoundingClientRect().width})));
    await writeFile(join(output,'overflow.json'),JSON.stringify({path,width,metrics,overflow},null,2));
   }
   assert.ok(metrics.scroll<=width+1,path+' '+width+' horizontal overflow '+metrics.scroll);
   if(['/', '/driver'].includes(path)&&width!==768)await page.screenshot({path:join(output,(path==='/'?'rider':'driver')+'-'+width+'.jpg'),type:'jpeg',quality:55,fullPage:true});
   cases.push({path,...metrics});
  }
 }
 await page.goto(origin+'/help/');await page.locator('[data-help-search]').fill('synthetic-no-matching-question-58291');assert.equal(await page.locator('[data-help-item]:visible').count(),0);
 await page.locator('[data-help-search]').fill('');assert.ok(await page.locator('[data-help-item]:visible').count()>0);
 for(const [alias,target] of [['/drive','/driver/'],['/faq','/help/']]){await page.goto(origin+alias);assert.equal(new URL(page.url()).pathname,target);}
 await page.emulateMedia({reducedMotion:'no-preference'});
 for(const path of ['/','/driver/']) {
  await page.goto(origin+path);const track=page.locator('.kinetic-tape__track');
  assert.equal(await track.count(),1,path+' kinetic strip');
  assert.equal(await track.evaluate(e=>getComputedStyle(e).animationName),'tape-scroll');
 }
 await page.emulateMedia({reducedMotion:'reduce'});
 await page.goto(origin+'/driver/');const reduced=await page.locator('.kinetic-tape__track').evaluate(e=>({duration:parseFloat(getComputedStyle(e).animationDuration),iterations:getComputedStyle(e).animationIterationCount}));assert.ok(reduced.duration<=0.001);assert.equal(reduced.iterations,'1');
 const missing=await page.goto(origin+'/missing-qa-page');assert.equal(missing.status(),404);
 assert.deepEqual(errors,[]);assert.deepEqual(failures,[]);
 const report={actualNativeHttpServer:true,actualChromium:await browser.version(),externalRequestsBlocked:true,liveDatabaseOrProviders:false,routes,cases,helpSearch:true,kineticAnimationAndReducedMotion:true,aliases:true,unknown404:true,pageErrors:0,failedResources:0,performanceClaim:'No public Core Web Vitals or load-capacity claim'};
 await writeFile(join(output,'report.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({...report,cases:cases.length}));
} finally {await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r));}
