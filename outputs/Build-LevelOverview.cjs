const fs=require('node:fs'),path=require('node:path');
const levels=path.resolve(__dirname,'bridge/levels');
const input=path.resolve(process.argv[2]||path.join(levels,'level-58898913.json'));
if(!input.startsWith(levels+path.sep))throw Error('Blueprint must be under outputs/bridge/levels');
const source=JSON.parse(fs.readFileSync(input,'utf8'));
if(!Array.isArray(source.objects))throw Error('Blueprint objects missing');
const chapterPlan=JSON.parse(fs.readFileSync(path.join(levels,'multi-location-layout.json'),'utf8'));
const objects=source.objects.filter(o=>Number.isFinite(o.x)&&Number.isFinite(o.y)).map((o,index)=>({index,id:o.objectId??o.id,type:o.type||'unknown',x:o.x/30,y:64+o.y/30,w:Math.max(.02,(o.w||o.vw||30)/30),h:Math.max(.02,(o.h||o.vh||30)/30),vw:Math.max(.02,(o.vw||o.w||30)/30),vh:Math.max(.02,(o.vh||o.h||30)/30),rotation:o.rotation||0,invisible:!!o.invisible,disabled:!!o.disabled}));
const data={name:source.name||path.basename(input),levelId:source.levelId||0,input,builtAt:new Date().toISOString(),recordSource:source.recordSource||'',objects,chapters:chapterPlan.locations.map(o=>({name:o.name,rangeX:o.rangeX})),types:[...new Set(objects.map(o=>o.type))]};
const target=path.resolve(process.argv[3]||path.join(__dirname,'Level-Overview.html'));
if(!target.startsWith(path.resolve(__dirname)+path.sep)||path.extname(target)!=='.html')throw Error('Output must be HTML under outputs');
const payload=JSON.stringify(data).replace(/</g,'\\u003c');
const html=String.raw`<!doctype html>
<html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Native GD level overview</title>
<style>*{box-sizing:border-box}body{margin:0;background:#101820;color:#eaf2f9;font:14px system-ui}header{height:104px;padding:14px 20px;background:#152431;border-bottom:1px solid #34485a}h1{font-size:19px;margin:0 0 9px}button,label{margin-right:14px}button{background:#293d50;color:white;border:1px solid #4b667e;border-radius:6px;padding:7px 12px;cursor:pointer}.muted{color:#b3c4d4;font-size:12px}canvas{display:block;width:100vw;height:calc(100vh - 104px)}#tip{display:none;position:fixed;pointer-events:none;background:#07131ff0;border:1px solid #84a0b9;border-radius:7px;padding:10px;white-space:pre;line-height:1.5}#notice{position:absolute;right:18px;bottom:18px;max-width:510px;font-size:12px;background:#142331e8;padding:9px;border-radius:6px}</style>
<header><h1 id="title"></h1><button id="fit">Fit whole level</button><button id="edit">First 512 blocks</button><label><input id="invisible" type="checkbox" checked>Invisible / disabled records</label><label><input id="visual" type="checkbox">Rotated visual bounds</label><span class="muted">Drag to pan · wheel to zoom · hover to inspect</span><div class="muted" id="summary"></div></header><canvas id="canvas"></canvas><div id="tip"></div><div id="notice">Physical boxes come from the exported blueprint. Native types are shown as exported. Chapter labels are the staged cave/library/Nether/forest adaptation within the first 512 blocks; they are not a full Nasgubb reconstruction.</div>
<script>
const D=__DATA__,canvas=document.querySelector('#canvas'),ctx=canvas.getContext('2d'),tip=document.querySelector('#tip');
document.querySelector('#title').textContent=D.name+' · native level '+D.levelId;
const counts={};for(const o of D.objects)counts[o.type]=(counts[o.type]||0)+1;
document.querySelector('#summary').textContent=D.objects.length+' physical records · '+Object.entries(counts).map(([k,v])=>k+': '+v).join(' / ')+' · Minecraft block coordinates · generated '+D.builtAt;
let W=0,H=0,k=1,tx=0,ty=0,drag=null,moved=false;
const colors={solid:'#91aac1',hazard:'#ff786d',orb:'#ffd45b',portal:'#69dcf0',unknown:'#b3a4d5'};
const ext=D.objects.reduce((b,o)=>({x0:Math.min(b.x0,o.x-o.w/2),x1:Math.max(b.x1,o.x+o.w/2),y0:Math.min(b.y0,o.y-o.h/2),y1:Math.max(b.y1,o.y+o.h/2)}),{x0:0,x1:512,y0:67,y1:100});
function sx(x){return tx+x*k}function sy(y){return ty-y*k}
function fit(b=ext){k=Math.min((W-70)/Math.max(10,b.x1-b.x0),(H-160)/Math.max(12,b.y1-b.y0));tx=35-b.x0*k;ty=H-45+b.y0*k;draw()}
function resize(){W=window.innerWidth;H=window.innerHeight-104;const dpr=window.devicePixelRatio||1;canvas.width=Math.round(W*dpr);canvas.height=Math.round(H*dpr);ctx.setTransform(dpr,0,0,dpr,0,0);fit()}
function draw(){const showInvisible=document.querySelector('#invisible').checked,showVisual=document.querySelector('#visual').checked;ctx.clearRect(0,0,W,H);ctx.fillStyle='#101820';ctx.fillRect(0,0,W,H);
 let step=1;while(step*k<65)step*=step<10?2:5;const x0=-tx/k,x1=(W-tx)/k,y0=(ty-H)/k,y1=ty/k;
 ctx.strokeStyle='#243746';ctx.fillStyle='#7994aa';ctx.font='11px system-ui';ctx.lineWidth=1;
 for(let x=Math.ceil(x0/step)*step;x<x1;x+=step){ctx.beginPath();ctx.moveTo(sx(x),55);ctx.lineTo(sx(x),H);ctx.stroke();ctx.fillText(x.toFixed(0),sx(x)+3,H-12)}
 for(let y=Math.ceil(y0/step)*step;y<y1;y+=step){ctx.beginPath();ctx.moveTo(0,sy(y));ctx.lineTo(W,sy(y));ctx.stroke();ctx.fillText(y.toFixed(0),4,sy(y)-3)}
 ctx.fillStyle='#5aa75a16';ctx.fillRect(sx(0),sy(100),512*k,33*k);ctx.strokeStyle='#a3dc78';ctx.setLineDash([6,4]);ctx.strokeRect(sx(0),sy(100),512*k,33*k);ctx.setLineDash([]);
 ctx.strokeStyle='#dee6ee55';ctx.beginPath();ctx.moveTo(sx(0),sy(67));ctx.lineTo(sx(ext.x1),sy(67));ctx.stroke();
 for(const o of D.objects){if(!showInvisible&&(o.invisible||o.disabled))continue;const x=sx(o.x),y=sy(o.y),w=Math.max(1,o.w*k),h=Math.max(1,o.h*k);if(x+w<0||x-w>W||y+h<52||y-h>H)continue;ctx.globalAlpha=(o.invisible||o.disabled)?0.32:0.88;ctx.fillStyle=colors[o.type]||colors.unknown;ctx.fillRect(x-w/2,y-h/2,w,h);if(showVisual&&k>3){ctx.save();ctx.translate(x,y);ctx.rotate(o.rotation*Math.PI/180);ctx.strokeStyle=colors[o.type]||colors.unknown;ctx.setLineDash([3,2]);ctx.strokeRect(-o.vw*k/2,-o.vh*k/2,o.vw*k,o.vh*k);ctx.restore()}}
 ctx.globalAlpha=1;ctx.fillStyle='#132939';ctx.fillRect(0,0,W,52);ctx.font='12px system-ui';for(let i=0;i<D.chapters.length;i++){const c=D.chapters[i],x=sx(c.rangeX[0]),w=(c.rangeX[1]-c.rangeX[0])*k;ctx.fillStyle=['#696d74','#a88554','#bca16d','#974442','#507b43'][i%5];ctx.fillRect(x,9,w,20);if(w>45){ctx.fillStyle='#fff';ctx.fillText(c.name+' '+c.rangeX[0]+'–'+c.rangeX[1],x+4,23)}}ctx.fillStyle='#bbccdc';ctx.fillText('Staged chapters · editable zone X0–512 / Y67–100',12,45);
}
canvas.addEventListener('wheel',e=>{e.preventDefault();const beforeX=(e.offsetX-tx)/k,beforeY=(ty-e.offsetY)/k;k=Math.max(.05,Math.min(180,k*Math.exp(-e.deltaY*.0015)));tx=e.offsetX-beforeX*k;ty=e.offsetY+beforeY*k;tip.style.display='none';draw()},{passive:false});
canvas.addEventListener('pointerdown',e=>{drag={x:e.clientX,y:e.clientY,tx,ty};moved=false;canvas.setPointerCapture(e.pointerId)});canvas.addEventListener('pointerup',()=>drag=null);canvas.addEventListener('pointerleave',()=>tip.style.display='none');
canvas.addEventListener('pointermove',e=>{if(drag){tx=drag.tx+e.clientX-drag.x;ty=drag.ty+e.clientY-drag.y;tip.style.display='none';draw();return}const x=(e.offsetX-tx)/k,y=(ty-e.offsetY)/k;let best=null,area=Infinity;for(const o of D.objects){if(!document.querySelector('#invisible').checked&&(o.invisible||o.disabled))continue;if(Math.abs(x-o.x)<=Math.max(o.w/2,4/k)&&Math.abs(y-o.y)<=Math.max(o.h/2,4/k)&&o.w*o.h<area){best=o;area=o.w*o.h}}if(!best){tip.style.display='none';return}tip.textContent='Native object ID '+best.id+' · '+best.type+'\nMC center '+best.x.toFixed(2)+', '+best.y.toFixed(2)+'\nCollision bbox '+best.w.toFixed(2)+' × '+best.h.toFixed(2)+' blocks\nRotation '+best.rotation+'° · visual '+best.vw.toFixed(2)+' × '+best.vh.toFixed(2)+'\nInvisible '+best.invisible+' · disabled '+best.disabled;tip.style.display='block';tip.style.left=Math.min(W-320,e.clientX+14)+'px';tip.style.top=Math.min(window.innerHeight-165,e.clientY+12)+'px'});
document.querySelector('#fit').onclick=()=>fit();document.querySelector('#edit').onclick=()=>fit({x0:0,x1:512,y0:61,y1:105});document.querySelector('#invisible').onchange=draw;document.querySelector('#visual').onchange=draw;window.onresize=resize;resize();
</script></html>`;
fs.writeFileSync(target,html.replace('__DATA__',payload),'utf8');
console.log(JSON.stringify({target,input,objects:objects.length,types:data.types,levelId:data.levelId},null,2));
