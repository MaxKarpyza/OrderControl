# План разработки «Заказы воды»

Источник требований: `doc/idea.md`. Платформа: Android, Kotlin, Jetpack Compose,
Room, DataStore, классические RemoteViews-виджеты. Сеть нужна только для
чтения Google Таблицы по кнопке «Проверить таблицу»; всё остальное работает
офлайн.

## Принципы

- `minSdk 30`, `targetSdk 35`, `compileSdk 35/36`.
- Один оператор, одно устройство, без аккаунтов и синхронизации.
- Интерфейс на русском, все строки в `res/values/strings.xml`.
- Бизнес-правила вынесены в чистый Kotlin (`domain/`) и покрыты JVM-тестами.
- Виджеты обновляются push-ом: `WidgetRefresh.updateAll(context)` после каждого
  звонка, сохранения формы и «Заказы обработаны».
- Никаких новых зависимостей сверх необходимого: Compose BOM, Room, DataStore,
  lifecycle, coroutines, JUnit4.

## Архитектура

```
ru.ordercontrol
├── App.kt                     Application, инициализация AppGraph
├── AppGraph.kt                ручной DI-контейнер (repository, settings, contacts)
├── MainActivity.kt            список необработанных + настройки + онбординг
├── FormActivity.kt            мини-форма заказа (открывается из виджета)
├── data/
│   ├── db/                    Room: AppDatabase, DAO, entity, конвертеры
│   ├── SettingsRepository.kt  DataStore: рабочее время, аккаунт контактов
│   ├── CallRepository.kt      операции над записями звонков
│   └── ContactRepository.kt   поиск имени, создание контакта без дубликатов
├── domain/
│   ├── PhoneNormalizer.kt     нормализация РФ/СНГ
│   ├── WorkSchedule.kt        рабочее время и дни недели
│   ├── CallRules.kt           создавать/не создавать, склейка
│   ├── BlacklistPolicy.kt     PERMANENT / ONCE
│   └── CallLogReconciler.kt   закрытие записей, удалённых из журнала
├── phone/
│   ├── CallSessionStore.kt    состояние текущего звонка (SharedPreferences)
│   ├── PhoneStateReceiver.kt  PHONE_STATE + отложенная финализация
│   ├── CallLogReader.kt       добор номера и callLogId, сверка с журналом
│   └── CallFinalizer.kt       создание/склейка записи
├── widget/
│   ├── WidgetRefresh.kt       push-обновление обоих виджетов
│   ├── OrdersWidgetProvider.kt       1x1, только иконка
│   └── UnprocessedWidgetProvider.kt  2x2, счётчик + цвет
└── ui/
    ├── theme/                 Material3-тема
    ├── onboarding/            разрешения, аккаунт, батарея
    ├── list/                  экран необработанных
    ├── settings/              рабочее время
    └── form/                  мини-форма заказа
```

## Этапы

### Этап 1. Каркас проекта

- Gradle wrapper, version catalog, `app/build.gradle.kts`, манифест со всеми
  разрешениями, activity, receiver и обоими провайдерами виджетов.
- Ресурсы: тема, строки, иконка, layout-ы и info-файлы виджетов, правила backup.
- Компилируемые заглушки `App`, `AppGraph`, `MainActivity`, `FormActivity`,
  `PhoneStateReceiver`, `WidgetRefresh`, провайдеры виджетов.
- Критерий: `gradlew assembleDebug` → BUILD SUCCESSFUL.

### Этап 2. Данные и домен

- Room: `CallRecordEntity`, `BlacklistEntity`, DAO, `AppDatabase`.
- DataStore: рабочее время (с–до, дни), выбранный аккаунт контактов, онбординг.
- `CallRepository`: создание/склейка, «раньше», закрытие всех, счётчик,
  сверка с журналом, подтягивание имён из контактов.
- `ContactRepository`: поиск по нормализованному номеру, создание контакта в
  выбранном аккаунте, отказ при дубликате.
- Чистая логика: нормализация, рабочее время, правила записи, blacklist,
  reconciler. JVM-тесты на всё перечисленное.
- Критерий: `assembleDebug` + `testDebugUnitTest` зелёные.

### Этап 3. Перехват звонков и виджеты

- `PhoneStateReceiver`: RINGING → OFFHOOK → IDLE, отличие входящего от
  исходящего, несколько SIM, `goAsync()`, добор из CallLog.
