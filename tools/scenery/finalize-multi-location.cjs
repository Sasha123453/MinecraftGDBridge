const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),filename=path.join(root,'outputs/bridge/levels/multi-location-layout.json');
const original=JSON.parse(fs.readFileSync(filename,'utf8'));
const boxes=[],plants=[],locations=[];
function box(block,x,y,z,xx=x,yy=y,zz=z){boxes.push({block:'minecraft:'+block,from:[x,y,z],to:[xx,yy,zz]});}
function plant(block,x,y,z){plants.push({block:'minecraft:'+block,at:[x,y,z]});}
function copy(name,offset){const section=original.locations.find(s=>s.name===name);for(let i=section.boxRange[0];i<=section.boxRange[1];i++){const b=structuredClone(original.boxes[i]);if(b.from[2]>0&&b.from[1]===b.to[1]&&(b.from[1]>=80||b.from[1]===64))continue;b.from[0]+=offset;b.to[0]+=offset;if(b.from[0]>512||b.to[0]<0)continue;b.from[0]=Math.max(0,b.from[0]);b.to[0]=Math.min(512,b.to[0]);boxes.push(b);}for(let i=section.plantRange[0];i<=section.plantRange[1];i++){const p=structuredClone(original.plants[i]);p.at[0]+=offset;if(p.at[0]>=0&&p.at[0]<=512)plants.push(p);}}
function chapter(name,range,run){const b=boxes.length,p=plants.length;run();locations.push({name,rangeX:range,boxRange:[b,boxes.length-1],plantRange:[p,plants.length-1]});}
function tank(x,width=5){
  // Every source is enclosed before the fluid is placed. Glass reveals actual lava.
  box('obsidian',x-1,69,-7,x+width,75,-7);
  box('obsidian',x-1,69,-6,x+width,69,-5);
  box('obsidian',x-1,75,-6,x+width,75,-5);
  box('obsidian',x-1,70,-6,x-1,74,-5);
  box('obsidian',x+width,70,-6,x+width,74,-5);
  box('glass',x,70,-5,x+width-1,74,-5);
  box('lava',x,70,-6,x+width-1,74,-6);
}
chapter('Ore cave',[0,120],()=>{
  copy('Stone cave',-100);
  box('stone',111,66,-6,120,79,-6);box('stone',111,80,-6,120,80,-1);
  box('stone',111,65,-5,120,65,-1);
  for(const [x,ore] of [[8,'coal_ore'],[22,'diamond_ore'],[40,'iron_ore'],[60,'redstone_ore'],[81,'diamond_ore'],[102,'coal_ore']]){
    box(ore,x,70,-5,x+2,71,-5);box(ore,x+1,72,-5);
  }
  for(const x of [15,48,89]){
    box('deepslate',x,69,-8,x+6,74,-8);
    box('stone',x-1,69,-7,x-1,74,-6);box('stone',x+7,69,-7,x+7,74,-6);
    box('stone',x,69,-7,x+6,69,-6);box('stone',x,74,-7,x+6,74,-6);
    box('air',x,70,-6,x+6,73,-6);
  }
  for(const x of [4,34,72,106]){box('bedrock',x,66,-5,x+6,67,-5);box('bedrock',x+2,68,-5,x+4,68,-5);}
  box('granite',91,75,-5,104,77,-5);box('diorite',106,69,-5,119,72,-5);
  // The later cave transitions to the lava/granite palette visible in the video.
  tank(111,4);
});
chapter('Library entrance',[124,131],()=>copy('Library entrance',-88));
chapter('Oak library',[132,262],()=>copy('Oak library',-88));
chapter('Nether fortress',[274,372],()=>{
  box('netherrack',274,66,-6,372,78,-6);
  box('netherrack',274,79,-6,372,79,-1);
  box('nether_bricks',274,65,-5,372,65,-1);
  box('nether_bricks',274,66,-5,372,68,-5);box('nether_bricks',274,78,-5,372,78,-5);
  for(let x=274;x<=362;x+=11){box('nether_bricks',x,69,-5,x+2,77,-4);box('nether_bricks',x,64,6,x+2,69,7);box('netherrack',x+4,65,9,x+7,67,10);}
  for(const x of [280,302,324,346])tank(x,5);
  box('nether_bricks',371,66,-5,372,77,-3);
});
chapter('Forest hills and lake',[384,512],()=>{
  copy('Forest clearing',24);
  for(const x of [398]){
    box('dirt',x,66,-6,x+12,67,-3);box('grass_block',x,68,-6,x+12,68,-3);
    box('dirt',x+3,69,-6,x+9,69,-4);box('grass_block',x+3,70,-6,x+9,70,-4);
  }
  for(const x of [410,454,490]){
    box('birch_log',x,66,-10,x,72,-10);box('birch_leaves',x-2,71,-12,x+2,73,-8);box('birch_leaves',x-1,74,-11,x+1,74,-9);
  }
  box('stone',430,63,4,448,63,11);
  box('grass_block',430,64,4,448,65,4);box('grass_block',430,64,11,448,65,11);
  box('grass_block',430,64,5,430,65,10);box('grass_block',448,64,5,448,65,10);
  box('water',431,64,5,447,64,10);
});
let cells=0;const unique=new Map();function pos([x,y,z]){if(x<0||x>512||y<50||y>90||z<-16||z>24||z===0)throw Error('Unsafe position');}
for(const b of boxes){pos(b.from);pos(b.to);if(b.from[2]<=0&&b.to[2]>=0)throw Error('Gameplay crossing');cells+=(b.to[0]-b.from[0]+1)*(b.to[1]-b.from[1]+1)*(b.to[2]-b.from[2]+1);for(let x=b.from[0];x<=b.to[0];x++)for(let y=b.from[1];y<=b.to[1];y++)for(let z=b.from[2];z<=b.to[2];z++)unique.set(`${x},${y},${z}`,b.block);}
for(const p of plants){pos(p.at);cells++;unique.set(p.at.join(','),p.block);}if(cells>16384)throw Error('Batch too large '+cells);
const output={...original,name:'Video observed cave, library, Nether fortress and forest adaptation',boxes,plants,locations,validation:{authoredCellsIncludingOverlaps:cells,distinctAuthoredCells:unique.size,maxBatchCells:16384,gameplayCellsTouched:0,materials:[...new Set([...boxes,...plants].map(b=>b.block))].sort()},applicationNotes:[
  'Chapter order corrected from root-observed video frames: cave, library, Nether fortress, natural forest.',
  'Chapter widths are a compact 512-block adaptation, not a claimed exact timestamp-to-coordinate reconstruction.',
  'All lava sources are enclosed by obsidian and glass; the forest water basin has a solid floor and raised walls.',
  'Native GD gameplay geometry and z=0 remain unchanged; apply only through isolated GDBridge-XO integrated server.',
  'Cave includes actual diamond, coal, iron, redstone ores, bedrock veins, granite, diorite, torches and deep recesses.',
  'Library retains actual bookshelves and lanterns; Nether uses actual nether bricks/netherrack/lava; forest adds birch and a contained lake.'
]};fs.writeFileSync(filename,JSON.stringify(output,null,2)+'\n');console.log(JSON.stringify({filename,boxes:boxes.length,plants:plants.length,...output.validation},null,2));
