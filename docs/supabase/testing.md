# Проверка Supabase backend

## Локальный стенд без Ktor

Нужны отдельный одноразовый PostgreSQL 17, PostgREST, Deno 2.9.8, Python и Node.js.
Python зависимости: `python -m pip install -r server/supabase/scripts/requirements-test.txt`.
Newman: `npm install --prefix server/supabase/.local/newman --no-audit --no-fund newman@6.2.1`.
Инструменты устанавливать из официальных дистрибутивов; локальный Docker не нужен.
В Windows PostgreSQL/PostgREST требуют каталог bin с LIBPQ.dll.

```powershell
$env:TEST_PG_BIN=(Resolve-Path server/supabase/.local/pg/bin).Path
$env:DENO_BINARY=(Resolve-Path server/supabase/.local/deno/deno.exe).Path
$env:POSTGREST_BINARY=(Resolve-Path server/supabase/.local/postgrest/postgrest.exe).Path
$env:TEST_PG_PORT='55432'
$env:PYTHONDONTWRITEBYTECODE='1'
python server/supabase/scripts/check-source.py
python server/supabase/scripts/test-local.py --disposable-postgres
python server/supabase/scripts/test-mutation-responses.py --disposable-postgres
```

PostgreSQL владелец стенда запускает отдельно на 127.0.0.1. Не задавать рабочий
пользовательский кластер: bootstrap настраивает общие роли и пароль тестового gateway.
Скрипты создают новые случайные БД на localhost и не принимают удалённый URL БД.
Игнорируемая папка server/supabase/.local/ предназначена для локальных инструментов
и результатов. В CI PostgreSQL запускается как сервис.
На Linux задавать DENO_BINARY/POSTGREST_BINARY или использовать инструменты PATH.
Параметры TEST_REST_PORT/TEST_API_PORT/TEST_RESPONSE_PORT позволяют сменить локальные порты.

Отдельно TypeScript: из server/supabase выполнить `deno task check` и `deno task test`.
Для Python нужны подключение owner локального disposable-кластера и psycopg.
Не сохранять пароли в документации. На CI пароль является явно тестовой константой,
не связан с Supabase. JWT генерируются и передаются дочерним процессам в памяти.

## Фактические результаты

PostgreSQL 17.6, PostgREST 16.5, Deno 2.9.8, Newman 6.2.1; 6 ноября 2026 года.

| Уровень | Результат |
|---|---|
| Deno fmt lint check | PASS |
| TypeScript unit | 53/53 |
| SQL/RLS | 67/67 |
| API assertions | 298, без ошибок |
| Покрытие исходных операций | 24 положительных и 24 отрицательных сценария |
| Newman | 48 запросов, 144 assertions, 0 failures |
| Основной runner | 366 проверок, 0 ошибок |
| Регрессия мутаций | 99/99; также проверялась на PostgREST 14.18 |
| OpenAPI | Валидный 3.0.3; проверка запросов и ответов текущего Supabase API |
| Миграции | Применение 001–004 локально; контрольные суммы 4/4 |
| Workflow | Локальная проверка actionlint 1.7.12 прошла |

Unit, HTTP-запросы и assertions не складываются в одно число тестов. Основной runner
содержит SQL, API и один итог Newman. Auth/JWT issuer и внешние providers в локальном
стенде — фикстуры. Настоящий PostgreSQL/PostgREST/Deno исполняет рабочий SQL и TypeScript.
Подробные результаты локального прогона сохраняются в server/supabase/.local/ и исключены из Git.

Регрессия сопоставляет HTTP с SQL-состоянием. До 004 она воспроизводит 204 с
Content-Length:4; повтор шлюза моделируется явно, а не настоящим Envoy. После 004
проверяет 200/receipt, ревизию +1, 409/412 без изменений и откат после исключения
после DML. Облачные подтверждения Envoy находятся отдельно в evidence/cloud-delete.json.

### Повторный локальный прогон

При повторной локальной проверке на PostgreSQL 17.6, PostgREST 16.5 и Deno 2.9.8
прошли 102 модульных теста, 389 проверок основного набора и 99 проверок мутаций.
Основной набор включал 67 проверок SQL/RLS, 24 положительных и 24 отрицательных
сценария, а также Newman (48 запросов, 144 проверки). Браузерный каталог проверен
на локальной связке Edge Function, PostgREST и PostgreSQL. Проверки OpenAPI,
контрольных сумм четырёх миграций и actionlint также прошли.

## Реальное облако

[Безопасные доказательства](evidence/index.md): публичные RPC 200, закрытые схемы 406,
Auth двух пользователей и изоляция сохранений 54/54, Edge Auth/CORS, World Bank 200,
ONET/CarsBase 503 без ключей. DELETE после миграции 004 — 28/28; Envoy attempts=1.
При stale DELETE 412 SQL digest и revision не меняются. Слабый W/ETag не появился
при identity и gzip/deflate/br; no-transform исключил фактическое сжатие.
Пишущие облачные проверки выполнялись ранее по отдельному разрешению, в финализации
они не повторялись. Положительная облачная публикация не выполнялась.

## Автоматизация

Workflow `.github/workflows/supabase-tests.yml`: Ubuntu, временный postgres:17 service,
установка Deno/Python/Node/PostgREST, основной runner, source checks и регрессия мутаций.
Контейнер PostgreSQL используется только на GitHub runner; локальный Docker не нужен.
Токенов Supabase/GitHub, deploy и облачных пишущих тестов в workflow нет.
Загружаются только safe summary JSON и JUnit XML. Полные HTTP/Newman отчёты с
подставленными Authorization заголовками выгружать нельзя. run-newman.cjs ограничивает
адрес localhost. Старый Ktor workflow удалён вместе с устаревшей реализацией.

## Ограничения

Положительная облачная публикация каталога, Android end-to-end/refresh/offline,
нагрузка и максимальный payload пока
не подтверждены. Сканирование секретов по шаблонам является эвристикой, а не
доказательством отсутствия любой произвольной парольной строки. Применённые миграции
не переписывались, облачная БД в финализации не изменялась.
