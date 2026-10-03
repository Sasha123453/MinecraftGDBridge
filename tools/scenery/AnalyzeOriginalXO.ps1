$ErrorActionPreference='Stop'
$rawLevel=Get-Content work/xo-original-raw-level.txt -Raw
$records=$rawLevel.Split(';')
$objects=[Collections.Generic.List[object]]::new()
for($recordIndex=1;$recordIndex -lt $records.Length;$recordIndex++){
 $parts=$records[$recordIndex].Split(',');if($parts.Length -lt 6){continue}
 $fields=@{};for($i=0;$i+1 -lt $parts.Length;$i+=2){$fields[$parts[$i]]=$parts[$i+1]}
 if(-not $fields.ContainsKey('1') -or -not $fields.ContainsKey('2') -or -not $fields.ContainsKey('3')){continue}
 $x=[double]::Parse($fields['2'],[Globalization.CultureInfo]::InvariantCulture);if($x -lt 0 -or $x -gt 6000){continue}
 $objects.Add([pscustomobject]@{recordIndex=$recordIndex;id=[int]$fields['1'];x=$x;y=[double]::Parse($fields['3'],[Globalization.CultureInfo]::InvariantCulture);rotation=$fields['6'];scale=$fields['32'];groups=$fields['57'];raw=$records[$recordIndex]})
}
$objects|ConvertTo-Json -Depth 5|Set-Content work/xo-first6000-analysis.json -Encoding utf8
$objects|Group-Object id|Sort-Object Count -Descending|Select-Object -First 28 Name,Count|ConvertTo-Json -Compress
$knownFeatures=@(12,13,47,111,660,36,35,84,141,101,99,10,11,1332,1333,392,15)
$objects|Where-Object {$_.id -in $knownFeatures}|Sort-Object x|Select-Object -First 90 recordIndex,id,x,y,rotation,scale|ConvertTo-Json -Compress
