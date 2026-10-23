---
title: Подключение и разбор мобильных ошибок GlitchTip
type: runbook
last_verified: 2026-10-23
related_code:
  - tools/glitchtip.py
  - tools/glitchtip.json
  - tools/test_glitchtip.py
---

# Разбор ошибок в глитче

Сервис: [GlitchTip](https://apps-geonical.ru/errors/), организация `geonical`,
проект `nextgis-mobile` (NextGIS Mobile, ID 1). Рабочие команды уже находятся
в `tools/glitchtip.py`, подключение — в `tools/glitchtip.json`. Python 3.13,
только стандартная библиотека; установка SDK или нового connector не нужна.

CLI читает существующий приватный файл доступа в Documents/Geonical/ServerAdmin/
GlitchTip/«Вход в багрепорты.txt» текущего Windows-пользователя. Путь можно задать
через `GLITCHTIP_CREDENTIALS_FILE` или `--credentials-file`. Поддерживаются
исходные строки «Логин: …», «Пароль: …» и JSON с `email`/`password`.
Пароль, session cookies, CSRF и DSN в Git не добавлять. Cookies живут только
в памяти команды. Значения приватного файла не выводить в чат или журнал.

Из корня Android-репозитория:

```powershell
$env:TEMP='Q:\Codex\Temp'; $env:TMP=$env:TEMP; $env:TMPDIR=$env:TEMP
py -3.13 -B tools/glitchtip.py list
py -3.13 -B tools/glitchtip.py list --all
py -3.13 -B tools/glitchtip.py export --output Q:\Codex\Scratch\<task-id>\glitchtip
py -3.13 -B tools/glitchtip.py show 101
```

`show` печатает полные приватные события: при необходимости перенаправлять stdout
в файл на Q, не публиковать его содержимое. Export сохраняет все страницы событий
и комментарии открытых групп, отказывается писать внутрь Git checkout.
`events/latest` сервиса не гарантирует максимальный date_created: сверять все
события, их исходную версию/source_revision и date_created/date_received.
Переоткрытая группа не означает повтор на новой сборке; старый offline envelope
может прийти значительно позже. Число событий не равно числу пользователей/падений.

Используется проверенный API GlitchTip 6.2.6: `_allauth/browser/v1/auth/session`
и `auth/login` для session/CSRF, Sentry-compatible `/api/0/` для групп и событий.
CLI сохраняет HTTPS и тот же host при пагинации, корректирует отсутствующий
`/errors` в Link от reverse proxy. Схема сервиса: `/errors/api/openapi.json`.

## Обработка и закрытие

1. Выполнить repository preflight, проверить ветку и чужие пересекающиеся PR.
2. Сохранить исходный export вне Git. Прочитать все события группы, breadcrumbs,
   причины, версии, предыдущие комментарии и уже сделанные исправления.
3. Разделить доказанное исправление, усиление диагностики и штатный шум.
   Не глушить неизвестный ANR/native crash или реальную ошибку записи только
   потому, что воспроизведения нет. Сетевой offline/cancel, отсутствие спутникового
   решения и явно подтверждённые защитные отказы могут оставаться только локально.
4. Изменить код и добавить адресные регрессии, docs и новые карточки. Создать
   reviewable commits/PR. Для неизвестной причины описать новые поля, которые
   помогут следующему разбору; не объявлять причину установленной.
5. По поручению пользователя закрыть только обработанные группы. В комментарии
   указать вывод, код/PR, проверки, непроверенные сценарии и состояние выпуска.

```powershell
py -3.13 -B tools/glitchtip.py resolve 101 --comment-file Q:\Codex\Scratch\<task-id>\101.txt --snapshot Q:\Codex\Scratch\<task-id>\glitchtip\issues.json
py -3.13 -B tools/glitchtip.py list
```

Resolve проверяет проект, сравнивает count/lastSeen со снимком, добавляет
комментарий и меняет только status на resolved. Новые события требуют нового
чтения. Сами события и счётчики не удаляются. Закрытие после диагностического
улучшения означает завершённый разбор; новый повтор требует проверки.
Уведомления сервиса отключены. Deployment, рестарт сервера и выпуск APK
не входят в этот workflow без отдельного поручения.

Проверка CLI: `py -3.13 -B -m unittest discover -s tools -p test_glitchtip.py -v`.
Последний разбор: [2026-10-23](../reference/glitchtip-triage-2026-10-23.md).
