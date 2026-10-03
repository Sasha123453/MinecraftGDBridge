const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const base = JSON.parse(fs.readFileSync(path.join(root, 'outputs/bridge/levels/concept-layout.json'), 'utf8'));
const boxes = [], plants = [];
const segments = [];
const mat = name => `minecraft:${name}`;
function box(block, x0, y0, z0, x1=x0, y1=y0, z1=z0) {
  boxes.push({block:mat(block),from:[x0,y0,z0],to:[x1,y1,z1]});
}
function plant(block,x,y,z) { plants.push({block:mat(block),at:[x,y,z]}); }
function segment(name,start,end,run) {
  const before = boxes.length, beforePlants = plants.length;
  run();
  segments.push({name,rangeX:[start,end],boxRange:[before,boxes.length-1],plantRange:[beforePlants,plants.length-1]});
}
function tree(x,z,baseY,height=6) {
  box('oak_log',x,baseY,z,x,baseY+height,z);
  box('oak_leaves',x-2,baseY+height-1,z-2,x+2,baseY+height+1,z+2);
  box('oak_leaves',x-1,baseY+height+2,z-1,x+1,baseY+height+2,z+1);
}
segment('Forest approach',0,93,()=>{
  // Retain the established concept's small foreground islands and background ruins.
  for(const b of base.boxes) if(Math.max(b.from[0],b.to[0])<=93) boxes.push(b);
  for(const p of base.plants) if(p.at[0]<=93) plants.push(p);
  for(const x of [7,31,55,79]) tree(x,-13,66,6+(x%2));
  for(const x of [16,43,68]) {
    box('dirt',x,64,5,x+5,64,8);
    box('grass_block',x,65,5,x+5,65,8);
    plant('grass',x+1,66,6); plant('grass',x+3,66,7);
  }
  box('mossy_stone_bricks',88,66,-5,90,68,-5);
  box('stone_bricks',91,66,-5,93,71,-5);
});
segment('Stone cave',100,210,()=>{
  // A continuous opaque backdrop hides the old distant tree line, with a real roof
  // on both sides of the protected gameplay slice. Construction never spans z=0.
  box('stone',100,66,-6,210,79,-6);
  box('stone',100,80,-6,210,80,-1);
  box('stone',100,80,1,210,80,5);
  box('stone',100,65,-5,210,65,-1);
  box('stone',100,64,1,210,64,4);
  // Rough strata, pillars and smaller low foreground rock masses make the cave
  // read as a Minecraft volume when moving the camera around it.
  for(let x=100;x<=197;x+=13) {
    box('cobblestone',x,66,-5,x+3,69,-5);
    box('deepslate',x+4,73,-5,x+9,74,-5);
    box('stone',x+6,76,-4,x+7,79,-3);
    box('stone',x+6,74,-4,x+6,75,-4);
    box('cobblestone',x+2,64,7,x+5,66,9);
    box('stone',x+3,67,8,x+4,68,8);
  }
  for(const x of [100,209]) {
    box('stone',x,66,-5,x+1,79,-2);
    box('stone',x,65,5,x+1,74,7);
  }
  // Small dark mineral panels sit in front of the rock face, not painted UI.
  for(const x of [114,142,170,198]) {
    box('deepslate',x,69,-5,x+6,72,-5);
    box('stone',x+2,66,-3,x+3,68,-3);
    plant('torch',x+2,69,-3);
    box('cobblestone',x+1,65,3,x+2,65,3);
    plant('torch',x+1,66,3);
  }
});
segment('Library entrance',212,219,()=>{
  box('stone_bricks',212,66,-6,219,76,-6);
  box('oak_log',217,66,-5,217,80,-3);
  box('oak_planks',216,79,-5,219,80,-1);
  box('stone_bricks',215,65,3,217,67,5);
  plant('lantern',216,68,4);
});
segment('Oak library',220,350,()=>{
  box('oak_planks',220,66,-6,350,80,-6);
  box('oak_planks',220,65,-5,350,65,-1);
  box('oak_planks',220,64,1,350,64,4);
  box('oak_planks',220,81,-6,350,81,-1);
  box('oak_planks',220,81,1,350,81,4);
  // Stacked bookshelf bays, real log uprights and ceiling crossbeams.
  for(let x=222;x<=343;x+=11) {
    const end = Math.min(x+8,349);
    box('oak_log',x,66,-5,x,80,-5);
    box('bookshelf',x+1,67,-5,end,70,-5);
    box('oak_planks',x+1,71,-5,end,71,-5);
    box('bookshelf',x+1,72,-5,end,76,-5);
    box('oak_planks',x+1,77,-5,end,77,-5);
    box('oak_log',x,80,-4,x,80,-1);
    box('oak_log',x,80,1,x,80,4);
  }
  box('oak_log',350,66,-5,350,80,-5);
  // Six freestanding reading tables and lanterns provide actual block lighting.
  for(const x of [228,250,272,294,316,338]) {
    box('oak_log',x,66,-3,x,67,-3);
    box('oak_planks',x-1,68,-4,x+1,68,-2);
    plant('lantern',x,69,-3);
    box('oak_planks',x-1,78,-4,x+1,78,-3);
    plant('lantern',x,79,-4);
    box('bookshelf',x-2,65,5,x+2,66,6);
    box('oak_planks',x-2,67,5,x+2,67,6);
    plant('lantern',x,68,5);
  }
  // End piers give a visible architectural doorway without filling the lane.
  for(const x of [220,348]) {
    box('oak_log',x,65,5,x+1,78,6);
    box('stone_bricks',x,64,5,x+1,65,6);
  }
});
segment('Forest clearing',360,512,()=>{
  for(const x of [366,392,420,448,478,506]) tree(x,-13,66,7);
  for(const x of [368,405,444,480]) {
    box('dirt',x,64,4,x+7,64,7);
    box('grass_block',x,65,4,x+7,65,7);
    plant('grass',x+1,66,5); plant('grass',x+5,66,6);
    box('mossy_stone_bricks',x+10,66,-5,x+12,68,-5);
  }
  box('stone_bricks',358,66,-5,361,75,-5);
  box('oak_log',360,76,-5,362,79,-5);
});
let touched=0;
const occupied=new Map();
for(const b of boxes) {
  for(let i=0;i<3;i++) if(b.from[i]>b.to[i]) throw new Error('Reversed box');
  if(b.from[2]<=0&&b.to[2]>=0) throw new Error('Box touches gameplay');
  for(const p of [b.from,b.to]) validatePosition(p);
  touched+=(b.to[0]-b.from[0]+1)*(b.to[1]-b.from[1]+1)*(b.to[2]-b.from[2]+1);
  for(let x=b.from[0];x<=b.to[0];x++) for(let y=b.from[1];y<=b.to[1];y++) for(let z=b.from[2];z<=b.to[2];z++) occupied.set(`${x},${y},${z}`,b.block);
}
for(const p of plants) { validatePosition(p.at);touched++;occupied.set(p.at.join(','),p.block); }
function validatePosition([x,y,z]) {
  if(x<0||x>512||y<50||y>90||z<-16||z>24||z===0) throw new Error(`Out of bounds: ${x},${y},${z}`);
}
if(touched>16384) throw new Error(`applyScenery limit exceeded: ${touched}`);
const plan={
  schema:'gdbridge-scenery-plan-v1',name:'XO inspired forest, cave and oak library',
  state:'generated-ready-for-applyScenery',targetWorld:'GDBridge-XO',
  coordinateSystem:'Minecraft integer block coordinates; positive Z foreground; negative Z background',
  preserve:{gameplaySliceZ:0,nativePhysics:true,existingAuthoringMarkers:true},
  timeOfDay:3000,cleanupOwnedBackWalls:base.cleanupOwnedBackWalls,
  boxes,plants,locations:segments,
  validation:{authoredCellsIncludingOverlaps:touched,distinctAuthoredCells:occupied.size,maxBatchCells:16384,gameplayCellsTouched:0,materials:[...new Set([...boxes,...plants].map(o=>o.block))].sort()},
  applicationNotes:[
    'Apply through WorldEditor.applyScenery on the isolated integrated Minecraft server.',
    'Requires sceneryMaterial mappings for oak_log, oak_planks, oak_leaves, bookshelf, lantern, torch, cobblestone and deepslate.',
    'The continuous cave and library backdrops conceal generated distant oaks; no broad clearing of existing player builds is performed.',
    'Lanterns and torches are genuine Minecraft blocks on solid support blocks, with native light emission.',
    'This is a multi-location adaptation inspired by the requested video, not a verified reconstruction of its architecture.'
  ]
};
const output=path.join(root,'outputs/bridge/levels/multi-location-layout.json');
fs.writeFileSync(output,JSON.stringify(plan,null,2)+'\n');
console.log(JSON.stringify({output,boxes:boxes.length,plants:plants.length,...plan.validation},null,2));
