import os, pathlib, subprocess, zipfile
mod = pathlib.Path(__file__).resolve().parent
root = mod.parents[2]
work = root / 'work'
toolchain = work / 'msvc/VC/Tools/MSVC/14.44.35207'
sdk = pathlib.Path('C:/Program Files (x86)/Windows Kits/10')
version = '10.0.26100.0'
env = os.environ.copy()
env['PATH'] = ';'.join(map(str, [toolchain/'bin/Hostx64/x64', sdk/'bin'/version/'x64', work/'build-tools/bin'])) + ';' + env['PATH']
env['INCLUDE'] = ';'.join(map(str,[toolchain/'include',sdk/'Include'/version/'ucrt',sdk/'Include'/version/'shared',sdk/'Include'/version/'um',sdk/'Include'/version/'winrt']))
env['LIB'] = ';'.join(map(str,[toolchain/'lib/x64',sdk/'Lib'/version/'ucrt/x64',sdk/'Lib'/version/'um/x64']))
cmake = work/'build-tools/cmake/data/bin/cmake.exe'
try:
 subprocess.run([str(cmake),'--build',str(mod/'build'),'--parallel','8'],env=env,check=True)
except (OSError, subprocess.CalledProcessError):
 # Existing CMake-generated response files also permit direct native rebuilds.
 subprocess.run([str(toolchain/'bin/Hostx64/x64/cl.exe'),'@manual-compile.rsp'],cwd=mod/'build',env=env,check=True)
 subprocess.run([str(toolchain/'bin/Hostx64/x64/link.exe'),'@manual-link.rsp'],cwd=mod/'build',env=env,check=True)
with zipfile.ZipFile(mod/'experiment.minecraft_bridge.geode','w',zipfile.ZIP_DEFLATED) as archive:
 archive.write(mod/'mod.json','mod.json')
 archive.write(mod/'build/experiment.minecraft_bridge.dll','experiment.minecraft_bridge.dll')
print(mod/'experiment.minecraft_bridge.geode')
