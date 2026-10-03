const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),source=JSON.parse(fs.readFileSync(path.join(root,'outputs/bridge/levels/selected-forest-layout.json'),'utf8'));
const boxes=[];let cells=0;
function add(from,to,block){boxes.push({from,to,block:'minecraft:'+block});cells+=(to[0]-from[0]+1)*(to[1]-from[1]+1)*(to[2]-from[2]+1);}
for(const original of source.boxes){const from=[...original.from],to=[...original.to];if(from[0]>372)continue;to[0]=Math.min(372,to[0]);const floor=from[2]>0?64:65;if(from[1]<=floor){add(from,[to[0],Math.min(floor,to[1]),to[2]],'stone');if(to[1]>floor)add([from[0],floor+1,from[2]],to,'air');}else add(from,to,'air');}
for(const p of source.plants)if(p.at[0]<=372)add([...p.at],[...p.at],'air');
if(cells>16384)throw Error('Cleanup batch too large');
const plan={schema:'gdbridge-scenery-plan-v1',name:'Remove owned selected-forest decoration before indoor chapters',targetWorld:'GDBridge-XO',boxes,plants:[],validation:{authoredCellsIncludingOverlaps:cells,maxBatchCells:16384,gameplayCellsTouched:0},applicationNotes:['Apply this cleanup BEFORE multi-location-layout.json, as a separate scenery batch.','Touches only exact box/plant footprints in selected-forest-layout.json for X0..372. Removes generated forest decoration from the cave, library and Nether chapters.','Restores stone floor at Y64 foreground/Y65 background and removes generated decoration above. Never touches z=0.','Leaves X384..512 forest chapter in place. Do not apply after constructing the indoor chapters.']};
const filename=path.join(root,'outputs/bridge/levels/cave-forest-cleanup-layout.json');fs.writeFileSync(filename,JSON.stringify(plan,null,2)+'\n');console.log(JSON.stringify({filename,boxes:boxes.length,cells},null,2));
