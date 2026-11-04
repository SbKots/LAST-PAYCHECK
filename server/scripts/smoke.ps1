param([string]$BaseUrl='http://127.0.0.1:8089/api/v1')
$ErrorActionPreference='Stop'
$serverRoot=Split-Path -Parent $PSScriptRoot
$values=@{}
$envFile=Join-Path $serverRoot '.env'
if(Test-Path -LiteralPath $envFile){Get-Content -LiteralPath $envFile | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $values[$matches[1].Trim().TrimStart([char]0xfeff)]=$matches[2].Trim() }
}}
$token=if($env:ADMIN_TOKEN){$env:ADMIN_TOKEN}else{$values['ADMIN_TOKEN']}
if(-not $token){throw 'ADMIN_TOKEN is required'}
$evidence=[System.Collections.Generic.List[object]]::new()
function Request-Api([string]$Method,[string]$Path,[int]$Expected,[object]$Body=$null,[string]$ETag='', [bool]$Admin=$false) {
    $headers=@{}
    if($Admin){$headers.Authorization="Bearer $token"}
    if($ETag){$headers['If-Match']=$ETag}
    $args=@{Method=$Method;Uri="$BaseUrl$Path";Headers=$headers;SkipHttpErrorCheck=$true;TimeoutSec=20}
    if($null -ne $Body){$args.ContentType='application/json';$args.Body=[Text.Encoding]::UTF8.GetBytes(($Body|ConvertTo-Json -Depth 20 -Compress))}
    $response=Invoke-WebRequest @args
    $raw=[string]$response.Content
    $data=if($raw){$raw|ConvertFrom-Json -Depth 30}else{$null}
    $evidence.Add([pscustomobject]@{method=$Method;path=$Path;status=[int]$response.StatusCode;body=$raw;etag=($response.Headers['ETag'] -join '');location=($response.Headers['Location'] -join '')})
    if([int]$response.StatusCode -ne $Expected){throw "$Method $Path expected $Expected, got $($response.StatusCode): $raw"}
    Write-Output "$Method $Path -> $Expected"
    return [pscustomobject]@{Data=$data;ETag=($response.Headers['ETag'] -join '');Status=[int]$response.StatusCode}
}
# Keep diagnostics on the information stream so returned response objects stay unambiguous.
function Check([string]$Method,[string]$Path,[int]$Expected,[object]$Body=$null,[string]$ETag='', [bool]$Admin=$false) {
    $result=@(Request-Api $Method $Path $Expected $Body $ETag $Admin)
    $result | Where-Object {$_ -is [string]} | ForEach-Object {Write-Host $_}
    return $result | Where-Object {$_ -isnot [string]}
}
$manifest=Check GET '/catalog' 200
$base=$manifest.Data.catalogVersion
$list=@('/occupations','/skills','/jobs','/inflation','/cars/brands','/cars/models','/tracks','/race-configurations')
foreach($path in $list){$suffix=if($path -eq '/inflation'){'&country=RUS&yearFrom=2020&yearTo=2020'}else{''};$null=Check GET "${path}?catalogVersion=$base$suffix" 200}
foreach($path in @('/occupations/warehouse','/cars/models/city-hatch','/tracks/night-delivery')){$null=Check GET "${path}?catalogVersion=$base" 200}
$version='smoke-'+[DateTime]::UtcNow.ToString('yyyyMMddHHmmss')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,6)
$draft=Check POST '/admin/catalogs' 201 @{version=$version;baseVersion=$base} '' $true
$null=Check GET "/admin/catalogs/$version/skills" 200 $null '' $true
$skill=@{id='smoke-skill';code='smoke-skill';title='Учебная проверка CRUD';description='DEMO';source='DEMO';externalId=$null}
$made=Check POST "/admin/catalogs/$version/skills" 201 $skill $draft.ETag $true
$null=Check GET "/admin/catalogs/$version/skills/smoke-skill" 200 $null '' $true
$update=@{code='smoke-skill';title='Изменённый учебный навык';description='DEMO';source='DEMO';externalId=$null}
$changed=Check PUT "/admin/catalogs/$version/skills/smoke-skill" 200 $update $made.ETag $true
$null=Check DELETE "/admin/catalogs/$version/skills/logistics" 409 $null $changed.ETag $true
$conflict=@{id='smoke-duplicate';code='smoke-skill';title='DEMO';description='';source='DEMO';externalId=$null}
$null=Check POST "/admin/catalogs/$version/skills" 409 $conflict $changed.ETag $true
$null=Check PUT "/admin/catalogs/$version/skills/smoke-skill" 412 $update $draft.ETag $true
$invalid=@{code='smoke-skill';title='DEMO';description='';source='ONET';externalId=$null}
$null=Check PUT "/admin/catalogs/$version/skills/smoke-skill" 422 $invalid $changed.ETag $true
$deleted=Check DELETE "/admin/catalogs/$version/skills/smoke-skill" 204 $null $changed.ETag $true
$null=Check GET "/admin/catalogs/$version/skills/smoke-skill" 404 $null '' $true
$null=Check POST "/admin/catalogs/$version/publish" 200 $null $deleted.ETag $true
$null=Check POST "/admin/catalogs/$version/publish" 409 $null '"rev-5"' $true
$null=Check DELETE "/admin/catalogs/$version/skills/logistics" 409 $null '"rev-5"' $true
$null=Check GET "/admin/catalogs/$version/skills" 401
$null=Check GET '/skills?catalogVersion=missing' 404
$null=Check GET "/skills?catalogVersion=$base&pageSize=101" 422
$null=Check GET "/skills?catalogVersion=$base&unexpected=1" 400
$null=Check GET "/occupations/missing?catalogVersion=$base" 404
$null=Check GET "/skills?catalogVersion=$base" 200
$target=Join-Path $serverRoot '.local/http-evidence.json'
New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
$evidence|ConvertTo-Json -Depth 40|Set-Content -LiteralPath $target -Encoding utf8
Write-Host "All $($evidence.Count) real HTTP checks passed. Evidence: .local/http-evidence.json (no Authorization headers)."
