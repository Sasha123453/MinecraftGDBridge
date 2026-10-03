'use strict';
// XO-only deterministic scenery. Emits staged plans; never connects to or edits games.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../..');
const local=JSON.parse(fs.readFileSync(path.join(root,'.local.json'),'utf8'));
const runtime=path.resolve(process.argv[2]||local.runtimeRoot);
const source=path.join(runtime,'outputs/bridge/levels/level-58835426.json');
const native=JSON.parse(fs.readFileSync(source,'utf8'));
const output=path.join(root,'outputs/scenery/xo-full-scene');fs.mkdirSync(output,{recursive:true});
const chapters=[
 {id:'cave',from:-16,to:120,style:'cave'},
 {id:'library',from:121,to:270,style:'library'},
 {id:'nether',from:271,to:383,style:'nether'},
 {id:'sunny-forest',from:384,to:640,style:'forest'},
 {id:'deep-mines',from:641,to:860,style:'cave'},
 {id:'lush-ravine',from:861,to:1040,style:'ravine'},
 {id:'nether-fortress',from:1041,to:1190,style:'nether'},
 {id:'forest-river',from:1191,to:1440,style:'forest'},
 {id:'forest-finale',from:1441,to:1632,style:'forest'}
];
const ops=[];
function box(chapter,block,from,to,reason){
 if(from.some((v,i)=>!Number.isInteger(v)||v>to[i]))throw Error('Invalid box');
 if(from[0]<-16||to[0]>1632||from[1]<50||to[1]>160||from[2]<-16||to[2]>24||from[2]<=0&&to[2]>=0)throw Error('Unsafe scenery: '+JSON.stringify({from,to}));
 if(from[2]>0&&to[1]>66&&!/roof/.test(reason||''))throw Error('Foreground reaches corridor');
 // Bound individual operations, preserving ordering. MC validator counts sum of volumes.
 const yz=(to[1]-from[1]+1)*(to[2]-from[2]+1),span=Math.max(1,Math.floor(15000/yz));
 for(let x=from[0];x<=to[0];x+=span)ops.push({chapter,block,from:[x,from[1],from[2]],to:[Math.min(to[0],x+span-1),to[1],to[2]],reason});
}
const count=b=>b.from.reduce((n,v,i)=>n*(b.to[i]-v+1),1);
const B=(c,m,x0,x1,y0,y1,z0,z1,r)=>box(c,m,[x0,y0,z0],[x1,y1,z1],r);
// Only remove exact previous owned roof footprints. No blanket clearing of world/route.
B('owned-roof-cleanup','minecraft:air',0,120,89,90,-10,-1,'Earlier cave-height-expansion rear roof');
B('owned-roof-cleanup','minecraft:air',0,120,89,90,1,12,'Earlier cave-height-expansion foreground roof');
B('owned-roof-cleanup','minecraft:air',0,120,86,88,12,12,'Earlier cave-height-expansion front upper lip roof');
B('owned-roof-cleanup','minecraft:air',132,262,81,81,-6,-1,'Earlier multi-location library rear roof');
B('owned-roof-cleanup','minecraft:air',274,372,79,79,-6,-1,'Earlier multi-location Nether rear roof');
// Keep continuous rear enclosure up to high native XO geometry, avoiding sky at chapter seams.
for(const c of chapters){
 const indoor=c.style==='cave'||c.style==='library'||c.style==='nether';
 if(indoor){
  const mat=c.style==='library'?'minecraft:oak_planks':c.style==='nether'?'minecraft:netherrack':'minecraft:stone';
  B(c.id,mat,c.from,c.to,65,151,-7,-7,'Continuous high rear chapter enclosure');
  B(c.id,mat,c.from,c.to,152,152,-9,-1,'High rear roof, above all native XO objects');
  B(c.id,mat,c.from,c.to,65,65,-6,-1,'Rear floor; gameplay slice untouched');
  // Depth bands/tier details stay behind the playable plane, including high chapters.
  if(c.style==='cave'){
   for(let x=c.from+4;x<=c.to-5;x+=14){
    const k=Math.floor((x-c.from)/14);
    for(const y of [68,85,104,125,143]){
     const material=['minecraft:cobblestone','minecraft:deepslate','minecraft:iron_ore','minecraft:coal_ore','minecraft:diamond_ore'][(k+Math.floor(y/10))%5];
     B(c.id,material,x,Math.min(c.to,x+3),y,y+2,-6,-6,'Deterministic geological stratum');
     B(c.id,'minecraft:stone',x+4,x+4,y,y,-6,-4,'Torch ledge support');
     B(c.id,'minecraft:torch',x+4,x+4,y+1,y+1,-4,-4,'Local cave lighting at each height tier');
    }
   }
  }
  if(c.style==='library'){
   for(let x=c.from+3;x<=c.to;x+=16){
    B(c.id,'minecraft:oak_log',x,Math.min(c.to,x+1),66,151,-6,-5,'Rear timber bay column');
    for(const y of [67,88,110,132]){
     if(x+3<=c.to)B(c.id,'minecraft:bookshelf',x+3,Math.min(c.to,x+12),y,y+6,-6,-6,'Rear library shelf tier');
     if(x+3<=c.to)B(c.id,'minecraft:oak_planks',x+3,Math.min(c.to,x+12),y+7,y+7,-6,-4,'Shelf tier wooden lintel');
     if(x+6<=c.to){B(c.id,'minecraft:oak_planks',x+6,x+8,y+1,y+1,-6,-3,'Reading balcony behind route');B(c.id,'minecraft:lantern',x+7,x+7,y+2,y+2,-3,-3,'Library warm lantern');}
    }
   }
  }
  if(c.style==='nether'){
   for(let x=c.from+4;x<=c.to-4;x+=18){
    B(c.id,'minecraft:nether_bricks',x,x+2,66,150,-6,-5,'Rear fortress column');
    for(const y of [70,92,114,136]){
     B(c.id,'minecraft:nether_bricks',x+3,Math.min(c.to,x+14),y,y+1,-6,-5,'Rear fortress walkway');
     B(c.id,'minecraft:nether_bricks',x+4,x+4,y+2,y+2,-5,-4,'Torch buttress');
     B(c.id,'minecraft:torch',x+4,x+4,y+3,y+3,-4,-4,'Nether local warm lighting');
    }
   }
  }
 }else{
  B(c.id,'minecraft:dirt',c.from,c.to,62,64,-16,-3,'Rear forest earth bank');
  B(c.id,'minecraft:grass_block',c.from,c.to,65,65,-16,-3,'Rear forest grass bank');
  // Two staggered forest layers, reference-sized crowns, deterministic spacing.
  for(let layer=0;layer<2;layer++)for(let x=c.from+5+layer*6;x<=c.to-5;x+=17){
   const z=layer===0?-7:-13,k=Math.floor((x-c.from)/17),h=5+(k+layer)%3;
   tree(c.id,x,66,z,h,layer===0?3:2);
  }
  // Front terrain stays below Y67; no new front walls, pillars or crowns.
  for(let x=c.from+5;x<=c.to-5;x+=23){
   B(c.id,'minecraft:dirt',x,x+4,63,64,4,7,'Low foreground earth mound');
   B(c.id,'minecraft:grass_block',x,x+4,65,65,4,7,'Low foreground grass terrace');
   B(c.id,'minecraft:grass',x+1,x+2,66,66,5,5,'Foreground grass below gameplay corridor');
  }
  // Elevated rear shelves/trees for high native geometry, rather than empty sky.
  const bins=[];for(let x=Math.max(0,c.from);x<=c.to;x+=32){
   const a=native.objects.filter(o=>o.type==='solid'&&!o.invisible&&!o.disabled&&o.x/30>=x&&o.x/30<x+32).map(o=>64+o.y/30).filter(y=>y>94&&y<150).sort((a,b)=>a-b);
   if(a.length>=6){const ground=Math.max(88,Math.min(141,Math.floor(a[Math.floor(a.length*.25)])-3));bins.push({x,ground,nativeSolids:a.length});}
  }
  c.elevatedTiers=bins;
  for(const t of bins){
   const x1=Math.min(c.to,t.x+28);
   B(c.id,'minecraft:stone',t.x,x1,t.ground-3,t.ground-1,-16,-11,'Elevated rear island stone face');
   B(c.id,'minecraft:grass_block',t.x,x1,t.ground,t.ground,-16,-11,'Elevated rear island grass');
   if(t.x+7<=x1)tree(c.id,t.x+7,t.ground+1,-13,5,2);
   B(c.id,'minecraft:grass',t.x+2,Math.min(x1,t.x+4),t.ground+1,t.ground+1,-12,-12,'Elevated island grass');
  }
  if(c.style==='ravine'){
   for(let x=c.from+2;x<=c.to-6;x+=13){
    const h=15+Math.floor((x-c.from)/13)%5*3;
    B(c.id,'minecraft:stone',x,x+3,66,66+h,-16,-15,'Open-sky ravine rear rock spire');
    B(c.id,'minecraft:oak_leaves',x,x+3,66+h,67+h,-15,-14,'Ravine greenery behind route');
   }
  }
 }
}
function tree(chapter,x,y,z,h,r){
 B(chapter,'minecraft:oak_log',x,x,y,y+h-1,z,z,'Rear oak trunk');
 B(chapter,'minecraft:oak_leaves',x-r,x+r,y+h-2,y+h,z-r,z+r,'Rear block crown broad tier');
 B(chapter,'minecraft:oak_leaves',x-r+1,x+r-1,y+h+1,y+h+1,z-r+1,z+r-1,'Rear block crown upper tier');
}
// Replace short sky slits at cave/library and library/Nether boundaries with architectural transitions.
B('seam-cave-library','minecraft:stone_bricks',111,131,65,151,-6,-6,'Rear stone chapter transition, removes sky slit');
B('seam-library-nether','minecraft:stone_bricks',263,273,65,151,-6,-6,'Rear fortress transition, removes sky slit');
// A sealed decorative river is wholly behind the route. Fluid contained on all sides and below.
const river={id:'forest-river',x:1320};
B(river.id,'minecraft:stone',1320,1352,62,62,-16,-3,'Sealed river bed');
B(river.id,'minecraft:grass_block',1320,1352,63,65,-16,-16,'Sealed river rear bank');
B(river.id,'minecraft:grass_block',1320,1352,63,65,-3,-3,'Sealed river front bank');
B(river.id,'minecraft:grass_block',1320,1320,63,65,-15,-4,'Sealed river west bank');
B(river.id,'minecraft:grass_block',1352,1352,63,65,-15,-4,'Sealed river east bank');
B(river.id,'minecraft:air',1321,1351,63,65,-15,-4,'Clear exact staged river interior before water');
B(river.id,'minecraft:water',1321,1351,63,63,-15,-4,'Contained rear river source layer');
// Packing preserves global operation order, including the river and exact owned cleanup.
const batches=[];let current=[],cells=0;
function flush(){if(current.length){batches.push({boxes:current,cells});current=[];cells=0;}}
for(const op of ops){const n=count(op);if(n>16384)throw Error('Oversized operation');if(cells+n>16000)flush();current.push(op);cells+=n;}flush();
const receipt=[];for(let i=0;i<batches.length;i++){
 const name='xo-scene-'+String(i+1).padStart(2,'0')+'.json',batch=batches[i];
 const plan={schema:'gdbridge-scenery-plan-v1',name:'XO full scene '+(i+1)+'/'+batches.length,targetWorld:'GDBridge-XO',seed:20261003,batchIndex:i+1,batchCount:batches.length,cellBudget:batch.cells,notes:'Scenery only. Apply sequentially after existing intro plans and foreground-caps-cleanup; exact authored roof cleanup first. Native gameplay plane Z0 is forbidden.',boxes:batch.boxes.map(({from,to,block})=>({from,to,block}))};
 const text=JSON.stringify(plan,null,2)+'\n';fs.writeFileSync(path.join(output,name),text);receipt.push({file:name,cells:batch.cells,boxes:batch.boxes.length,sha256:crypto.createHash('sha256').update(text).digest('hex'),chapters:[...new Set(batch.boxes.map(o=>o.chapter))]});
}
// Write roof-only restore. Full rollback requires a world backup.
const restoreOldRoofs={schema:'gdbridge-scenery-plan-v1',name:'Restore only earlier owned roof footprints',targetWorld:'GDBridge-XO',notes:'Optional roof-only restore. Full scene rollback requires root world backup; this does not remove new owned scenery.',boxes:[{from:[0,89,-10],to:[120,90,-1],block:'minecraft:stone'},{from:[0,89,1],to:[120,90,12],block:'minecraft:stone'},{from:[0,86,12],to:[120,88,12],block:'minecraft:deepslate'},{from:[132,81,-6],to:[262,81,-1],block:'minecraft:oak_planks'},{from:[274,79,-6],to:[372,79,-1],block:'minecraft:netherrack'}]};
fs.writeFileSync(path.join(output,'restore-old-roofs.json'),JSON.stringify(restoreOldRoofs,null,2)+'\n');

