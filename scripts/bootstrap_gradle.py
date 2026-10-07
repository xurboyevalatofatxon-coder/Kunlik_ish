#!/usr/bin/env python3
"""Small explicit Gradle bootstrap, not the official Gradle Wrapper.
Downloads only the pinned official distribution and verifies its SHA-256 before use.
An installed Gradle is used only when its exact version matches the pinned version.
"""
import hashlib, os, pathlib, re, shutil, subprocess, sys, urllib.request, zipfile
VERSION='8.13'
ROOT=pathlib.Path(__file__).resolve().parents[1]

def main():
    installed=shutil.which('gradle')
    if installed:
        v=subprocess.run([installed,'--version'],capture_output=True,text=True)
        if re.search(r'(?m)^Gradle '+re.escape(VERSION)+r'$',v.stdout):
            return subprocess.call([installed,*sys.argv[1:]],cwd=ROOT)
    cache=ROOT/'.bootstrap';cache.mkdir(exist_ok=True)
    executable=cache/f'gradle-{VERSION}'/'bin'/('gradle.bat' if os.name=='nt' else 'gradle')
    if not executable.exists():
        url=f'https://downloads.gradle.org/distributions/gradle-{VERSION}-bin.zip'
        archive=cache/f'gradle-{VERSION}-bin.zip'
        print(f'Downloading official Gradle {VERSION}; this requires network access.',flush=True)
        try:
            with urllib.request.urlopen(url+'.sha256',timeout=20) as response:
                expected=response.read().decode('ascii').strip().split()[0]
            if not re.fullmatch('[0-9a-fA-F]{64}',expected):raise RuntimeError('Invalid official checksum response')
            with urllib.request.urlopen(url,timeout=60) as response,archive.open('wb') as output:
                shutil.copyfileobj(response,output)
            actual=hashlib.sha256(archive.read_bytes()).hexdigest()
            if actual.lower()!=expected.lower():raise RuntimeError('Gradle distribution checksum mismatch')
            with zipfile.ZipFile(archive) as z:
                for entry in z.infolist():
                    path=(cache/entry.filename).resolve()
                    if cache.resolve() not in path.parents:raise RuntimeError('Unsafe archive path')
                z.extractall(cache)
            if os.name!='nt':executable.chmod(0o755)
        except Exception as error:
            print('BUILD BLOCKED: '+str(error),file=sys.stderr);return 2
    return subprocess.call([str(executable),*sys.argv[1:]],cwd=ROOT)
if __name__=='__main__':sys.exit(main())
