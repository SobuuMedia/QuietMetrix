#!/usr/bin/env python3
"""Validate SDK version consistency and stage only publications supported by this host."""
import argparse, pathlib, platform, re, subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODULES = ('quietmetrix-sdk', 'quietmetrix-sdk-compose', 'quietmetrix-sdk-debug')

def version():
    versions = [re.search(r'^version = "([^"]+)"$', (ROOT/module/'build.gradle.kts').read_text(), re.M)[1] for module in MODULES]
    assert len(set(versions)) == 1, 'Core, Compose and debug versions must match'
    current = versions[0]
    assert re.fullmatch(r'\d+\.\d+\.\d+', current), 'A release requires a stable semantic version'
    runtime = (ROOT/'quietmetrix-sdk/src/commonMain/kotlin/com/quietmetrix/analytics/internal/ConfigHolder.kt').read_text()
    assert f'SDK_VERSION = "{current}"' in runtime, 'Runtime version differs from publication'
    assert (ROOT/f'docs/releases/sdk-{current}.md').is_file(), 'Release notes missing'
    return current

def publications(host):
    if host == 'macos':
        core = ['KotlinMultiplatform','Android','Jvm','Js','WasmJs','IosArm64','IosSimulatorArm64','IosX64','MacosArm64']
        wrappers = ['KotlinMultiplatform','Android','Jvm','WasmJs','IosArm64','IosSimulatorArm64']
        return {MODULES[0]: core, MODULES[1]: wrappers, MODULES[2]: wrappers}
    return {MODULES[0]: ['LinuxX64' if host == 'linux' else 'MingwX64']}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--tag')
    parser.add_argument('--local', action='store_true', help='Install only host-supported publications into Maven Local')
    args = parser.parse_args()
    current = version()
    if args.tag: assert args.tag in ('v'+current, 'sdk-v'+current), 'Tag and SDK versions differ'
    print('SDK release version:', current)
    if args.check: return
    host = {'Darwin':'macos','Linux':'linux','Windows':'windows'}[platform.system()]
    destination = 'MavenLocal' if args.local else 'ReleaseStagingRepository'
    tasks = [f':{module}:publish{publication}PublicationTo{destination}'
             for module, names in publications(host).items() for publication in names]
    wrapper = ROOT/('gradlew.bat' if host == 'windows' else 'gradlew')
    subprocess.run([str(wrapper), *tasks], cwd=ROOT, check=True)
    print('Prepared', host, 'publications for', destination)

if __name__ == '__main__': main()
