import sys, pathlib, json, shutil, hashlib
source=pathlib.Path(sys.argv[1]).resolve()
repo=pathlib.Path('C:/MinecraftGDBridge')
stage=sys.argv[2] if len(sys.argv)>2 else 'gd-transition-next'
assert (repo/'.git').is_dir(), 'Initialize repository before sync'
manifest=[]
def copy(src,rel):
 target=repo/rel
 target.parent.mkdir(parents=True,exist_ok=True)
 shutil.copy2(src,target)
 manifest.append({'path':str(rel).replace('\\','/'),'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'bytes':target.stat().st_size})
for branch in ['minecraft',stage]:
 folder=source/'outputs/bridge'/branch
 for src in folder.rglob('*'):
  if not src.is_file(): continue
  parts=src.relative_to(folder).parts
  if any(p in ['build','dist','.gradle','proof-of-concept-backup'] for p in parts): continue
  if src.suffix.lower() not in ['.cpp','.hpp','.h','.java','.json','.gradle','.properties','.py','.md','.txt','.accesswidener']: continue
  if src.name in ['staged-manifest.json','roundtrip-loss-audit.json','baseline-y-validation.json']:continue
  copy(src,src.relative_to(source))
for src in (source/'outputs').iterdir():
 if src.is_file() and src.suffix.lower() in ['.ps1','.cjs','.mjs','.txt']:
  copy(src,src.relative_to(source))
for src in (source/'work').glob('*'):
 if src.is_file() and ('generate-' in src.name or src.name in ['finalize-multi-location.cjs','AnalyzeOriginalXO.ps1','parse-xo-easy-candidates.py']):
  copy(src,pathlib.Path('tools/scenery')/src.name)
for src in (source/'outputs/scenery').glob('*.json'):
 copy(src,src.relative_to(source))
for src in (source/'outputs/shaders').rglob('*.txt'):
 copy(src,src.relative_to(source))
copy(source/'outputs/Scene-Target.json',pathlib.Path('outputs/Scene-Target.json'))
ignore='''# Generated runtime, third-party downloads and local configuration
work/
.local.json
**/.gradle/
**/build/
**/dist/
**/proof-of-concept-backup/
outputs/launcher/bin/
outputs/launcher/data/
outputs/backups/
outputs/bridge/runtime/
outputs/bridge/recordings/
outputs/bridge/levels/
outputs/music/
*.jar
*.geode
*.dll
*.lib
*.exp
*.obj
*.pdb
*.exe
*.zip
*.7z
*.png
!outputs/Scene-Target-Selected.png
*.mp4
*.log
*.lnk
'''
(repo/'.gitignore').write_text(ignore,encoding='utf8')
(repo/'.local.json').write_text(json.dumps({'runtimeRoot':str(source)},indent=2)+'\n',encoding='utf8')
(repo/'source-sync.json').write_text(json.dumps({'sourceStage':stage,'files':manifest},indent=2)+'\n',encoding='utf8')
(repo/'Control.ps1').write_text('''param([Parameter(Mandatory=$true)][string]$Action,[string]$PayloadJson='{}')
$ErrorActionPreference='Stop'
$localSettings=Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot '.local.json') | ConvertFrom-Json
& (Join-Path $localSettings.runtimeRoot 'outputs/Control-Bridge.ps1') -Action $Action -PayloadJson $PayloadJson
''',encoding='utf8')
(repo/'README.md').write_text('''# Minecraft × Geometry Dash

Minecraft Java 1.20.1 supplies the world and camera. Geometry Dash 2.2081 / Geode supplies real physics, triggers and progress. Both run concurrently through localhost TCP18471. Default hybrid mode uses Minecraft platforms/spikes and original GD character, trails, portals, orbs and pads. Volumetric special objects remain optional.

## Sources

- outputs/bridge/minecraft: Fabric source/Gradle project.
- outputs/bridge/gd-transition-next: current Geode source.
- outputs: control, import, install, recording and music scripts.
- tools/scenery: authored decoration generators.
- outputs/scenery: decoration plans.
- outputs/shaders: preset text; shader packs downloaded separately.

## This installation

Git project: C:\MinecraftGDBridge. Runtime/toolchains currently remain in the original workspace to keep the existing installation working. The ignored .local.json records its path. Control.ps1 routes background commands to that runtime. GitHub remote: https://github.com/Sasha123453/MinecraftGDBridge.

Build inputs still expect the existing workspace/toolchains. Source migration is complete; portable build/setup consolidation is pending. Do not run copied installation scripts until runtime paths are configured. No game binaries, saves/worlds, downloaded levels, recordings or proprietary texture assets are committed. source-sync.json records copied files and hashes.

## Current limits

Editable markers cover Minecraft X0..512 / Y67..100 / Z0; GD continues beyond this region. Manual QA uses Easy XO58898913; Auto XO58835426 is for visual QA. First20seconds ofAutoXO have passed without inputs/noclip; fullcompletion remains unverified. The selected sunnyforest concept is an art target, not a current screenshot. Native particles/effects and visual quality are incomplete. GD042 addresses native level transition lifetime and signed-scale export; runtime validation determines actual stability.
''',encoding='utf8')
print(json.dumps({'repository':str(repo),'sourceFiles':len(manifest),'sourceBytes':sum(x['bytes'] for x in manifest)},ensure_ascii=False))
