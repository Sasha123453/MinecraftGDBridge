'use strict';
// Original authored geometry and deterministic world plans. No game control or licensed assets.
const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'outputs/scenery/reference-scene');
const sceneryMaxX=110; // Off-plane finish scenery only. Native geometry and Z0 floor stop at their original bounds.
fs.mkdirSync(out,{recursive:true});
const hash=s=>crypto.createHash('sha256').update(s).digest('hex');
const write=(name,value)=>{const text=JSON.stringify(value,null,2)+'\n';fs.writeFileSync(path.join(out,name),text);return {file:name,sha256:hash(text)};};
const header='kA2,0,kA3,0,kA4,0,kA6,1,kA7,1,kA8,0,kA10,0,kA11,0,kA22,0,kS1,70,kS2,105,kS3,145,kS4,90,kS5,100,kS6,110,kS7,255,kS8,255,kS9,255,kS10,230,kS11,230,kS12,230';
const object=(id,x,y,role)=>({id,x,y,editorY:y-90,scale:1,rotation:0,flipX:false,flipY:false,role});
const samples=[
 object(35,345,90,'Automatic yellow pad before stepped stone platform'),
 object(1,405,105,'Platform base west'),object(1,435,105,'Platform base center'),object(1,465,105,'Platform base east'),object(1,465,135,'Platform second step'),
 object(36,525,195,'Optional real yellow orb; automatic route does not require input'),
 object(35,645,90,'Automatic yellow pad before single spike'),object(8,705,105,'Single stone spike'),
 object(12,855,135,'Native cube return portal; preserves cube AUTO route'),
 object(35,945,90,'Automatic yellow pad before double spikes'),object(8,1005,105,'Double spike west'),object(8,1035,105,'Double spike east'),
 object(35,1305,90,'Automatic yellow pad before triple spikes'),object(8,1365,105,'Triple spike west'),object(8,1395,105,'Triple spike center'),object(8,1425,105,'Triple spike east'),
 object(35,1605,90,'Automatic yellow pad before final low platform'),
 object(1,1665,105,'Final platform west'),object(1,1695,105,'Final platform center'),object(1,1725,105,'Final platform east')
];
const raw=header+';'+samples.map(o=>`1,${o.id},2,${o.x},3,${o.editorY},6,0,32,1;`).join('');
const gzip=zlib.gzipSync(Buffer.from(raw),{level:9});
if(zlib.gunzipSync(gzip).toString()!==raw)throw Error('Native source compression roundtrip failed');
const level={fixture:true,fixtureVersion:1,provenance:'Original hand-authored sunny reference course, CC0-1.0 geometry; no XO or game assets',license:'CC0-1.0',id:900000104,levelId:900000104,name:'Bridge sunny reference AUTO candidate',creator:'MinecraftGDBridge',songId:0,audioTrack:0,downloadMusic:false,levelString:gzip.toString('base64url')+'=='.slice(0,(3-gzip.length%3)%3),rawLevelString:raw,fixtureMetadata:{purpose:'Short no-input cube route through pixel stone platforms and spikes, with optional orb and cube-return portal. Pad spacing is derived from runtime-verified fixture A; added stepped/wide platforms require fresh playback verification.',sourceObjectCount:samples.length,counts:{solid:7,spike:6,pad:5,orb:1,portal:1},coordinates:'Native GD x/y. Editor Y = native Y - 90. 30 GD units = 1 Minecraft block.',nativeImplicitFloorY:90,autoCandidate:true,autoVerified:false,nativePlaybackVerified:false,exactMinecraftGeometryVerified:false,completionVerified:false,compressionRoundtripVerified:true,samples,verification:'Fresh native blueprint, exact persistent-piece import/export comparison, then no-input noclip-off completion and native Minecraft framebuffer recording.'}};
const sourceReceipt=write('reference-auto-source.json',level);
const ops=[];
function box(block,x0,x1,y0,y1,z0,z1,reason){
 const from=[x0,y0,z0],to=[x1,y1,z1];
 if(from.some((v,i)=>!Number.isInteger(v)||v>to[i])||x0< -16||x1>sceneryMaxX||y0<50||y1>90||z0< -16||z1>12||z0<=0&&z1>=0)throw Error('Unsafe off-plane scenery');
 if(z0>=2&&y1>66)throw Error('Foreground crosses playable icon height');
 const yz=(y1-y0+1)*(z1-z0+1),span=Math.max(1,Math.floor(15000/yz));
 for(let x=x0;x<=x1;x+=span)ops.push({block,from:[x,y0,z0],to:[Math.min(x1,x+span-1),y1,z1],reason});
}
const count=o=>o.from.reduce((n,v,i)=>n*(o.to[i]-v+1),1);
// Clear the owned off-plane showcase envelope before authoring a new scene; never erase Z0 pieces.
box('minecraft:air',-16,sceneryMaxX,65,84,-16,-1,'Clear reference background footprint');
box('minecraft:air',-16,sceneryMaxX,62,66,2,12,'Clear lower foreground footprint');
box('minecraft:air',-16,sceneryMaxX,67,67,1,1,'Clear near-lane vegetation only');
// Main lane is one-block thick in front of actual obstacle cells. Lower front terraces reveal dirt faces.
box('minecraft:dirt',-16,sceneryMaxX,63,65,1,1,'Near-lane exposed dirt side');
box('minecraft:grass_block',-16,sceneryMaxX,66,66,1,1,'Main lane front grass lip');
box('minecraft:dirt',-16,sceneryMaxX,61,63,2,12,'Lower foreground earth');
box('minecraft:grass_block',-16,sceneryMaxX,64,64,2,12,'Lower foreground lawn');
box('minecraft:dirt',-16,sceneryMaxX,63,65,-16,-1,'Continuous rear earth bank');
box('minecraft:grass_block',-16,sceneryMaxX,66,66,-16,-1,'Rear meadow bank');
// Staggered oak layers, organic block crowns, no equal-spaced flat wall of trees.
function tree(x,z,h,r){
 box('minecraft:oak_log',x,x,67,66+h,z,z,'Rear oak trunk');
 box('minecraft:oak_leaves',x-r,x+r,65+h,67+h,z-r,z+r,'Oak broad crown');
 box('minecraft:oak_leaves',x-r+1,x+r-1,68+h,68+h,z-r+1,z+r-1,'Oak top crown');
 box('minecraft:oak_leaves',x-1,x+1,69+h,69+h,z-1,z+1,'Oak small raised crown tier');
 box('minecraft:oak_leaves',x-r-1,x-r-1,66+h,67+h,z-1,z+1,'Asymmetric oak crown branch');
}
for(const [x,z,h,r] of [[-9,-7,5,3],[1,-6,6,3],[12,-8,5,3],[23,-6,7,3],[35,-7,5,3],[46,-8,6,3],[59,-6,5,3],[70,-7,6,3],[82,-6,5,3],[94,-8,6,3],[106,-7,5,3],[-3,-13,4,2],[7,-12,5,2],[18,-13,4,2],[29,-12,5,2],[41,-13,4,2],[52,-12,5,2],[64,-13,5,2],[76,-13,4,2],[88,-12,5,2],[99,-13,4,2],[108,-12,5,2]])tree(x,z,h,r);
for(let x=-12;x<=103;x+=7){
 const z=-4-Math.abs(x)%2;
 box('minecraft:oak_leaves',x,x+3,67,68,z,z+1,'Low rear shrub screens distant gray horizon');
 box('minecraft:oak_leaves',x+1,x+2,69,69,z,z,'Small shrub upper clump');
}
// Low front mounds and discrete stones frame the track without hiding the route.
for(const [x,z,w,d] of [[-12,3,4,2],[-2,4,3,2],[8,3,4,2],[21,4,4,2],[31,3,3,2],[45,4,4,2],[55,3,3,2],[68,4,3,2],[80,3,4,2],[91,4,3,2],[103,3,4,2]]){
 box('minecraft:dirt',x,x+w-1,64,64,z,z+d-1,'Foreground terrace earth');
 box('minecraft:grass_block',x,x+w-1,65,65,z,z+d-1,'Foreground terrace grass cap');
 box('minecraft:grass',x+1,x+w-2,66,66,z,z,'Terrace grass tuft');
}
for(const [x,z,w] of [[-5,4,2],[6,5,2],[18,3,2],[28,5,3],[40,4,2],[51,5,2],[62,3,2],[75,4,2],[86,5,2],[99,3,2]])box('minecraft:stone_bricks',x,x+w-1,65,65,z,z,'Low foreground chipped stone detail');
for(let x=-13;x<=109;x+=3){const z=-2-(Math.abs(x*17)%4);box('minecraft:grass',x,x,67,67,z,z,'Rear wild meadow tuft');}
for(const [x,z] of [[-7,-3],[6,-4],[16,-2],[27,-3],[39,-4],[50,-2],[61,-3],[71,-4]])box('minecraft:fern',x,x,67,67,z,z,'Rear fern among wild grass');
for(const [x,z] of [[0,-3],[14,-4],[25,-2],[38,-3],[53,-4],[67,-2]])box(x%2?'minecraft:poppy':'minecraft:dandelion',x,x,67,67,z,z,'Sparse rear meadow flowers');
for(const [x,z] of [[2,4],[16,5],[37,4],[58,5],[83,4],[101,5]])box('minecraft:moss_carpet',x,x+1,65,65,z,z,'Low foreground moss detail');
for(const [x,z] of [[3,-3],[18,-4],[32,-2],[48,-4],[65,-3]]){
 box('minecraft:stone_bricks',x,x+1,67,67,z,z,'Rear half-hidden stone relic');
 box('minecraft:mossy_stone_bricks',x+1,x+1,67,67,z,z,'Moss on rear stone relic');
}
const batches=[];let current=[],cells=0;
const flush=()=>{if(current.length){batches.push({ops:current,cells});current=[];cells=0;}};
for(const op of ops){const n=count(op);if(n>16384)throw Error('Operation budget exceeded');if(cells+n>16000)flush();current.push(op);cells+=n;}flush();
const receipts=batches.map((batch,i)=>{const file=`reference-scene-${String(i+1).padStart(2,'0')}.json`;return {...write(file,{schema:'gdbridge-scenery-plan-v1',name:`Sunny reference scene ${i+1}/${batches.length}`,targetWorld:'GDBridge-Reference',seed:20261003,timeOfDay:1800,batchIndex:i+1,batchCount:batches.length,cellBudget:batch.cells,notes:'Apply ordered to isolated showcase world after native geometry import. Every normal box stays off Z0. Bright morning lighting; no cave/XO decorations.',boxes:batch.ops.map(({block,from,to})=>({block,from,to}))}),cells:batch.cells,boxes:batch.ops.length};});
const floorReceipt=write('reference-route-floor.optional.json',{schema:'gdbridge-scenery-plan-v1',name:'Sunny reference route floor materials',targetWorld:'GDBridge-Reference',timeOfDay:1800,cellBudget:273,notes:'Requires scenery validator to permit dirt/grass_block terrainFloor. Same full-cell collision envelope below native floor; never changes authored pieces at Y67+.',boxes:[],terrainFloor:[{block:'minecraft:stone',from:[-16,64,0],to:[74,64,0]},{block:'minecraft:dirt',from:[-16,65,0],to:[74,65,0]},{block:'minecraft:grass_block',from:[-16,66,0],to:[74,66,0]}]});
const manifest={schema:'gdbridge-reference-scene-manifest-v1',generator:'tools/scenery/Build-ReferenceScene.cjs',deterministic:true,seed:20261003,source:sourceReceipt,sourceObjectCount:samples.length,nominalCourseBlocks:58,reference:'outputs/Scene-Target-Selected.png',sceneBounds:{minX:-16,maxX:sceneryMaxX,minY:61,maxY:84,minZ:-16,maxZ:12,forbiddenNormalZ:0},applyOrder:receipts,optionalRouteFloor:floorReceipt,totalRequestedCells:receipts.reduce((n,r)=>n+r.cells,0),totalBoxes:ops.length,visualDesign:['Warm bright forest instead of XO environments','Near lane grass edge with exposed dirt and a lower layered foreground','Visible foreground terraces and stones at Z3..5, below the icon route','Two staggered oak layers with small raised crown tiers and low rear shrubs','Off-plane finish decoration extends to X110; native20 objects and Z0 floor remain unchanged','Grass clumps, mossed rear relics and low foreground stones','Native original avatar with optional pixel textured special models provided separately'],preconditions:['Isolated GDBridge-Reference world and fresh reference-auto-source native load/import','Scenery API permits isolated reference world, fern/flowers/moss and off-plane X110','Root copies numbered JSON plans into runtime outputs/bridge/levels, then applies in order','Remove unrelated former off-plane geometry outside this footprint separately if it is visible'],verification:{liveApplied:false,renderVerified:false,autoVerified:false,geometryVerified:false},limits:['AUTO spacing starts from proven fixture A, but added platform steps and wider landing platform need runtime validation','Cube-return portal keeps native cube gameplay; optional cyan model changes appearance only','Orb is a real native interactive object but is not necessary for the no-input route','Scenery never touches gameplay Z0; route recolor is separately bounded below native floor','No generated texture, shader output or concept is a live evidence frame']};
write('manifest.json',manifest);
console.log(JSON.stringify({source:sourceReceipt,objects:samples.length,plans:receipts.length,cells:manifest.totalRequestedCells,boxes:ops.length},null,2));
