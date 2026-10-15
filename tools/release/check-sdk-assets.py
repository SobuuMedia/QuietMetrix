#!/usr/bin/env python3
"""Verify the web/iOS artifacts users will download, including version metadata."""
import argparse, hashlib, json, pathlib, plistlib, tarfile, zipfile
import importlib.util

spec = importlib.util.spec_from_file_location('prepare_sdk', pathlib.Path(__file__).with_name('prepare-sdk.py'))
prepare = importlib.util.module_from_spec(spec); spec.loader.exec_module(prepare)

def main():
    parser = argparse.ArgumentParser(); parser.add_argument('assets', type=pathlib.Path)
    assets = parser.parse_args().assets; current = prepare.version()
    expected = {f'QuietMetrix-{current}.xcframework.zip', f'sobuumedia-quietmetrix-sdk-{current}.tgz',
                'LICENSE','ExperimentElement.swift','RELEASE_NOTES.md'}
    for name in expected: assert (assets/name).is_file(), 'Missing release asset: '+name
    assert 'Permission is hereby granted' in (assets/'LICENSE').read_text(), 'MIT license text is missing'
    checksums = {}
    for line in (assets/'SHA256SUMS').read_text().splitlines():
        checksum, name = line.split('  ',1); checksums[name] = checksum
    assert set(checksums) == expected, 'Release checksum manifest is incomplete'
    for name, checksum in checksums.items():
        assert hashlib.sha256((assets/name).read_bytes()).hexdigest() == checksum, 'Checksum mismatch: '+name
    with tarfile.open(assets/f'sobuumedia-quietmetrix-sdk-{current}.tgz') as archive:
        package = json.load(archive.extractfile('package/package.json'))
        assert package['name'] == '@sobuumedia/quietmetrix-sdk' and package['version'] == current
        assert package['license'] == 'MIT'
        for name in [package['main'],package['types'],'README.md','LICENSE']:
            assert 'package/'+name in archive.getnames(), 'Missing npm asset: '+name
        assert archive.extractfile('package/LICENSE').read() == (assets/'LICENSE').read_bytes()
    with zipfile.ZipFile(assets/f'QuietMetrix-{current}.xcframework.zip') as archive:
        assert archive.testzip() is None, 'Corrupt XCFramework archive'
        info = plistlib.loads(archive.read('QuietMetrix.xcframework/Info.plist'))
        libraries = info['AvailableLibraries']
        assert any(lib['SupportedArchitectures'] == ['arm64'] and lib.get('SupportedPlatformVariant') != 'simulator' for lib in libraries)
        assert any(set(lib['SupportedArchitectures']) == {'arm64','x86_64'} and lib.get('SupportedPlatformVariant') == 'simulator' for lib in libraries)
        plists = [name for name in archive.namelist() if name.endswith('/QuietMetrix.framework/Info.plist')]
        assert len(plists) == 2, 'Missing iOS framework slices'
        for name in plists:
            metadata = plistlib.loads(archive.read(name))
            assert metadata['CFBundleShortVersionString'] == current and metadata['CFBundleVersion'] == current
    print('Release assets verified: npm runtime/types/license, iOS versions/architectures, SwiftUI source and SHA-256 manifest.')

if __name__ == '__main__': main()