fs.writeFileSync(path.join(output,'operations.json'),JSON.stringify({seed:20261003,operations:ops},null,2)+'\n');
const bounds={minX:-16,maxX:1632,minY:50,maxY:160,minZ:-16,maxZ:24,forbiddenZ:0};
const manifest={schema:'gdbridge-xo-scene-manifest-v1',generatedBy:'tools/scenery/Build-XOScene.cjs',deterministic:true,seed:20261003,sourceBlueprint:path.relative(runtime,source).replaceAll('\\','/'),sourceSha256:crypto.createHash('sha256').update(fs.readFileSync(source)).digest('hex'),nativeLengthBlocks:Math.max(...native.objects.map(o=>o.x))/30,nativeHighestObjectY:64+Math.max(...native.objects.map(o=>o.y))/30,requiredSceneryBounds:bounds,chapters,applyOrder:receipt,totalRequestedCells:receipt.reduce((n,b)=>n+b.cells,0),totalBoxes:ops.length,preconditions:['Isolated GDBridge-XO world','Existing multi-location and cave-height-expansion plans already applied','Existing foreground-caps-cleanup applied','MC scenery validator supports manifest bounds','Root copies only numbered plans into runtime outputs/bridge/levels and applies in order'],verification:{liveApplied:false,renderVerified:false,physicsChanged:false,routeSliceTouched:false},designLimits:['XO-only fixed chapter layout; general level autogeneration remains deferred','Native original groups/move effects are not inferred as terrain changes','High rear tiers use original native solids, including some group-disabled objects not identifiable from this static blueprint','Background roofs remain above highest native object; full playability requires normal input test of Minecraft quantized geometry']};
fs.writeFileSync(path.join(output,'manifest.json'),JSON.stringify(manifest,null,2)+'\n');
console.log(JSON.stringify({batches:receipt.length,cells:manifest.totalRequestedCells,boxes:ops.length,bounds,chapters:chapters.map(c=>({id:c.id,from:c.from,to:c.to,elevatedTiers:c.elevatedTiers?.length||0}))},null,2));
