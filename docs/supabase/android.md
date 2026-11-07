# Подключение Android к новому backend

В репозитории ещё нет исходников Android-модуля. Этот контракт позволяет создать
клиент без Ktor, локального ПК и туннелей в существующем развёрнутом Supabase.
В приложении сохраняются только URL проекта, публичный ключ и сессия конкретного пользователя.
Привилегированные ключи, пароль БД, старый ADMIN_TOKEN и ключи провайдеров не нужны.

## Auth

Регистрация: POST `<SUPABASE_URL>/auth/v1/signup`, заголовок `apikey: <PUBLIC_KEY>`,
Content-Type application/json, тело `{"email":"...","password":"..."}`.
Если требуется подтверждение почты, завершить его до входа.

Вход: POST `/auth/v1/token?grant_type=password` с тем же телом и публичным apikey.
Использовать access_token в Authorization: Bearer, refresh_token — для обновления
через POST `/auth/v1/token?grant_type=refresh_token`. Не отправлять publishable key
как пользовательский Bearer. Сессии хранить защищённо средствами Android/Keystore,
не логировать и не помещать в исходники. Для библиотечного клиента использовать
поддерживаемую Supabase Auth session management, не собственный JWT-парсер.

## Каталоги через Data API

Общий адрес — `<SUPABASE_URL>/rest/v1/rpc/<имя>`, метод POST.
Заголовки: apikey, Content-Type application/json, Content-Profile lastpay_api,
Accept-Profile lastpay_api; для вошедшего пользователя также Bearer access_token.

Manifest:
```json
{"args":{}}
```
к `get_catalog`.

Список:
```json
{"args":{"resource":"skills","catalogVersion":"demo-1","page":1,"pageSize":20}}
```
к `list_catalog`. Версию брать из manifest, а не закреплять demo-1 в приложении.
Доступные resource: occupations, skills, jobs, inflation, car-brands, car-models,
tracks, race-configurations. Для inflation обязательны country/yearFrom/yearTo;
для car-models необязателен brandId, для race-configurations — trackId.

Карточка:
```json
{"args":{"resource":"occupations","catalogVersion":"demo-1","id":"warehouse"}}
```
к `get_catalog_item`. Поддержаны occupations, car-models и tracks, как в старом API.

Публичные каталоги можно загружать без входа; приватное сохранение требует постоянной
сессии. Если в приложении по умолчанию используется профиль public, для этих запросов
явно выбрать lastpay_api. Вышедший из сессии клиент не должен повторять приватные запросы.

## Сохранение и интеграции

Прочитать собственное сохранение: GET `/rest/v1/user_save?select=*` с
Accept-Profile lastpay_api и Bearer. RLS вернёт только строку текущего владельца.

Атомарная запись: POST RPC `save_game`:
```json
{"args":{"snapshot":{"day":1},"expectedRevision":0}}
```
Получить новую revision из ответа; для последующих изменений передавать её.
На 412 перечитать сохранение и разрешить конфликт. Поле владельца не передаётся.
Снимок ограничен 256 КиБ и должен быть JSON-объектом.

Интеграции: GET `/functions/v1/providers/worldbank/inflation?country=RUS&yearFrom=2020&yearTo=2022`,
`/onet/occupations`, `/onet/skills/53-7062.00`, `/cars/brands`, `/cars/models` после
того же префикса `/functions/v1/providers`. Нужны публичный apikey и Bearer access_token.
fromCache/stale показывают происхождение ответа. 503 при отсутствии внешнего ключа
не должно блокировать игру: опубликованные каталоги содержат самостоятельные данные.

Административные RPC из [карты операций](operation-map.md) предназначены для владельца,
а не обычного игрового экрана. Считывать ETag ответа и передавать его строкой в
args.ifMatch, например `"rev-2"`. В JSON строка содержит кавычки:
```json
{"args":{"version":"draft-1","id":"skill-1","ifMatch":"\"rev-2\""}}
```

## Ошибки и offline

Нормализовать доменный error и штатный PostgREST code/message/details/hint.
401 — обновить сессию либо предложить вход; 403 — отказ доступа; 409 — конфликт;
412 — перечитать ревизию; 429/503 — учитывать Retry-After и повторять с задержкой.
Не выполнять бесконечные повторы. Кэшировать последнюю опубликованную версию
локально: игра должна работать при временной недоступности сети/Free-проекта.

Реальный Supabase Auth проверен двумя тестовыми пользователями. Android end-to-end,
refresh сессии и offline-синхронизация ещё не проверены. Исходников Android в этой копии нет.
Успешный delete_skill теперь возвращает HTTP 200 с JSON receipt; 204 больше не ожидается.
При неоднозначной транспортной ошибке не повторять мутацию автоматически; сначала перечитать состояние.

Источники: [Auth API](https://supabase.com/docs/reference/javascript/auth-signinwithpassword),
[API schemas](https://supabase.com/docs/guides/api/using-custom-schemas),
[Edge headers](https://supabase.com/docs/guides/functions/auth-headers).
