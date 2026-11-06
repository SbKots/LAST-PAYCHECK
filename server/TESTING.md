# Тестирование сервера LAST PAYCHECK

Запуск из корня проекта. Unit-тесты и проверки Ktor без PostgreSQL:

```powershell
cd server
./scripts/dev.ps1 test
```

На Linux: `bash server/gradlew -p server test`. JDK 21 обязателен.
`DatabaseTest` без переменной `LASTPAY_TEST_DATABASE=true` явно пропускается.

Полный прогон выполняется только на отдельном одноразовом кластере PostgreSQL 17.
Кластер должен слушать 127.0.0.1; не указывайте порт существующей пользовательской БД:
миграция V3 и provisionRoles создают и настраивают общие для кластера роли.
Скрипт не читает `.env`, создаёт БД `lastpay_test_<случайный суффикс>` и не удаляет базы.
Секреты генерируются в памяти и передаются дочерним процессам через окружение.

```powershell
# Установить зависимости выбранным Python и Node.js
python -m pip install -r server/scripts/requirements-test.txt
npm install --prefix server/.local/newman --no-audit --no-fund newman@6.2.1
$env:TEST_PG_PORT='55432'
$env:TEST_PG_PASSWORD='<пароль только одноразового локального кластера>'
python server/scripts/practice11.py --disposable-postgres
```

Скрипт выполняет installDist, миграции V1–V3, настройку ролей, DEMO seed,
`clean test build installDist`, 24 негативных HTTP-сценария, контракт практики 10,
smoke практики 9 (при наличии pwsh), полную существующую коллекцию Newman.
Для каждого HTTP-этапа запускается отдельный Netty, чтобы лимиты предыдущего этапа
не влияли на следующий. Процесс останавливается в finally, в том числе при ошибке.

World Bank в полном прогоне использует контролируемую запись PostgreSQL-кэша
RUS 2020–2022, созданную только в тестовой БД. Это проверка ответа из кэша,
а не подтверждение доступности реального World Bank. Ошибки upstream проверяются
через Ktor MockEngine; реальные ключи O*NET/CarsBase не нужны и не используются.

Результаты текущего запуска: `server/.local/practice11-summary.json`,
`negative-results.json`, `newman-summary.json`, логи этапов и Gradle XML/HTML.
Полные Newman JSON-репорты намеренно не сохраняются: они могут содержать
подставленный Authorization. Сохраняются статистика и ошибки утверждений.
В GitHub Actions загружаются только безопасные отчёты из allowlist.

Матрица минимального негативного покрытия: `server/postman/negative-cases.json`.
Скрипт сверяет её с обоими OpenAPI-файлами и останавливается при расхождении.
Дополнительные негативные проверки находятся в RoutesTest, DatabaseTest,
ProviderTest, smoke.ps1 и в последней папке существующей коллекции Postman.

## Тест-план

Объект: 24 операции Ktor, CRUD черновиков, ETag, публикация, PostgreSQL RLS,
SQL-ограничения, внешние провайдеры и кэш. Android и облачный хостинг вне объёма.

Входные условия: JDK 21, Gradle wrapper, одноразовый локальный PostgreSQL 17,
Python-зависимости, Node.js/Newman, отсутствие реальных ключей провайдеров.
Подготовка: отдельная случайная БД, миграции, ограниченные роли, DEMO seed.

Уровни проверки:
- Изолированные JUnit 5: validId, integer, JSON shape/text/skillInput, канонизация,
  SHA-256, CatalogRevision, ошибки, AdminAccess, RequestLimiter, HTTP-провайдеры.
- Ktor testApplication: маршруты, JSON ошибок, заголовки, 400/401/403/404/405/422/429/503.
- PostgreSQL: RLS, CHECK, UNIQUE/FK, транзакционный откат, ETag, кэш и fallback.
- Реальный Netty: негативная матрица, успешные операции OpenAPI, smoke и Newman.

Критерий выхода: минимум 10 проходящих unit-тестов; покрытие негативным сценарием
каждой операции; отсутствие ошибок и пропусков в полном прогоне; успешные сборка,
контракт и Newman; соответствие Word-отчёта фактическим данным.
CI считается подтверждённым только после реального запуска на GitHub.

Приоритет P1: авторизация, RLS, неизменяемость публикаций, конфликты и откат.
Приоритет P2: валидация, пагинация, заголовки, кэш, ошибки провайдеров, лимиты.
Ожидаемые результаты по всем 24 операциям приведены в negative-cases.json;
пошаговые основные тест-кейсы и фактические итоги — в docs/11-server-testing.docx.
