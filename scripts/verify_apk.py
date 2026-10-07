#!/usr/bin/env python3
"""Validate an ACTUAL APK with Android SDK tools; never substitutes source checks."""
import argparse, hashlib, json, os, pathlib, re, shutil, subprocess, sys, zipfile

def sdk_tool(name: str) -> str:
    found=shutil.which(name)
    if found: return found
    sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk: raise RuntimeError('ANDROID_HOME or ANDROID_SDK_ROOT is required')
    dirs=sorted((pathlib.Path(sdk)/'build-tools').glob('*'),reverse=True)
    for directory in dirs:
        candidate=directory/(name+('.bat' if os.name=='nt' and name=='apksigner' else '.exe' if os.name=='nt' else ''))
        if candidate.exists():return str(candidate)
    raise RuntimeError(f'Missing Android SDK tool: {name}')

def main() -> int:
    p=argparse.ArgumentParser();p.add_argument('apk');p.add_argument('--package',required=True);p.add_argument('--output',required=True);p.add_argument('--install',action='store_true')
    args=p.parse_args();apk=pathlib.Path(args.apk).resolve()
    result={'apk':str(apk),'status':'FAIL','install':'NOT_RUN'}
    try:
        if not apk.is_file() or apk.stat().st_size==0:raise RuntimeError('APK does not exist or is empty')
        with zipfile.ZipFile(apk) as z:
            if 'AndroidManifest.xml' not in z.namelist() or 'classes.dex' not in z.namelist():raise RuntimeError('Missing binary manifest or DEX')
            if z.testzip():raise RuntimeError('Corrupt APK ZIP entry')
        signature=subprocess.run([sdk_tool('apksigner'),'verify','--verbose','--print-certs',str(apk)],check=True,capture_output=True,text=True).stdout
        metadata=subprocess.run([sdk_tool('aapt'),'dump','badging',str(apk)],check=True,capture_output=True,text=True).stdout
        if f"package: name='{args.package}'" not in metadata:raise RuntimeError('Unexpected package identity')
        if "versionCode='1'" not in metadata:raise RuntimeError('Unexpected version code')
        version='1.0.0-debug' if args.package.endswith('.debug') else '1.0.0'
        if f"versionName='{version}'" not in metadata:raise RuntimeError('Unexpected version name')
        if "sdkVersion:'26'" not in metadata:raise RuntimeError('Unexpected minimum SDK')
        if "targetSdkVersion:'36'" not in metadata:raise RuntimeError('Unexpected target SDK')
        if 'launchable-activity:' not in metadata:raise RuntimeError('No launcher activity')
        result.update(status='PASS',size_bytes=apk.stat().st_size,sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),signature=signature,metadata=metadata)
        if args.install:
            adb=shutil.which('adb')
            if not adb:raise RuntimeError('adb is unavailable')
            installed=subprocess.run([adb,'install','-r',str(apk)],check=True,capture_output=True,text=True)
            if 'Success' not in installed.stdout:raise RuntimeError('adb did not confirm installation')
            launched=subprocess.run([adb,'shell','am','start','-W','-n',args.package+'/uz.dailygoals.MainActivity'],check=True,capture_output=True,text=True)
            if 'Status: ok' not in launched.stdout or 'Error' in launched.stdout:raise RuntimeError('Android did not confirm launch')
            result.update(install='PASS',launch_output=launched.stdout)
    except Exception as e:
        result.update(status='FAIL',error=str(e))
    out=pathlib.Path(args.output);out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result,indent=2));return 0 if result['status']=='PASS' else 1
if __name__=='__main__':sys.exit(main())
