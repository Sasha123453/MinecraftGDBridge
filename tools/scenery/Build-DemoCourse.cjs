'use strict';
// Original, reproducible geometry seed and off-plane decoration. No runtime control or game assets.
const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'outputs/scenery/demo-course');
fs.mkdirSync(out,{recursive:true});
const seed=20261004,sceneryMaxX=445,routeMaxX=397;
const hash=s=>crypto.createHash('sha256').update(s).digest('hex');
const write=(file,value)=>{const text=JSON.stringify(value,null,2)+'\n';fs.writeFileSync(path.join(out,file),text);return {file,sha256:hash(text)};};
const short=JSON.parse(fs.readFileSync(path.join(root,'outputs/scenery/reference-scene/reference-auto-source.json'),'utf8'));
const header=short.rawLevelString.split(';')[0];
const samples=[];
function object(id,x,y,role){samples.push({id,x,y,editorY:y-90,scale:1,rotation:0,flipX:false,flipY:false,role});}
function at(id,cellX,cellY,role){object(id,cellX*30+15,cellY*30+105,role);}
// Start with the runtime-verified original cube sequence, then a comfortable second jump pair.
for(const sample of short.fixtureMetadata.samples)object(sample.id,sample.x,sample.y,`Cube opening: ${sample.role}`);
for(const padX of [65,77]){object(35,padX*30+15,90,'Yellow pad before introductory two-spike jump');at(8,padX+2,0,'Cube opening spike');at(8,padX+3,0,'Cube opening spike');}
object(13,2565,135,'Native ship portal: X85.5, begin genuine flight section');samples.at(-1).scale=2;
// Flight ceiling is ordinary Minecraft stone brick. Small stepped floor islands leave 7+ blocks clear.
for(let x=88;x<=168;x++)at(1,x,10,'Ship corridor ceiling, real full Minecraft stone-brick cell');
for(const [x,height,width] of [[99,2,3],[119,3,3],[141,2,4],[159,2,2]])for(let dx=0;dx<width;dx++)for(let y=0;y<height;y++)at(1,x+dx,y,'Ship corridor low stone island');
for(const x of [102,122,145])at(8,x,0,'Ship floor stone spike beside island');
object(36,109*30+15,255,'Optional yellow orb suspended in ship clearing');
object(36,150*30+15,285,'Optional yellow orb suspended in ship clearing');
object(47,5115,255,'Native ball portal: X170.5, intersects the real ship flight lane');samples.at(-1).scale=2;
// Genuine ball gravity flips use an actual Minecraft ceiling, matching the flight sections.
for(let x=172;x<=228;x++)at(1,x,10,'Ball corridor ceiling, real full Minecraft stone-brick cell');
// Optional orbs are genuine native inputs, not required for the safe gravity-flip corridor.
for(const x of [184,201,218])object(36,x*30+15,135,'Native optional ball-section yellow orb');
object(660,6915,240,'Native wave portal: X230.5, catches either ball gravity lane');samples.at(-1).scale=3.5;
for(let x=233;x<=328;x++)at(1,x,10,'Wave corridor ceiling, real full Minecraft stone-brick cell');
for(const [x,height,width] of [[242,2,3],[263,3,3],[286,2,4],[308,3,3]])for(let dx=0;dx<width;dx++)for(let y=0;y<height;y++)at(1,x+dx,y,'Wave corridor stepped low island');
for(const x of [248,270,295,315])at(8,x,0,'Wave floor stone spike');
object(36,277*30+15,255,'Optional native orb inside the wave corridor');
object(12,9915,255,'Native cube portal: X330.5, intersects the real wave flight lane');samples.at(-1).scale=2;
// Safe pad/spike distances match the already completed reference sequence.
for(const [x,count] of [[339,1],[350,2],[362,3]]){object(35,x*30+15,90,'Final cube automatic yellow pad');for(let i=0;i<count;i++)at(8,x+2+i,0,'Final cube stone spike');}
object(35,374*30+15,90,'Final yellow pad before the low landing platform');
at(1,376,0,'Final low landing platform west');at(1,377,0,'Final low landing platform east');
const occupied=new Set();for(const s of samples){const key=`${s.x}:${s.y}`;if(occupied.has(key))throw Error(`Duplicate authored center ${key}`);occupied.add(key);}
const raw=header+';'+samples.map(s=>`1,${s.id},2,${s.x},3,${s.editorY},6,0,32,${s.scale};`).join('');
const gzip=zlib.gzipSync(Buffer.from(raw),{level:9});if(zlib.gunzipSync(gzip).toString()!==raw)throw Error('Compression roundtrip failed');
const counts={solid:0,spike:0,pad:0,orb:0,portal:0};for(const s of samples)counts[s.id===1?'solid':s.id===8?'spike':s.id===35?'pad':s.id===36?'orb':'portal']++;
const sections=[{name:'Cube forest opening',mode:'cube',fromX:0,toX:2565},{name:'Ship under the stone canopy',mode:'ship',fromX:2565,toX:5115},{name:'Ball clearing',mode:'ball',fromX:5115,toX:6915},{name:'Wave ruins corridor',mode:'wave',fromX:6915,toX:9915},{name:'Cube final jumps',mode:'cube',fromX:9915,toX:11325}];
const source=write('demo-course-source.json',{fixture:true,fixtureVersion:1,provenance:'Original multi-mode demonstration course. CC0-1.0 geometry, adapted from the original verified bridge reference fixture. No downloaded levels/assets.',license:'CC0-1.0',id:900000105,levelId:900000105,name:'Bridge Forest Demonstration',creator:'MinecraftGDBridge',songId:0,audioTrack:0,downloadMusic:false,levelString:gzip.toString('base64url')+'=='.slice(0,(3-gzip.length%3)%3),rawLevelString:raw,fixtureMetadata:{purpose:'Approximately 40-second normal native-physics demonstration after Minecraft import/export, with cube, ship, ball and wave sections.',sourceObjectCount:samples.length,counts,nativeImplicitFloorY:90,nativeNormalSpeedGUPerSecond:311.58,approximateWorldCompiledDurationSeconds:39.6,expectedAuthoringRegion:{minX:-2,maxX:routeMaxX,minY:65,maxY:104},lastObjectX:11325,sections,autoCandidate:false,autoVerified:false,nativePlaybackVerified:false,exactMinecraftGeometryVerified:false,completionVerified:false,compressionRoundtripVerified:true,samples,verification:'Import seed into isolated Minecraft reference world; compile actual server blocks, then record genuine GD ship/wave input with noclip disabled. Native telemetry determines mode and vertical input thresholds.'}});
const ops=[];
function box(block,x0,x1,y0,y1,z0,z1){
 if([x0,x1,y0,y1,z0,z1].some(v=>!Number.isInteger(v))||x0>x1||y0>y1||z0>z1||x0< -16||x1>sceneryMaxX||y0<50||y1>90||z0< -16||z1>12||z0<=0&&z1>=0)throw Error('Unsafe off-plane decoration');
 if(z0>=2&&y1>66)throw Error('Foreground would obscure the icon route');
 const yz=(y1-y0+1)*(z1-z0+1),span=Math.max(1,Math.floor(14500/yz));for(let x=x0;x<=x1;x+=span)ops.push({block,from:[x,y0,z0],to:[Math.min(x1,x+span-1),y1,z1]});
}
// No enormous air cleanup: deterministic additive scene over the compatible short forest envelope.
box('minecraft:dirt',-16,sceneryMaxX,63,65,1,1);box('minecraft:grass_block',-16,sceneryMaxX,66,66,1,1);
box('minecraft:dirt',-16,sceneryMaxX,61,63,2,12);box('minecraft:grass_block',-16,sceneryMaxX,64,64,2,12);
box('minecraft:dirt',-16,sceneryMaxX,64,65,-16,-1);box('minecraft:grass_block',-16,sceneryMaxX,66,66,-16,-1);
function tree(x,z,h,r,birch=false){
 if(x-r-1< -16||x+r>sceneryMaxX)return;
 const wood=birch?'minecraft:birch_log':'minecraft:oak_log',leaves=birch?'minecraft:birch_leaves':'minecraft:oak_leaves';
 box(wood,x,x,67,66+h,z,z);box(leaves,x-r,x+r,65+h,67+h,z-r,z+r);
 box(leaves,x-r+1,x+r-1,68+h,68+h,z-r+1,z+r-1);box(leaves,x-1,x+1,69+h,69+h,z-1,z+1);
 box(leaves,x-r-1,x-r-1,66+h,67+h,z-1,z+1);
}
for(let x=-9,i=0;x<sceneryMaxX;i++,x+=9+(i%4)){
 // Occasional clearings let distant layers show through the nearest canopy.
 if(i%7!==4)tree(x,-6-(i%3),5+(i%3),3,i%8===5);
}
for(let x=-3,i=0;x<sceneryMaxX;i++,x+=11+(i%3))tree(x,-12-(i%2),4+(i%2),2,i%5===2);
for(let x=-12,i=0;x<sceneryMaxX-3;i++,x+=6+(i%3)){
 const z=-4-(i%2);box('minecraft:oak_leaves',x,x+3,67,68,z,z+1);box('minecraft:oak_leaves',x+1,x+2,69,69,z,z);
}
for(let x=-12,i=0;x<sceneryMaxX-4;i++,x+=11+(i%5)){
 const z=3+(i%2),w=3+(i%2);box('minecraft:dirt',x,x+w-1,64,64,z,z+1);box('minecraft:grass_block',x,x+w-1,65,65,z,z+1);box('minecraft:grass',x+1,x+w-2,66,66,z,z);
 box(i%3?'minecraft:stone_bricks':'minecraft:mossy_cobblestone',x+6,x+7,65,65,5,5);
 if(i%2===0)box('minecraft:moss_carpet',x+4,x+5,65,65,4,4);
}
for(let x=-13,i=0;x<=sceneryMaxX;i++,x+=3){const z=-2-(i%4);box(i%9===0?'minecraft:fern':'minecraft:grass',x,x,67,67,z,z);}
for(let x=4,i=0;x<sceneryMaxX-2;i++,x+=17+(i%5))box(i%3?'minecraft:dandelion':'minecraft:poppy',x,x,67,67,-3-(i%2),-3-(i%2));
// Rear mossy ruins give the flight sections a visual rhythm without intersecting gameplay Z0.
for(const x of [95,116,145,184,210,241,264,286,313,347,376]){
 box('minecraft:mossy_stone_bricks',x,x+1,67,68,-3,-3);box('minecraft:stone_bricks',x+1,x+4,67,67,-3,-3);
 box('minecraft:oak_leaves',x+3,x+4,68,68,-4,-3);
}
const volume=o=>o.from.reduce((n,v,i)=>n*(o.to[i]-v+1),1),batches=[];let batch=[],cells=0;
function flush(){if(batch.length){batches.push({boxes:batch,cells});batch=[];cells=0;}}
for(const op of ops){const n=volume(op);if(n>16000)throw Error('Oversized box');if(cells+n>16000)flush();batch.push(op);cells+=n;}flush();
const applyOrder=batches.map((b,i)=>({...write(`demo-course-scene-${String(i+1).padStart(2,'0')}.json`,{schema:'gdbridge-scenery-plan-v1',name:`Forest demonstration decoration ${i+1}/${batches.length}`,targetWorld:'GDBridge-Reference',seed,timeOfDay:3000,batchIndex:i+1,batchCount:batches.length,cellBudget:b.cells,notes:'Off-plane only; additive warm forest, foreground terraces and mossy flight ruins. Does not overwrite authored Z0 obstacle cells.',boxes:b.boxes}),cells:b.cells,boxes:b.boxes.length}));
const floor=write('demo-course-route-floor.optional.json',{schema:'gdbridge-scenery-plan-v1',name:'Demonstration raised grass route floor',targetWorld:'GDBridge-Reference',timeOfDay:3000,cellBudget:(routeMaxX+17)*3,notes:'Actual Minecraft full-cell floor below authored Y67+ obstacles. Verify imported authoring-region bounds before applying; never replace later world edits automatically.',boxes:[],terrainFloor:[{block:'minecraft:stone',from:[-16,64,0],to:[routeMaxX,64,0]},{block:'minecraft:dirt',from:[-16,65,0],to:[routeMaxX,65,0]},{block:'minecraft:grass_block',from:[-16,66,0],to:[routeMaxX,66,0]}]});
const presentation=write('presentation.json',{world:'GDBridge-Reference',timeOfDay:3000,visualStyle:'volume',camera:{distance:8,offsetX:5.6,height:2.3,playerHeight:1.2,yaw:166,pitch:3.5,cubeDeadzone:3.3,debugHud:false},flightInputHints:{ship:{holdBelowNativeY:210,releaseAboveNativeY:285},wave:{holdBelowNativeY:205,releaseAboveNativeY:310},ball:{inputRequired:true,gravityFlipOnGrounded:true},cube:{inputRequired:false},mustUseFreshNativeModeAndPosition:true,noclip:false,note:'These thresholds are a recording controller starting point, not a verified macro. Native GD remains the physics authority.'}});
const manifest={schema:'gdbridge-demo-course-manifest-v1',generator:'tools/scenery/Build-DemoCourse.cjs',deterministic:true,seed,source,sourceObjectCount:samples.length,counts,sections,approximateDurationSeconds:39.6,normalSpeedGUPerSecond:311.58,lastSourceObjectX:11325,expectedAuthoringRegion:{minX:-2,maxX:routeMaxX,minY:65,maxY:104},reference:'outputs/Scene-Target-Selected.png',sceneBounds:{minX:-16,maxX:sceneryMaxX,minY:61,maxY:78,minZ:-16,maxZ:12,forbiddenNormalZ:0},applyOrder,optionalRouteFloor:floor,presentation,totalRequestedCells:applyOrder.reduce((n,p)=>n+p.cells,0),totalBoxes:ops.length,verification:{liveApplied:false,renderVerified:false,geometryVerified:false,normalCompletionVerified:false},preconditions:['Import original native seed into isolated GDBridge-Reference first, then inspect actual exported blueprint-derived bounds.','Off-plane validator needs Reference region+48 for the decorated finish; existing +16 only covers X413.','Apply numbered plans in order, then bounded route floor, and F6 compile actual server cells.','Do not import this seed over later user edits without explicit intent.'],limits:['Duration is an estimate until genuine native playback is recorded.','Flight sections require real input; this is no longer a no-input AUTO course.','Native forgiving spike hitboxes are not enlarged.','Native implicit floor/ceiling authority and broader mode/effect support remain existing limitations.','Decoration is additive over the compatible short reference forest; no destructive huge cleanup.']};
write('manifest.json',manifest);
console.log(JSON.stringify({source,objects:samples.length,counts,lastSourceObjectX:11325,expectedRegionMaxX:routeMaxX,sceneryMaxX,plans:applyOrder.length,cells:manifest.totalRequestedCells,approximateDurationSeconds:39.6},null,2));
