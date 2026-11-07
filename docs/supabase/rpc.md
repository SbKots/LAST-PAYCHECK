# RPC и формат запросов

Все RPC находятся в `lastpay_api`. В Data API использовать POST
`/rest/v1/rpc/<имя>` и тело `{"args":{...}}`. Заголовки Content-Profile и
Accept-Profile должны указывать lastpay_api. Проектный публичный ключ — в apikey;
сессия пользователя — в Authorization: Bearer. Идентификаторы — строчные
латинские буквы, цифры, точка, дефис, подчёркивание, первый символ — буква/цифра;
максимум 64, для версии — 32. Неизвестные параметры отклоняются.

| RPC | Поля args | Доступ и результат |
|---|---|---|
| get_catalog | Пустой объект | Публичный manifest последней публикации |
| list_catalog | resource, catalogVersion; page/pageSize; специальные фильтры | Публичные data + meta |
| get_catalog_item | resource, catalogVersion, id | Карточка occupations/car-models/tracks |
| list_draft_skills | catalogVersion; page/pageSize | Только admin, ETag и страница навыков |
| get_draft_skill | catalogVersion, id | Только admin, ETag и карточка навыка |
| create_draft | version, baseVersion | Только admin; 201, ETag rev-1; атомарная копия |
| create_skill | version, ifMatch, body | Только admin; 201, новый ETag |
| update_skill | version, id, ifMatch, body | Только admin; 200, новый ETag |
| delete_skill | version, id, ifMatch | Только admin; 200, `{deleted:true,id,catalogVersion}`, новый ETag (миграция 004) |
| publish_catalog | version, ifMatch | Только admin; 200, manifest и неизменяемая публикация |
| save_game | snapshot, expectedRevision | Постоянный владелец; атомарная запись собственного снимка |
| provider_cache | Служебный протокол op/provider/hash/owner и др. | Только service_role; клиентам не предоставляется |

resource: occupations, skills, jobs, inflation, car-brands, car-models, tracks,
race-configurations. page по умолчанию 1, диапазон 1..1 000 000; pageSize по
умолчанию 20, диапазон 1..100. Выход за диапазон — 422, неверный целочисленный
синтаксис — 400. Конец данных — пустой data с прежним total.

Для inflation обязательны country (ISO alpha-3), yearFrom, yearTo (1900..2100,
необратный период). Для car-models возможен brandId, для race-configurations — trackId.
Заголовок ETag содержит кавычки; в ifMatch передавать всю строку.
После миграции 004 ответы с ETag содержат `Cache-Control: private, no-store,
no-transform`. Слабый ETag `W/"rev-N"` не является допустимым ifMatch; повторите
чтение с identity, не удаляйте W/ автоматически. Успешный delete_skill теперь
возвращает 200 и JSON-подтверждение вместо прежнего 204; см.
[диагностику и миграцию](delete-response-diagnosis.md).

body навыка содержит code (64), title (160), description (4000), source (DEMO/ONET),
externalId (null или строка до 64). Для create_skill дополнительно обязателен id;
для update_skill id берётся из args и не допускается в body. Поля обязательны,
лишние запрещены. ONET требует непустой externalId, description может быть пустым.
Длина учитывается по Unicode-кодовым точкам, обязательные строки не могут состоять
из пробельных символов. Нулевой символ отклоняет сам JSONB/PostgREST до функции.

Доменная ошибка имеет `error` с code, message, requestId и details.
HTTP 400 — формат/параметры; 401 — нет постоянной сессии; 403 — нет полномочий;
404 — нет версии/карточки; 409 — UNIQUE/FK либо опубликованная версия;
412 — устаревшая ревизия; 422 — ограничения/поля; 429 — квота;
503 — нет доступной публикации/источника/кэша. Неожиданная ошибка — общий 500.
Ошибки до RPC имеют штатный формат Supabase/PostgREST, см. architecture.md.

save_game не принимает user_id. snapshot — объект до 256 КиБ, expectedRevision=0
создаёт новое сохранение, дальнейшее изменение требует текущую ревизию.
Прочитать собственную строку можно через invoker view `/rest/v1/user_save`.
Прямое редактирование view и таблицы клиентом запрещено.

Служебные операции provider_cache: limit — учёт квот проверенного uid;
lookup — чтение кэша; acquire — аренда обновления; store — запись только при
действующей аренде; release — освобождение. provider и hash проверяются по
whitelist/формату. Контракт предназначен только для серверного модуля `_shared/cache.ts`.

Две операции чтения черновиков специально доступны anon на уровне EXECUTE,
но сами возвращают 401 без сессии и 403 без членства; это сохраняет явную семантику
ошибки. Записывающие RPC вообще не предоставлены anon: отказ может сформировать
PostgREST до вызова функции. У всех обычных пользователей отсутствует прямой DML.
