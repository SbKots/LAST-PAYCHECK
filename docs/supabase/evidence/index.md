# Доказательства облачных проверок

Это безопасные отчёты реальных запусков 6 ноября 2026 года, сохранённые из локальных
артефактов предыдущих разрешённых проверок. Финализация документации не повторяет
пишущие облачные тесты. Локальные тестовые фикстуры не выдаются за Supabase Auth.

| Отчёт | Подтверждение |
|---|---|
| [cloud-public.json](cloud-public.json) | get_catalog, восемь list_catalog, карточки — 200; закрытые схемы — 406 |
| [cloud-edge.json](cloud-edge.json) | Реальный deploy providers; отсутствие/неверный JWT — 401; CORS |
| [cloud-providers-auth.json](cloud-providers-auth.json) | Auth 200; World Bank 200 с реальными значениями; O*NET/CarsBase 503 без ключей |
| [cloud-security.json](cloud-security.json) | Два пользователя; 54/54: RLS, приватность, ревизии, запреты admin и служебного RPC |
| [cloud-delete.json](cloud-delete.json) | Миграции 001–004, CRUD нового черновика, 28/28, DELETE 200/receipt, stale 412, неизменность demo-1 и сохранений |

В cloud-delete.json дополнительно сохранены безопасные записи исторического лога:
успешный DELETE и намеренный stale DELETE имеют по одной upstream-попытке Envoy.
Сильный ETag сохранён при Accept-Encoding gzip/deflate/br и identity; фактический
Content-Encoding отсутствует благодаря no-transform. Публикация тестового каталога
не выполнялась. Прежний дефект 204/Content-Length:4 описан отдельно в диагностике.

Пароли, JWT, refresh_token, privileged keys и полные HTTP-заголовки в этих файлах отсутствуют.
