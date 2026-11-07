# Supabase backend LAST PAYCHECK

Рабочий backend: Auth, PostgreSQL/RLS, RPC схемы lastpay_api и Edge Functions providers и catalog.
Четыре миграции применены в существующем проекте `mmuytwyhdwttzxuredfm` (Frankfurt Free).
URL: https://mmuytwyhdwttzxuredfm.supabase.co

- [Контракт](../openapi.yaml), [архитектура](../docs/supabase/architecture.md), [RPC](../docs/supabase/rpc.md).
- [Локальная проверка](../docs/supabase/testing.md), [облачные результаты](../docs/supabase/evidence/README.md).
- [Развёртывание и обслуживание](../docs/supabase/deployment.md), [Android](../docs/supabase/android.md).

Миграции 001–004 неизменяемы после применения; следующие изменения оформлять новым SQL-файлом.
tests/bootstrap.sql и пишущие тесты предназначены исключительно для случайной локальной БД.
Новый backend не требует server/, Gradle, Docker или постоянно работающего ПК.
Публичный клиент использует publishable/anon key и собственный JWT. Привилегированные ключи
не входят в исходники, отчёты и Android. Ключ O*NET и демо-токен CarsBase настроены
в Edge Secrets. Все пять облачных маршрутов `providers` вернули HTTP 200 с
действительной сессией, включая повторы из свежего кэша.
