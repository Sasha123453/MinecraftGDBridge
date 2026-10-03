$ErrorActionPreference='Stop'
$boxes=[Collections.Generic.List[object]]::new()
$plants=[Collections.Generic.List[object]]::new()
function AddBox($from,$to,$block){$boxes.Add([ordered]@{from=$from;to=$to;block="minecraft:$block"})}
# Exact old authored roof footprint only. Apply after the original multi-location plan.
AddBox @(0,80,-6) @(120,80,-1) 'air'
# Raise the rear wall and give the ceiling real depth on both sides of the native lane.
AddBox @(0,80,-8) @(120,88,-6) 'stone'
AddBox @(0,89,-10) @(120,90,-1) 'stone'
AddBox @(0,89,1) @(120,90,12) 'stone'
AddBox @(0,86,12) @(120,88,12) 'deepslate'
foreach($span in @(@(0,1),@(119,120))){
    AddBox @($span[0],80,-10) @($span[1],88,-1) 'stone'
    AddBox @($span[0],80,1) @($span[1],88,12) 'stone'
}
# Irregular stone overhangs stay behind the gameplay plane, with bright ore patches.
for($x=7;$x -le 110;$x+=17){
    AddBox @($x,86,-5) @(($x+4),88,-4) 'deepslate'
    AddBox @(($x+1),83,-5) @(($x+3),84,-5) 'granite'
    AddBox @(($x+2),84,-4) @(($x+3),84,-4) 'iron_ore'
}
foreach($x in @(18,55,96)){AddBox @($x,81,-5) @(($x+2),82,-5) 'diamond_ore'}
for($x=6;$x -le 114;$x+=12){
    AddBox @($x,82,-5) @(($x+2),82,-5) 'cobblestone'
    $plants.Add([ordered]@{at=@(($x+1),83,-5);block='minecraft:torch'})
}
$floor=@([ordered]@{from=@(0,66,-1);to=@(120,66,1);block='minecraft:deepslate'})
$cells=0L
foreach($box in @($boxes.ToArray())+$floor){
    $cells+=(1+[Math]::Abs($box.to[0]-$box.from[0]))*(1+[Math]::Abs($box.to[1]-$box.from[1]))*(1+[Math]::Abs($box.to[2]-$box.from[2]))
    if($box -notin $floor -and [Math]::Min($box.from[2],$box.to[2]) -le 0 -and [Math]::Max($box.from[2],$box.to[2]) -ge 0){throw 'Normal scenery touches gameplay plane'}
}
$cells+=$plants.Count
if($cells -gt 16384){throw "Too many authored cells: $cells"}
$plan=[ordered]@{v=1;name='Cave-height expansion for the first 120 blocks';notes='Apply after multi-location-layout.json. Clears only its authored Y80 rear roof; new roof Y89..90. Native gameplay markers remain above terrainFloor Y66.';timeOfDay=3000;boxes=$boxes.ToArray();plants=$plants.ToArray();terrainFloor=$floor;authoredCells=$cells}
$output=Join-Path (Split-Path $PSScriptRoot -Parent) 'outputs/bridge/levels/cave-height-expansion-layout.json'
[IO.File]::WriteAllText($output,($plan | ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
[pscustomobject]@{path=$output;boxes=$boxes.Count;plants=$plants.Count;authoredCells=$cells}
