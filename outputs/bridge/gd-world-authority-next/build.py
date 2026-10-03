import json, os, pathlib, subprocess, zipfile
mod = pathlib.Path(__file__).resolve().parent
root = mod.parents[2]
settings = root / '.local.json'
runtime = pathlib.Path(json.loads(settings.read_text(encoding='utf-8-sig'))['runtimeRoot']) if settings.exists() else root
work = runtime / 'work'
toolchain = work / 'msvc/VC/Tools/MSVC/14.44.35207'
sdk = pathlib.Path('C:/Program Files (x86)/Windows Kits/10')
version = '10.0.26100.0'
env = os.environ.copy()
env['PATH'] = ';'.join(map(str, [toolchain/'bin/Hostx64/x64', sdk/'bin'/version/'x64', work/'build-tools/bin'])) + ';' + env['PATH']
env['INCLUDE'] = ';'.join(map(str,[toolchain/'include',sdk/'Include'/version/'ucrt',sdk/'Include'/version/'shared',sdk/'Include'/version/'um',sdk/'Include'/version/'winrt']))
env['LIB'] = ';'.join(map(str,[toolchain/'lib/x64',sdk/'Lib'/version/'ucrt/x64',sdk/'Lib'/version/'um/x64']))
build = mod / 'build'
build.mkdir(exist_ok=True)
(build/'CMakeFiles/MinecraftGDBridge.dir/src').mkdir(parents=True, exist_ok=True)
# Reuse cached SDK bindings and libraries, but always compile this repository's
# source. Running the old CMake cache would silently compile its old workspace.
cached = runtime/'outputs/bridge'/mod.name/'build'
for name in ['manual-compile.rsp', 'manual-link.rsp']:
 source = cached/name
 if source.resolve() != (build/name).resolve():
  if not source.is_file(): raise RuntimeError(f'Missing cached SDK build response: {source}')
  (build/name).write_text(source.read_text(encoding='utf-8'), encoding='utf-8')
subprocess.run([str(toolchain/'bin/Hostx64/x64/cl.exe'),'@manual-compile.rsp'],cwd=build,env=env,check=True)
subprocess.run([str(toolchain/'bin/Hostx64/x64/link.exe'),'@manual-link.rsp'],cwd=build,env=env,check=True)
with zipfile.ZipFile(mod/'experiment.minecraft_bridge.geode','w',zipfile.ZIP_DEFLATED) as archive:
 archive.write(mod/'mod.json','mod.json')
 archive.write(mod/'build/experiment.minecraft_bridge.dll','experiment.minecraft_bridge.dll')
print(mod/'experiment.minecraft_bridge.geode')
