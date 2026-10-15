"""Local release-gate regressions; fixture archives do not represent platform builds."""
import hashlib, importlib.util, pathlib, tempfile, unittest, zipfile

spec = importlib.util.spec_from_file_location('bundle_sdk', pathlib.Path(__file__).with_name('bundle-sdk.py'))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)

class BundleGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name); self.version = '0.7.0'
        for artifact in release.expected():
            folder = self.root/'io/github/sobuumedia'/artifact/self.version
            folder.mkdir(parents=True); stem = artifact+'-'+self.version
            (folder/(stem+'.pom')).write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>io.github.sobuumedia</groupId><artifactId>'+artifact+'</artifactId><version>'+self.version+'</version></project>')
            (folder/(stem+'.module')).write_text('{}')
            for suffix in ('.jar','-sources.jar','-javadoc.jar'):
                with zipfile.ZipFile(folder/(stem+suffix),'w') as archive: archive.writestr('fixture','fixture')
            for file in list(folder.iterdir()): pathlib.Path(str(file)+'.asc').write_text('-----BEGIN PGP SIGNATURE-----\nfixture')
        self.output = self.root/'bundle.zip'

    def test_complete_bundle_preserves_repository_layout_and_checksums(self):
        release.bundle(self.root,self.output,self.version)
        with zipfile.ZipFile(self.output) as archive:
            poms = [name for name in archive.namelist() if name.endswith('.pom')]
            self.assertEqual(23,len(poms))
            for name in poms:
                self.assertEqual(hashlib.sha256(archive.read(name)).hexdigest(),archive.read(name+'.sha256').decode())

    def test_missing_windows_component_blocks_release(self):
        path = self.root/'io/github/sobuumedia/quietmetrix-sdk-mingwx64/0.7.0/quietmetrix-sdk-mingwx64-0.7.0.pom'
        path.unlink()
        with self.assertRaisesRegex(AssertionError,'Missing platform publication'): release.bundle(self.root,self.output,self.version)
        self.assertFalse(self.output.exists())

    def test_unsigned_component_blocks_release(self):
        next(self.root.rglob('*.pom.asc')).unlink()
        with self.assertRaisesRegex(AssertionError,'Missing signature'): release.bundle(self.root,self.output,self.version)

    def test_mismatched_component_version_blocks_release(self):
        file = next(self.root.rglob('*.pom')); file.write_text(file.read_text().replace('<version>0.7.0','<version>0.6.0'))
        with self.assertRaisesRegex(AssertionError,'Wrong POM version'): release.bundle(self.root,self.output,self.version)

if __name__ == '__main__': unittest.main()
