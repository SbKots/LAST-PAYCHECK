"""Read-only source checks. Never print matched credentials or connect to cloud."""
import hashlib,json,re,subprocess,sys
from pathlib import Path
from zipfile import ZipFile
import yaml
from openapi_spec_validator import validate_spec

SB=Path(__file__).resolve().parents[1]
ROOT=SB.parents[1]
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT)
spec=yaml.safe_load((ROOT/'openapi.yaml').read_text(encoding='utf-8'))
validate_spec(spec)
mapping=json.loads((ROOT/'docs/supabase/operation-map.json').read_text(encoding='utf-8'))
assert len(mapping)==24 and len({r['id'] for r in mapping})==24
assert all(r['newPath'] in spec['paths'] for r in mapping)
assert spec['paths']['/rest/v1/rpc/delete_skill']['post']['responses'].get('200')
assert '/rest/v1/rpc/provider_cache' not in spec['paths']
files=sorted(set(git('ls-files','--cached','--others','--exclude-standard','-z').decode().split('\0'))-{''})
# Word's temporary owner files are not DOCX ZIP packages or submission artifacts.
files=[p for p in files if (ROOT/p).is_file() and not (Path(p).name.startswith('~$') and Path(p).suffix=='.docx')]
patterns=[rb'sb_secret_[A-Za-z0-9_-]{12,}',rb'sbp_[A-Za-z0-9]{20,}',rb'gh[pousr]_[A-Za-z0-9]{24,}',rb'github_pat_[A-Za-z0-9_]{25,}',rb'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{20,}']
bad=[]
for name in files:
    p=ROOT/name
    if p.suffix=='.docx':
        with ZipFile(p) as z:data=b'\n'.join(z.read(n) for n in z.namelist() if n.endswith('.xml'))
    else:data=p.read_bytes()
    if any(re.search(pattern,data) for pattern in patterns):bad.append(name)
    if re.search(r'(^|/)\.env($|\.)',name) and not name.endswith('.env.example'):bad.append(name)
assert not bad,'Potential secrets in paths (values suppressed): '+', '.join(sorted(set(bad)))
# Inspect reachable Git blobs once, without printing values or changing history.
objects=git('rev-list','--objects','--all').decode().splitlines()
history_blobs=0
for line in objects:
    oid=line.split(' ',1)[0]
    if git('cat-file','-t',oid).strip()!=b'blob':continue
    history_blobs+=1
    data=git('cat-file','blob',oid)
    assert not any(re.search(pattern,data) for pattern in patterns),'Potential secret in Git history; values suppressed'
assert len(list((SB/'migrations').glob('*.sql')))==4
locked=json.loads((SB/'contracts/migration-checksums.json').read_text())
assert all(hashlib.sha256((SB/'migrations'/name).read_bytes()).hexdigest()==digest for name,digest in locked.items()),'Applied migration changed'
assert not (ROOT/'server/build.gradle.kts').exists(), 'Legacy Ktor build found'
assert not (ROOT/'supabase/config.toml').exists(), 'Duplicate Supabase project at repository root'
print(json.dumps(dict(openapi='PASS',operationCoverage='24/24',migrationChecksums='4/4',sourceFiles=len(files),historyBlobs=history_blobs,secretPatterns='PASS; heuristic scan, not a proof for arbitrary password strings'),ensure_ascii=False))