- Запись создаётся сразу по состоянию звонка (номер виден оператору без
  задержки). Если номер в `PHONE_STATE` пустой, журнал опрашивается внутри
  работы ресивера (до 5,5 с) — после `finish()` процесс может быть убит.
  `callLogId` дозаполняется в `CallSync` при открытии списка.
- `CallFinalizer`: правила «отвечен всегда / пропущенный только в рабочее
  время», склейка необработанных, blacklist ONCE/PERMANENT,
  скрытый номер, `callLogId`.
- Сверка с журналом перед показом списка и обновлением виджета.
- Виджеты: 1x1 иконка → `FormActivity`; 2x2 счётчик + цвет → `MainActivity`.
- Критерий: сборка зелёная, логика покрыта тестами там, где не нужен Android.

### Этап 4. Экраны

- Онбординг: разрешения, исключение из оптимизации батареи, выбор
  Google-аккаунта (fallback на локальную книгу), сохранение флага.
- Список необработанных: контакт, бутыли, адрес, «раньше», время, кнопка
  «Заказы обработаны», шестерёнка.
- Настройки: «с–до» и чекбоксы дней недели.
- Мини-форма: бутыли, адрес (подставляется именем контакта, если оператор
  не ввёл своё), имя (только неизвестный номер), «не клиент»
  (постоянно/временно), «Предыдущий звонок» в пределах сегодняшнего дня,
  «Сохранить и закрыть» (не закрывает запись как обработанную).
- Критерий: сборка зелёная, навигация и сохранение работают через репозиторий.

### Этап 5. Проверка по Google Таблице

- `domain/SheetCsv` — разбор CSV-экспорта вкладки (кавычки, запятые,
  переводы строк).
- `domain/SheetAddresses` — три блока адресов (`B`/`I`/`Q`) и количество
  в соседней ячейке (`C`/`J`/`R`).
- `domain/AddressNormalizer` — нормализация адресов и извлечение количества
  из свободного текста.
- `domain/SheetOrderMatcher` — точные и нечёткие совпадения, одна строка
  на одну запись, учёт израсходованных строк, синонимов и отказов оператора.
- `data/SheetsClient` — скачивание CSV вкладок за сегодня и завтра
  (`дд.ММ.гг`) по HTTPS, без новых зависимостей. Вкладка читается по `gid`,
  который берётся из списка вкладок документа: на несуществующее имя `gviz`
  отвечает данными первого листа, то есть чужим днём. Отсутствующая вкладка
  (например, завтрашней ещё нет) пропускается.
- `data/SheetCheckRepository` — сверка, закрытие записей с
  `closeReason = SHEET`, сохранение синонимов.
- Room: таблица `address_aliases` (синоним или отказ), колонка
  `call_records.sheetMatchKey`, миграции 1→2 и 2→3. Миграция 1→2 пересоздаёт
  `call_records` вместо `ALTER TABLE … DROP COLUMN`: этот синтаксис есть
  только в SQLite 3.35+, а на Android 11 доступен SQLite 3.28.
- UI: кнопка «Проверить таблицу», прогресс, итог проверки и подтверждение
  неполных совпадений.
- Критерий: сборка и unit-тесты зелёные; проверка не падает без сети.

### Этап 6. Проверка

- `assembleDebug`, `testDebugUnitTest`, `lintDebug`.
- Ручная проверка соответствия `doc/idea.md` по чек-листу.
- Итоговый отчёт: что реализовано, что осталось за рамками MVP.

## Контракты

- `PhoneNormalizer.normalize(raw: String?): String?` — `8XXXXXXXXXX`, `7XXXXXXXXXX`,
  `9XXXXXXXXX` → `+7XXXXXXXXXX`; скрытый/пустой → `null`.
- `WorkSchedule(startMinute, endMinute, days)` + `isWorkingAt(epochMillis, zone)`.
- `CallRules.plan(answered, working, blacklisted, hasOpenRecord, existingSilent,
  hiddenNumber): RegistrationPlan` — `Ignore` | `Merge(silent)` | `Create(silent)`.
- `BlacklistPolicy.suppressesCall(kind)`, `consumesAfterCall(kind)`.
- `CallLogReconciler.idsToClose(open, existingCallLogIds)`.
- `ContactMatcher.findName(number, contacts)`, `shouldCreate(name, existingName)`.
- `CallRepository.registerIncoming(numberRaw, answered, incomingAt, callLogId): Long?`,
  `openItems()`, `observeOpenItems()`, `latestOpen()`, `recordById(id)`,
  `previousOpenToday(current)`, `saveOrder(recordId, bottles, address,
  nameInput, skip: SkipUpdate): Boolean`, `markAllProcessed()`, `unreadCount()`,
  `reconcileWithCallLog(ids)`, `refreshNamesFromContacts()`.
