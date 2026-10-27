/**
 * pose fix 行为 E2E（真实浏览器 + PIL 像素差分）
 * 前置：后端 :8088 + 前端 :5173；启用模型 Mao(id=8)。
 * 验证：idle_auto_play=0 无待机静止；idle_auto_play=0 自定义待机可动；idle_auto_play=1 库待机可动；还原。
 */
import puppeteer from "puppeteer-core";
import { execFileSync } from "node:child_process";
import { writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const FRONT = "http://localhost:5173/ai-mental-care", BASE = "http://localhost:8088";
const CHROME = "C:/Program Files/Google/Chrome/Application/chrome.exe", MODEL_ID = "8";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms)); let admin;
function capCode(key) { const py = `import redis,sys\nv=redis.Redis(host='127.0.0.1',port=6379,password='132466',db=0,decode_responses=False).get('verification-code:'+sys.argv[1])\nprint('__NONE__' if v is None else ''.join(chr(b) for b in v[1:-1])+chr(v[-1]&127))`; return execFileSync("python", ["-c", py, key], { encoding: "utf8" }).trim(); }
async function login(a, p, path) { for (let i=0;i<3;i++) { const cr=await fetch(`${BASE}/common/verification/code`,{method:"POST"}); const cb=await cr.json(); const res=await fetch(`${BASE}${path}`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({loginAccount:a,password:p,key:cb.data.key,code:capCode(cb.data.key)})}); const b=await res.json(); if(b.code===200&&b.data?.token)return{token:b.data.token,headerName:b.data.headerName||"admin-token"}; } throw Error("login failed"); }
async function setIdle(x) { admin ??= await login("admin","123456","/admin/account/login"); const r=await fetch(`${BASE}/admin/live2d-model/${MODEL_ID}`,{method:"PUT",headers:{[admin.headerName]:admin.token,"Content-Type":"application/json"},body:JSON.stringify({id:MODEL_ID,...x})}); const b=await r.json(); if(b.code!==200)throw Error(JSON.stringify(b)); }
async function waitCanvas(page) { for(let i=0;i<25;i++){if(await page.evaluate(()=>!!document.querySelector(".live2d-avatar canvas")))return true;await sleep(1200)}return false; }
async function diff(page,label) { const clip=await page.evaluate(()=>{const r=document.querySelector(".live2d-avatar canvas").getBoundingClientRect();return{x:r.x,y:r.y,width:r.width,height:r.height}}); const p=path.join(__dirname,label); writeFileSync(p+"1.png",await page.screenshot({clip})); await sleep(8000); writeFileSync(p+"2.png",await page.screenshot({clip})); await sleep(8000); writeFileSync(p+"3.png",await page.screenshot({clip})); const py=`from PIL import Image\nimport sys,itertools\ndef load(a):return list(Image.open(a).convert('RGB').getdata())\nA,B,C=load(sys.argv[1]),load(sys.argv[2]),load(sys.argv[3])\ndef dd(X,Y):return sum(max(abs(X[i][j]-Y[i][j]) for j in range(3))>4 for i in range(len(X)))/len(X)\nprint(max(dd(A,B),dd(B,C),dd(A,C)))`; return Number(execFileSync("python",["-c",py,p+"1.png",p+"2.png",p+"3.png"],{encoding:"utf8"}).trim()); }
async function main(){
  await setIdle({idleAutoPlay:0,idleExpressionList:[],idleMotionList:[],idleIntervalSeconds:0});
  const browser=await puppeteer.launch({executablePath:CHROME,headless:true,args:["--no-sandbox","--disable-gpu"]}),page=await browser.newPage(); await page.setViewport({width:1440,height:900}); const errors=[];page.on("pageerror",e=>errors.push(String(e)));page.on("console",m=>m.type()==="error"&&errors.push(m.text()));
  const user=await login("portaltest2","Portal123!","/user/account/login"); await page.goto(FRONT,{waitUntil:"networkidle2"}); await page.evaluate(u=>{localStorage.setItem("user_token_info",JSON.stringify(u));localStorage.setItem("token",u.token);localStorage.setItem("portal-user",JSON.stringify({token:u.token}))},{token:user.token,headerName:user.headerName}); await page.reload({waitUntil:"networkidle2"}); await page.goto(`${FRONT}/conversation/audio?inputTypes=AUDIO&outputTypes=AVATAR_2D,TEXT,AUDIO`,{waitUntil:"networkidle2",timeout:40000});
  if(!await waitCanvas(page))throw Error("canvas A missing"); const a=await diff(page,"pose-static-"); console.log(`[A] auto=0 无待机 diff=${(a*100).toFixed(3)}%`); if(a>.002)throw Error("A not static");
  await setIdle({idleAutoPlay:0,idleExpressionList:["exp_01"],idleMotionList:["mtn_01"],idleIntervalSeconds:3}); await page.reload({waitUntil:"networkidle2"}); if(!await waitCanvas(page))throw Error("canvas B missing"); const b=await diff(page,"pose-custom-"); console.log(`[B] auto=0 自定义待机 diff=${(b*100).toFixed(3)}%`); if(b<.002)throw Error("B custom idle did not move");
  await setIdle({idleAutoPlay:1,idleExpressionList:[],idleMotionList:[],idleIntervalSeconds:0}); await page.reload({waitUntil:"networkidle2"}); if(!await waitCanvas(page))throw Error("canvas C missing"); const c=await diff(page,"pose-auto-"); console.log(`[C] auto=1 库待机 diff=${(c*100).toFixed(3)}%`); if(c<.002)throw Error("C auto idle did not move");
  await setIdle({idleAutoPlay:1,idleExpressionList:[],idleMotionList:[],idleIntervalSeconds:0}); const e=errors.filter(x=>!x.includes("404")&&!x.includes("Failed to load resource")&&!x.includes("Cubism")); if(e.length)throw Error(JSON.stringify(e)); await browser.close(); console.log("===== pose fix E2E passed =====");
}
main().catch(e=>{console.error("E2E failed:",e.message);process.exit(1)});