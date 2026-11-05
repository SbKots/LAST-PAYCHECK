"""Check every successful operation against OpenAPI on an isolated local Netty instance."""
import argparse
from decimal import Decimal
import json
import os
from pathlib import Path
import re
from http.client import HTTPConnection
from urllib.parse import urlencode, urlsplit
from uuid import uuid4

from openapi_schema_validator import OAS30Validator
from openapi_spec_validator import validate
import yaml


SERVER = Path(__file__).resolve().parents[1]


def local_token():
    token = os.environ.get("ADMIN_TOKEN")
    env = SERVER / ".env"
    if not token and env.is_file():
        for line in env.read_text(encoding="utf-8-sig").splitlines():
            key, _, value = line.partition("=")
            if key.strip() == "ADMIN_TOKEN":
                token = value.strip()
    if not token:
        raise RuntimeError("Set ADMIN_TOKEN for the test instance")
    return token


def verify(base_url):
    url = urlsplit(base_url)
    assert url.scheme == "http" and url.hostname in {"127.0.0.1", "localhost", "::1"}
    assert url.path == "/api/v1" and not (url.query or url.fragment or url.username)
    spec = yaml.safe_load((SERVER.parent / "openapi.yaml").read_text(encoding="utf-8"))
    validate(spec)
    spec = json.loads(json.dumps(spec), parse_float=Decimal)
    assert any(urlsplit(item["url"]).path == url.path for item in spec["servers"])
    bearer = spec["components"]["securitySchemes"]["AdminBearer"]
    assert (bearer["type"], bearer["scheme"]) == ("http", "bearer")
    token = local_token()
    covered = set()
    count = 0

    def expand(value):
        if isinstance(value, dict) and "$ref" in value:
            node = spec
            for segment in value["$ref"].removeprefix("#/").split("/"):
                node = node[segment.replace("~1", "/").replace("~0", "~")]
            return expand(node)
        if isinstance(value, dict):
            return {key: expand(item) for key, item in value.items()}
        if isinstance(value, list):
            return [expand(item) for item in value]
        return value

    def check(schema, value):
        OAS30Validator(expand(schema), format_checker=OAS30Validator.FORMAT_CHECKER).validate(value)

    def exchange(method, path, status, *, params=None, query=None, body=None, etag=None):
        nonlocal count
        path_params, query = params or {}, query or {}
        path_spec = spec["paths"][path]
        operation = path_spec[method.lower()]
        admin = path.startswith("/admin/")
        assert operation.get("security", spec.get("security", [])) == ([{"AdminBearer": []}] if admin else [])
        supplied = {"path": path_params, "query": query, "header": {"If-Match": etag} if etag else {}}
        declared = {"path": set(), "query": set(), "header": set()}
        parameters = path_spec.get("parameters", []) + operation.get("parameters", [])
        for definition in map(expand, parameters):
            name, location = definition["name"], definition["in"]
            declared[location].add(name)
            if name in supplied[location]:
                check(definition["schema"], supplied[location][name])
            else:
                assert not definition.get("required"), (method, path, name)
        assert all(values.keys() <= declared[key] for key, values in supplied.items())
        request_body = expand(operation.get("requestBody", {}))
        if body is not None:
            schema = request_body["content"]["application/json"]["schema"]
            if status == 422:
                assert not OAS30Validator(schema, format_checker=OAS30Validator.FORMAT_CHECKER).is_valid(body)
            else:
                check(schema, body)
        else:
            assert not request_body.get("required")
        headers = {"Accept": "application/json"}
        if admin:
            headers["Authorization"] = f"Bearer {token}"
        if etag:
            headers["If-Match"] = etag
        if body is not None:
            headers["Content-Type"] = "application/json"
        target = url.path + path.format(**path_params)
        if query:
            target += "?" + urlencode(query)
        connection = HTTPConnection(url.hostname, url.port, timeout=10)
        try:
            connection.request(method, target, body=None if body is None else json.dumps(body).encode(), headers=headers)
            response = connection.getresponse()
            assert response.status == status, (method, path, status, response.status)
            definition = expand(operation["responses"][str(status)])
            for name, header in definition.get("headers", {}).items():
                actual = response.headers.get(name)
                assert actual is not None, (method, path, name)
                schema = header["schema"]
                check(schema, int(actual) if schema["type"] == "integer" else actual)
                if name == "ETag":
                    assert re.fullmatch(r'"rev-[1-9][0-9]*"', actual)
                if name == "Location":
                    assert actual.startswith("/api/v1/admin/catalogs/")
            raw = response.read()
            if status == 204:
                assert raw == b""
                result = None
            else:
                assert response.headers.get_content_type() == "application/json"
                result = json.loads(raw, parse_float=Decimal)
                check(definition["content"]["application/json"]["schema"], result)
            if status < 300:
                covered.add((path, method.lower()))
            count += 1
            return result, response.headers.get("ETag")
        finally:
            connection.close()

    current, _ = exchange("GET", "/catalog", 200)
    base = current["catalogVersion"]
    for path in ("/occupations", "/skills", "/jobs", "/inflation", "/cars/brands", "/cars/models", "/tracks", "/race-configurations"):
        filters = {"/inflation": dict(country="RUS", yearFrom=2020, yearTo=2020),
                   "/cars/models": dict(brandId="demo"),
                   "/race-configurations": dict(trackId="night-delivery")}
        exchange("GET", path, 200, query=dict(catalogVersion=base, page=1, pageSize=2, **filters.get(path, {})))
    for path, identifier in (("/occupations/{id}", "warehouse"), ("/cars/models/{id}", "city-hatch"), ("/tracks/{id}", "night-delivery")):
        exchange("GET", path, 200, params={"id": identifier}, query={"catalogVersion": base})

    version = "p10-" + uuid4().hex[:16]
    parent = {"version": version}
    detail = dict(parent, id="p10-skill")
    skills = "/admin/catalogs/{version}/skills"
    _, rev = exchange("POST", "/admin/catalogs", 201, body=dict(version=version, baseVersion=base))
    exchange("GET", skills, 200, params=parent, query=dict(page=1, pageSize=2))
    skill = dict(id=detail["id"], code=detail["id"], title="Practice 10", description="", source="DEMO", externalId=None)
    # OpenAPI, Ktor and SQL must all reject whitespace-only required strings.
    for invalid in (dict(skill, title="   "), dict(skill, source="ONET", externalId="   ")):
        error, _ = exchange("POST", skills, 422, params=parent, body=invalid, etag=rev)
        assert error["error"]["code"] == "VALIDATIONERROR"
    _, rev = exchange("POST", skills, 201, params=parent, body=skill, etag=rev)
    exchange("GET", skills + "/{id}", 200, params=detail)
    update = dict(skill, title="Updated Practice 10")
    del update["id"]
    error, _ = exchange("PUT", skills + "/{id}", 422, params=detail, body=dict(update, title="   "), etag=rev)
    assert error["error"]["code"] == "VALIDATIONERROR"
    _, rev = exchange("PUT", skills + "/{id}", 200, params=detail, body=update, etag=rev)
    _, rev = exchange("DELETE", skills + "/{id}", 204, params=detail, etag=rev)
    published, _ = exchange("POST", "/admin/catalogs/{version}/publish", 200, params=parent, etag=rev)
    assert published["catalogVersion"] == version
    expected = {(path, method) for path, item in spec["paths"].items() for method in item
                if method in {"get", "post", "put", "delete", "patch", "head", "options", "trace"}}
    assert covered == expected and len(covered) == 19, (expected - covered, covered - expected)
    print(json.dumps({"successfulOperations": len(covered), "contractRequests": count,
                      "paths": len(spec["paths"]), "whitespaceValidationAligned": True}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8100/api/v1")
    verify(parser.parse_args().base_url)