- `SettingsRepository.schedule`, `setSchedule(...)`, `contactAccount`,
  `setContactAccount(...)`, `onboardingDone`, `setOnboardingDone()`.
- `ContactRepository.findName(number)`, `createContactIfAbsent(name, number)`.
- `SheetsClient.fetchTodayAndTomorrow(today): SheetFetchResult`.
- `SheetCheckRepository.check()`, `apply(tabs): SheetCheckOutcome`,
  `confirmAlias(aliasAddress, canonicalAddress)`.
- `WidgetRefresh.updateAll(context)`.

## Критерии готовности MVP

1. Входящий звонок создаёт запись после завершения; исходящие игнорируются.
2. Пропущенный вне рабочего времени не создаёт запись; отвеченный создаёт
   запись всегда.
3. Повторный звонок до обработки обновляет ту же строку, введённые данные
   сохраняются.
4. «Не клиент» постоянно/временно работает по правилам `idea.md`.
5. Неизвестный номер: имя вводится в форме и создаёт контакт без дубликата.
6. Список показывает необработанные, «раньше»; кнопка закрывает
   все.
7. Удалённый из журнала звонок закрывается как `DELETED_FROM_CALLLOG`.
8. Виджеты: 1x1 открывает форму последнего звонка, 2x2 показывает счётчик и
   цвет; оба обновляются push-ом.
9. Настройки рабочего времени сохраняются и применяются к новым звонкам.
10. `assembleDebug` и `testDebugUnitTest` проходят.
11. «Проверить таблицу» закрывает записи, чьи адреса есть в таблице за
    сегодня или завтра; повторная проверка не закрывает по той же строке
    ещё одну запись; неполное совпадение подтверждается оператором и
    запоминается как синоним, либо отклоняется и больше не предлагается.

## Риски

- AGP 9.x и Kotlin 2.2.x — свежий стек; при проблемах откат на AGP 8.13 +
  Gradle 8.13. Версии фиксируются по maven-metadata, а не по памяти.
- `PHONE_STATE` на Android 12+ может не отдавать номер без `READ_CALL_LOG`;
  добор из журнала обязателен.
- `goAsync()` ограничен ~10 секундами — задержка финализации 4 с.
- Проверка на реальном устройстве недоступна в этой среде: ограничиваемся
  сборкой, unit-тестами и lint.

## Состояние

Этапы 1–6 выполнены. Сборка и тесты: `assembleDebug`, `testDebugUnitTest`
(136 тестов), `lintDebug` — без ошибок. Проверено на эмуляторе: правила
звонков, склейка, «не клиент», форма и контакт, «Заказы обработаны», сверка
с журналом, настройки, оба виджета.

Покрыто JVM-тестами: нормализация номеров и адресов, рабочее время, правила
создания/склейки записей, «не клиент» (оба режима), скрытый номер,
сверка с журналом, создание и склейка записей в репозитории, отказ от
дубликата контакта, разбор CSV таблицы и сопоставление адресов на реальной
выгрузке.

Не проверено на реальном устройстве: перехват живого звонка, обновление
виджетов на рабочем лаунчере, «Проверить таблицу» с реальной сетью.

Найдено и исправлено при проверке:

- action виджета был `android.intent.action.APPWIDGET_UPDATE` вместо
  `android.appwidget.action.APPWIDGET_UPDATE` — виджеты не регистрировались;
- миграция 1→2 не убирала колонку `durationSec`, Room не открывал базу;
  пересоздание таблицы сделано без `DROP COLUMN` (нет в SQLite 3.28
  на Android 11);
- `gviz` на несуществующее имя вкладки отдаёт первый лист — вкладки
  читаются по `gid` из списка вкладок документа;
- «не клиент» снимался при каждом сохранении формы, даже если оператор
  не трогал переключатель;
- запись о звонке создавалась только после ожидания журнала (4–6 с), из-за
  чего форма сразу после звонка открывалась без номера. Теперь запись
  создаётся мгновенно, `callLogId` привязывается позже;
- результат проверки таблицы ничего не объяснял при нуле закрытых записей:
  теперь видно, какие вкладки прочитаны, сколько в таблице адресов и почему
  совпадений нет;
- похожие адреса искались только по совпадению с начала, поэтому
  «Город женский» не находился в «Континент город женский». Теперь ищется
  любая непрерывная часть адреса, а оператор может подтвердить пару или
  отказаться от неё.
