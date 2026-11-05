"""Optional QA: run with openapi-spec-validator and openapi-schema-validator installed."""
from pathlib import Path
from decimal import Decimal
import json, re, sys, xml.etree.ElementTree as ET
import yaml
from openapi_spec_validator import validate
from openapi_schema_validator import OAS30Validator
from jsonschema import RefResolver

root = Path(__file__).resolve().parents[1]
spec = yaml.safe_load((root.parent/'openapi.yaml').read_text(encoding='utf-8'))
validate(spec)
spec = json.loads(json.dumps(spec), parse_float=Decimal)
resolver = RefResolver.from_schema(spec)
evidence = json.loads((root/'.local/http-evidence.json').read_text(encoding='utf-8-sig'))
covered = set()
for row in evidence:
    path = row['path'].split('?')[0]
    matches = [p for p in spec['paths'] if re.fullmatch(re.sub(r'\{[^}]+\}', '[^/]+', p), path)]
    assert len(matches) == 1, path
    operation = spec['paths'][matches[0]][row['method'].lower()]
    response = operation['responses'][str(row['status'])]
    if '$ref' in response: response = resolver.resolve(response['$ref'])[1]
    if row['status'] == 204:
        assert not row['body']
    else:
        data = json.loads(row['body'], parse_float=Decimal)
        schema = response['content']['application/json']['schema']
        OAS30Validator(schema, resolver=resolver, format_checker=OAS30Validator.FORMAT_CHECKER).validate(data)
    covered.add((row['method'].lower(), matches[0]))
expected = {(m,p) for p,item in spec['paths'].items() for m in item if m in {'get','post','put','delete'}}
assert covered == expected, expected-covered
suites = [ET.parse(p).getroot() for p in (root/'build/test-results/test').glob('TEST-*.xml')]
result = {'httpChecks':len(evidence),'coveredOperations':len(covered),'expectedOperations':len(expected),
          'tests':sum(int(s.get('tests','0')) for s in suites),
          'failures':sum(int(s.get('failures','0'))+int(s.get('errors','0')) for s in suites),
          'skipped':sum(int(s.get('skipped','0')) for s in suites)}
(root/'.local/validation-summary.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result))
