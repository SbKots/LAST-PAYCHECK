# Карта 24 операций Supabase

Все 24 исходные операции реализованы. Старые пути указаны только для сопоставления с практиками; действующий HTTPS-контракт — [openapi.yaml](../../openapi.yaml). Приватные save_game/user_save добавлены отдельно.

| Код | Назначение | Текущий маршрут | Облако |
|---|---|---|---|
| OP-01 | Каталог опубликованной версии | `POST /rest/v1/rpc/get_catalog` | HTTP 200 verified |
| OP-02 | Профессии | `POST /rest/v1/rpc/list_catalog` | HTTP 200 verified |
| OP-03 | Навыки | `POST /rest/v1/rpc/list_catalog` | HTTP 200 verified |
| OP-04 | Игровые должности | `POST /rest/v1/rpc/list_catalog` | HTTP 200 verified |
| OP-05 | Историческая инфляция | `POST /rest/v1/rpc/list_catalog` | HTTP 200 verified |
| OP-06 | Марки автомобилей | `POST /rest/v1/rpc/list_catalog` | HTTP 200 verified |
| OP-07 | Модели автомобилей | `POST /rest/v1/rpc/list_catalog` | HTTP 200 verified |
| OP-08 | Трассы | `POST /rest/v1/rpc/list_catalog` | HTTP 200 verified |
| OP-09 | Конфигурации заездов | `POST /rest/v1/rpc/list_catalog` | HTTP 200 verified |
| OP-10 | Профессия по id | `POST /rest/v1/rpc/get_catalog_item` | HTTP 200 verified |
| OP-11 | Модель автомобиля по id | `POST /rest/v1/rpc/get_catalog_item` | HTTP 200 verified |
| OP-12 | Трасса по id | `POST /rest/v1/rpc/get_catalog_item` | HTTP 200 verified |
| OP-13 | Создать черновик из опубликованной версии | `POST /rest/v1/rpc/create_draft` | admin draft CRUD verified; delete 28/28 regression |
| OP-14 | Навыки черновика | `POST /rest/v1/rpc/list_draft_skills` | admin draft CRUD verified; delete 28/28 regression |
| OP-15 | Добавить навык в черновик | `POST /rest/v1/rpc/create_skill` | admin draft CRUD verified; delete 28/28 regression |
| OP-16 | Навык черновика по id | `POST /rest/v1/rpc/get_draft_skill` | admin draft CRUD verified; delete 28/28 regression |
| OP-17 | Заменить навык черновика | `POST /rest/v1/rpc/update_skill` | admin draft CRUD verified; delete 28/28 regression |
| OP-18 | Удалить навык без зависимостей | `POST /rest/v1/rpc/delete_skill` | admin draft CRUD verified; delete 28/28 regression |
| OP-19 | Опубликовать целостный каталог | `POST /rest/v1/rpc/publish_catalog` | publication not exercised in cloud; separate approval required |
| OP-20 | Инфляция по стране за период до десяти лет включительно | `GET /functions/v1/providers/worldbank/inflation` | HTTP 200 real World Bank |
| OP-21 | Поиск складских профессий O*NET | `GET /functions/v1/providers/onet/occupations` | HTTP 503 controlled; external provider keys absent |
| OP-22 | Навыки профессии O*NET | `GET /functions/v1/providers/onet/skills/{occupationCode}` | HTTP 503 controlled; external provider keys absent |
| OP-23 | Страница марок CarsBase | `GET /functions/v1/providers/cars/brands` | HTTP 503 controlled; external provider keys absent |
| OP-24 | Страница моделей CarsBase | `GET /functions/v1/providers/cars/models` | HTTP 503 controlled; external provider keys absent |

Все операции имеют локальный положительный и отрицательный сценарий. Для RPC используются apikey и профили lastpay_api; admin требует реестр и постоянный JWT. provider_cache не является клиентским API. DELETE после миграции 004 возвращает 200/receipt. Публикация в облаке не проверялась.
