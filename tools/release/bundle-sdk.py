#!/usr/bin/env python3
"""Validate all host publications and assemble a signed Central Portal deployment bundle."""
import argparse, hashlib, pathlib, xml.etree.ElementTree as ET, zipfile
import importlib.util

spec = importlib.util.spec_from_file_location('prepare_sdk', pathlib.Path(__file__).with_name('prepare-sdk.py'))
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)
NS = {'m':'http://maven.apache.org/POM/4.0.0'}

def expected():
    suffixes = ('android','jvm','js','wasm-js','iosarm64','iossimulatorarm64','iosx64','macosarm64','linuxx64','mingwx64')
    wrappers = ('android','jvm','wasm-js','iosarm64','iossimulatorarm64')
    return [module+suffix for module in prepare.MODULES
            for suffix in ('', *('-'+s for s in (suffixes if module == prepare.MODULES[0] else wrappers)))]

def bundle(repository, output, current, unsigned=False):
    entries = []
    for artifact in expected():
        folder = repository/'io/github/sobuumedia'/artifact/current
        stem = f'{artifact}-{current}'
        pom = folder/(stem+'.pom')
        assert pom.is_file(), f'Missing platform publication: {artifact}'
        metadata = ET.parse(pom).getroot()
        assert metadata.findtext('m:version', namespaces=NS) == current, f'Wrong POM version: {artifact}'
        assert metadata.findtext('m:groupId', namespaces=NS) == 'io.github.sobuumedia'
        assert metadata.findtext('m:artifactId', namespaces=NS) == artifact
        assert (folder/(stem+'.module')).is_file(), f'Missing Gradle variant metadata: {artifact}'
        assert (folder/(stem+'-sources.jar')).is_file(), f'Missing sources: {artifact}'
        assert (folder/(stem+'-javadoc.jar')).is_file(), f'Missing documentation: {artifact}'
        main = [folder/(stem+ext) for ext in ('.jar','.aar','.klib') if (folder/(stem+ext)).is_file()]
        assert main, f'Missing runtime: {artifact}'
        for runtime in main: assert zipfile.is_zipfile(runtime), f'Invalid runtime archive: {runtime.name}'
        files = [f for f in folder.iterdir() if f.suffix in ('.pom','.module','.jar','.aar','.klib')]
        for file in files:
            if not unsigned:
                signature = pathlib.Path(str(file)+'.asc')
                assert signature.is_file() and 'BEGIN PGP SIGNATURE' in signature.read_text(), f'Missing signature: {file.name}'
                entries.append(signature)
            entries.append(file)
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(entries):
            name = file.relative_to(repository).as_posix(); data = file.read_bytes()
            archive.writestr(name, data)
            for algorithm in ('md5','sha1','sha256','sha512'):
                archive.writestr(name+'.'+algorithm, hashlib.new(algorithm, data).hexdigest())
    print(f'Validated {len(expected())} platform components; bundle: {output}')

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repository', type=pathlib.Path, required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    parser.add_argument('--allow-unsigned', action='store_true', help='Local inspection only; unsuitable for Central')
    args = parser.parse_args()
    bundle(args.repository, args.output, prepare.version(), args.allow_unsigned)

if __name__ == '__main__': main()
