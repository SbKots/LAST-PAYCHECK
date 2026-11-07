# Файлы финализации

Состояние относительно HEAD ba1380c в feature/supabase-backend. Новые по Git файлы включают Supabase-изменения, существовавшие незакоммиченными до финализации. Commit/push/PR не выполнялись. Локальные инструменты, QA и логи исключены .gitignore.

| Статус | Файл |
|---|---|
| Удалён из рабочего дерева | .github/workflows/backend-tests.yml |
| Изменён | README.md |
| Изменён | docs/08-backend.docx |
| Изменён | docs/11-server-testing-results.json |
| Изменён | docs/11-server-testing.docx |
| Изменён | docs/diagrams/backend-er.drawio |
| Изменён | openapi.yaml |
| Удалён из рабочего дерева | server/.env.example |
| Удалён из рабочего дерева | server/.gitignore |
| Удалён из рабочего дерева | server/TESTING.md |
| Удалён из рабочего дерева | server/build.gradle.kts |
| Удалён из рабочего дерева | server/gradle.properties |
| Удалён из рабочего дерева | server/gradle/wrapper/gradle-wrapper.jar |
| Удалён из рабочего дерева | server/gradle/wrapper/gradle-wrapper.properties |
| Удалён из рабочего дерева | server/gradlew |
| Удалён из рабочего дерева | server/gradlew.bat |
| Удалён из рабочего дерева | server/openapi-integrations.yaml |
| Удалён из рабочего дерева | server/postman/LAST-PAYCHECK.postman_collection.json |
| Удалён из рабочего дерева | server/postman/local.postman_environment.json |
| Удалён из рабочего дерева | server/postman/negative-cases.json |
| Удалён из рабочего дерева | server/scripts/dev.ps1 |
| Удалён из рабочего дерева | server/scripts/init-env.ps1 |
| Удалён из рабочего дерева | server/scripts/practice10-contract.py |
| Удалён из рабочего дерева | server/scripts/practice11.py |
| Удалён из рабочего дерева | server/scripts/requirements-test.txt |
| Удалён из рабочего дерева | server/scripts/run-newman.cjs |
| Удалён из рабочего дерева | server/scripts/smoke.ps1 |
| Удалён из рабочего дерева | server/scripts/validate-http.py |
| Удалён из рабочего дерева | server/settings.gradle.kts |
| Удалён из рабочего дерева | server/src/main/kotlin/lastpay/Main.kt |
| Удалён из рабочего дерева | server/src/main/kotlin/lastpay/api/CatalogRoutes.kt |
| Удалён из рабочего дерева | server/src/main/kotlin/lastpay/api/IntegrationRoutes.kt |
| Удалён из рабочего дерева | server/src/main/kotlin/lastpay/api/Validation.kt |
| Удалён из рабочего дерева | server/src/main/kotlin/lastpay/application/CatalogService.kt |
| Удалён из рабочего дерева | server/src/main/kotlin/lastpay/application/DemoSeeder.kt |
| Удалён из рабочего дерева | server/src/main/kotlin/lastpay/domain/Catalog.kt |
| Удалён из рабочего дерева | server/src/main/kotlin/lastpay/domain/Providers.kt |
| Удалён из рабочего дерева | server/src/main/kotlin/lastpay/infrastructure/CatalogDatabase.kt |
| Удалён из рабочего дерева | server/src/main/kotlin/lastpay/infrastructure/Configuration.kt |
| Удалён из рабочего дерева | server/src/main/kotlin/lastpay/infrastructure/Providers.kt |
| Удалён из рабочего дерева | server/src/main/kotlin/lastpay/security/AdminAccess.kt |
| Удалён из рабочего дерева | server/src/main/resources/db/migration/V1__catalog_schema.sql |
| Удалён из рабочего дерева | server/src/main/resources/db/migration/V2__published_catalog_guards.sql |
| Удалён из рабочего дерева | server/src/main/resources/db/migration/V3__catalog_row_security.sql |
| Удалён из рабочего дерева | server/src/main/resources/demo-catalog.json |
| Удалён из рабочего дерева | server/src/main/resources/logback.xml |
| Удалён из рабочего дерева | server/src/test/kotlin/lastpay/DatabaseTest.kt |
| Удалён из рабочего дерева | server/src/test/kotlin/lastpay/RoutesTest.kt |
| Удалён из рабочего дерева | server/src/test/kotlin/lastpay/ValidationTest.kt |
| Новый | .github/workflows/supabase-tests.yml |
| Новый | docs/diagrams/backend-er.mmd |
| Новый | docs/diagrams/backend-er.png |
| Новый | docs/diagrams/backend-er.svg |
| Новый | docs/supabase/android.md |
| Новый | docs/supabase/architecture.md |
| Новый | docs/supabase/data-dictionary.md |
| Новый | docs/supabase/delete-response-diagnosis.md |
| Новый | docs/supabase/deployment.md |
| Новый | docs/supabase/evidence/README.md |
| Новый | docs/supabase/evidence/cloud-delete.json |
| Новый | docs/supabase/evidence/cloud-edge.json |
| Новый | docs/supabase/evidence/cloud-providers-auth.json |
| Новый | docs/supabase/evidence/cloud-public.json |
| Новый | docs/supabase/evidence/cloud-security.json |
| Новый | docs/supabase/evidence/local-mutation-results.json |
| Новый | docs/supabase/files.md |
| Новый | docs/supabase/free-tier.md |
| Новый | docs/supabase/implementation-report.md |
| Новый | docs/supabase/operation-map.json |
| Новый | docs/supabase/operation-map.md |
| Новый | docs/supabase/rpc.md |
| Новый | docs/supabase/security.md |
| Новый | docs/supabase/test-results.json |
| Новый | docs/supabase/testing.md |
| Новый | supabase/.env.example |
| Новый | supabase/.gitignore |
| Новый | supabase/README.md |
| Новый | supabase/config.toml |
| Новый | supabase/contracts/catalog-models.yaml |
| Новый | supabase/contracts/migration-checksums.json |
| Новый | supabase/deno.json |
| Новый | supabase/functions/_shared/backend.ts |
| Новый | supabase/functions/_shared/cache.ts |
| Новый | supabase/functions/_shared/countries.json |
| Новый | supabase/functions/_shared/http.ts |
| Новый | supabase/functions/_shared/providers.ts |
| Новый | supabase/functions/providers/index.ts |
| Новый | supabase/migrations/202611060001_schema.sql |
| Новый | supabase/migrations/202611060002_rpc.sql |
| Новый | supabase/migrations/202611060003_demo.sql |
| Новый | supabase/migrations/202611060004_delete_response.sql |
| Новый | supabase/postman/LAST-PAYCHECK-Supabase.postman_collection.json |
| Новый | supabase/postman/local.postman_environment.json |
| Новый | supabase/scripts/check-source.py |
| Новый | supabase/scripts/export-data-dictionary.py |
| Новый | supabase/scripts/generate-diagrams.py |
| Новый | supabase/scripts/generate-openapi.py |
| Новый | supabase/scripts/generate-postman.py |
| Новый | supabase/scripts/requirements-test.txt |
| Новый | supabase/scripts/run-newman.cjs |
| Новый | supabase/scripts/test-local.py |
| Новый | supabase/scripts/test-mutation-responses.py |
| Новый | supabase/scripts/verify-deployment.py |
| Новый | supabase/tests/bootstrap.sql |
| Новый | supabase/tests/local_server.ts |
| Новый | supabase/tests/sql_cases.py |
| Новый | supabase/tests/unit_test.ts |

Старые server/ и backend-tests.yml сохранены в соседней папке LAST-PAYCHECK-legacy-backup-20261106 и в истории ba1380c.
Материалы №1–7 не редактировались. Android-исходников в этой копии нет.
DOCX №9 и №10 не требуются: материалы сдаются кодом и API. Три раздельных PNG ER-модели заменены одной основной схемой с редактируемой детализацией.
