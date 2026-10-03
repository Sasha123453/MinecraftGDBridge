const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),boxes=[],plants=[];
function box(block,x,y,z,xx=x,yy=y,zz=z){boxes.push({block:'minecraft:'+block,from:[x,y,z],to:[xx,yy,zz]});}
function plant(block,x,y,z){plants.push({block:'minecraft:'+block,at:[x,y,z]});}
function tree(x,z,height,birch=false){const log=birch?'birch_log':'oak_log',leaves=birch?'birch_leaves':'oak_leaves';box(log,x,66,z,x,66+height,z);box(leaves,x-2,65+height,z-2,x+2,67+height,z+2);box(leaves,x-1,68+height,z-1,x+1,68+height,z+1);}
// Dense far tree line supplements the generated nearer oaks at z=-9.
for(let x=8;x<=488;x+=24)tree(x,-13,6+(Math.floor(x/24)%2));
for(let x=28;x<=496;x+=72)tree(x,-12,7,true);
// Modest low hills leave gaps between clusters and keep the tree trunks readable.
for(let x=10;x<=482;x+=36){box('dirt',x,65,-7,x+9,66,-4);box('grass_block',x,67,-7,x+9,67,-4);box('grass_block',x+3,68,-6,x+6,68,-5);plant('grass',x+1,68,-5);plant('grass',x+7,68,-6);plant('grass',x+4,69,-5);}
// Raised grass plinths near the image's lower edge, below the player silhouette.
for(let x=8;x<=488;x+=30){const z=(Math.floor(x/30)%2)?4:3;box('dirt',x,64,z,x+5,65,z+3);box('grass_block',x,66,z,x+5,66,z+3);for(const [dx,dz] of [[1,1],[3,2],[4,0]])plant('grass',x+dx,67,z+dz);}
// A second foreground layer gives genuine parallax without tall close trees.
for(let x=22;x<=484;x+=42){box('dirt',x,64,9,x+7,64,12);box('grass_block',x,65,9,x+7,65,12);plant('grass',x+2,66,10);plant('grass',x+5,66,11);}
// Grass patches alongside the protected native lane, on existing terrain.
for(let x=3;x<=507;x+=12){plant('grass',x,66,-3);plant('grass',x+2,66,-4);plant('grass',x,65,2);}
// Occasional low Minecraft stone fragments; no continuous background wall.
for(let x=54;x<=486;x+=72){box('mossy_stone_bricks',x,66,-5,x+2,66,-5);box('stone_bricks',x+18,65,7,x+20,65,8);}
let cells=0;const unique=new Set();function pos([x,y,z]){if(x<0||x>512||y<50||y>90||z<-16||z>24||z===0)throw Error('Unsafe cell');}
for(const b of boxes){pos(b.from);pos(b.to);if(b.from[2]<=0&&b.to[2]>=0)throw Error('Gameplay touched');cells+=(b.to[0]-b.from[0]+1)*(b.to[1]-b.from[1]+1)*(b.to[2]-b.from[2]+1);for(let x=b.from[0];x<=b.to[0];x++)for(let y=b.from[1];y<=b.to[1];y++)for(let z=b.from[2];z<=b.to[2];z++)unique.add(`${x},${y},${z}`);}for(const p of plants){pos(p.at);cells++;unique.add(p.at.join(','));}if(cells>16384)throw Error('Batch too large '+cells);
const plan={schema:'gdbridge-scenery-plan-v1',name:'Selected sunny layered Minecraft forest',state:'generated-ready-for-applyScenery',targetWorld:'GDBridge-XO',referenceImage:'outputs/Scene-Target-Selected.png',coordinateSystem:'Minecraft integer block coordinates; positive Z foreground; negative Z background',preserve:{gameplaySliceZ:0,nativePhysics:true,existingAuthoringMarkers:true},timeOfDay:3000,cleanupOwnedBackWalls:{},boxes,plants,validation:{authoredCellsIncludingOverlaps:cells,distinctAuthoredCells:unique.size,maxBatchCells:16384,gameplayCellsTouched:0,materials:[...new Set([...boxes,...plants].map(b=>b.block))].sort()},applicationNotes:['Scenery only: no fixed gameplay blueprint, native icon, orb, portal or pad placement is requested by this plan.','Preserve generated nearer oaks at z=-9; add a layered oak/birch line farther behind.','All foreground surfaces stay at y=65..66 with grass above; no tall close foreground tree trunks or continuous background wall.','Warm Minecraft daylight time 3000. Camera, native sprite identities and gameplay remain under the existing controller.','Cleanup removes only the known generated stone-brick back-wall footprint implemented by applyScenery.']};
const filename=path.join(root,'outputs/bridge/levels/selected-forest-layout.json');fs.writeFileSync(filename,JSON.stringify(plan,null,2)+'\n');console.log(JSON.stringify({filename,boxes:boxes.length,plants:plants.length,...plan.validation},null,2));
