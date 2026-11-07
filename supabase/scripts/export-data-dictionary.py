"""Export schema metadata from the last LOCAL disposable test run, never cloud."""
import json,os
from pathlib import Path
import psycopg
ROOT=Path(__file__).resolve().parents[2]
result=json.loads((ROOT/'supabase/.local/results.json').read_text(encoding='utf-8'))
assert result['passed'] and result['database'].startswith('lastpay_sb_')
lines=['# Структура PostgreSQL Supabase','','Получено из отдельной локальной БД после применения миграций 001–004. Здесь только метаданные схемы, без пользовательских строк. Источник облачного развёртывания — те же неизменяемые SQL-файлы. auth.users управляется платформой и не включена в 18 таблиц приложения.','']
with psycopg.connect(host='127.0.0.1',port=int(os.environ.get('TEST_PG_PORT','55432')),user='postgres',password=os.environ.get('TEST_PG_PASSWORD',''),dbname=result['database'],options='-c default_transaction_read_only=on') as c:
    tables=c.execute("SELECT n.nspname,t.relname,t.oid,t.relrowsecurity,t.relforcerowsecurity FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname IN ('lastpay','lastpay_private') AND t.relkind='r' ORDER BY n.nspname,t.relname").fetchall()
    assert len(tables)==18
    for schema,table,oid,rls,forced in tables:
        lines += [f'## {schema}.{table}', '',f'RLS: {rls}; FORCE RLS: {forced}. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.','', '| Поле | Тип | NOT NULL | Default |','|---|---|---|---|']
        for name,typ,notnull,default in c.execute("SELECT a.attname,format_type(a.atttypid,a.atttypmod),a.attnotnull,pg_get_expr(d.adbin,d.adrelid) FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE a.attrelid=%s AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum",(oid,)):
            lines.append(f'| {name} | {typ} | {notnull} | {default or "—"} |')
        lines += ['', 'Ограничения:', '']
        for name,definition in c.execute('SELECT conname,pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid=%s ORDER BY contype,conname',(oid,)):
            lines += [f'- `{name}`: `{definition.replace(chr(10)," ")}`.']
        lines += ['', 'Индексы:', '']
        for (definition,) in c.execute('SELECT indexdef FROM pg_indexes WHERE schemaname=%s AND tablename=%s ORDER BY indexname',(schema,table)):
            lines += ['- `'+definition+'`.']
        lines += ['']
lines += ['## Представление API','','lastpay_api.user_save использует security_invoker=true; читает только данные владельца через RLS. Прямая запись клиенту запрещена. Для записи применяется save_game с auth.uid() и expectedRevision.']
(ROOT/'docs/supabase/data-dictionary.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print('Exported 18-table schema dictionary; no data rows')
