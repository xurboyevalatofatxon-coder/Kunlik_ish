#!/usr/bin/env python3
"""Static file/resource checks only. This script does NOT claim Android compilation."""
import json, pathlib, re, sys, xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]
checks=[]
def check(name,condition,detail=''):
    checks.append({'check':name,'pass':bool(condition),'detail':detail})
for p in (root/'app/src/main').rglob('*.xml'):
    try:ET.parse(p);check('XML: '+str(p.relative_to(root)),True)
    except ET.ParseError as e:check('XML: '+str(p.relative_to(root)),False,str(e))
localizations={}
for folder in ['values','values-ru','values-en']:
    p=root/'app/src/main/res'/folder/'strings.xml'
    strings=ET.parse(p).getroot().findall('string')
    names=[s.attrib['name'] for s in strings]
    check('No duplicate keys: '+folder,len(set(names))==len(names))
    check('No empty localized strings: '+folder,all(s.text for s in strings))
    localizations[folder]=set(names)
keys=localizations['values']
check('Complete Russian localization',localizations['values-ru']==keys)
check('Complete English localization',localizations['values-en']==keys)
code='\n'.join(p.read_text() for p in (root/'app/src').rglob('*.kt'))
refs=set(re.findall(r'R\.string\.([A-Za-z_][A-Za-z0-9_]*)',code))
check('Every R.string reference exists',refs<=keys,','.join(sorted(refs-keys)))
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot();ns='{http://schemas.android.com/apk/res/android}'
permissions=[p.get(ns+'name') for p in manifest.findall('uses-permission')]
check('No Internet permission','android.permission.INTERNET' not in permissions)
check('No exact alarm special permission',not any('EXACT_ALARM' in p for p in permissions))
check('Automatic platform backup disabled',manifest.find('application').get(ns+'allowBackup')=='false')
check('Binary results only','enum class ResultValue { DONE, NOT_DONE }' in (root/'core/src/main/kotlin/uz/dailygoals/domain/Models.kt').read_text())
check('All five navigation routes',all(f'Destination("{r}"' in code for r in ['home','goals','stats','archive','settings']))
check('No destructive Room fallback','fallbackToDestructiveMigration(' not in code)
check('No bundled signing keys',not list(root.rglob('*.jks')) and not list(root.rglob('*.keystore')))
check('Source requirement attached',(root/'docs/USER_REQUIREMENTS.txt').is_file())
report={'scope':'STATIC FILE AND RESOURCE CHECKS; not an Android build','status':'PASS' if all(c['pass'] for c in checks) else 'FAIL','checks':checks,
        'localized_keys_per_language':len(keys),'kotlin_files':len(list(root.rglob('*.kt')))}
(root/'reports').mkdir(exist_ok=True)
(root/'reports/source-check.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
sys.exit(0 if report['status']=='PASS' else 1)
