# Запись заказов в Google Таблицу

Приложение отправляет заказы в таблицу через Apps Script Web App. Скрипт живёт
рядом с таблицей и пишет строки от имени владельца — Google-вход в приложении не
нужен.

Тот же скрипт обслуживает **общую историю операторов** — вкладку `history`
(см. [history-sync.md](history-sync.md)).

## Текущее развёртывание (18.11.2026)

- Проект Apps Script: **OrderControl таблица** (автономный, не связан с чужими
  скриптами таблицы).
- URL веб-приложения:
  `https://script.google.com/macros/s/AKfycbx--hHP_xxnAL-_Hth_RPK8Rt7n1COT6SlpdBIi8qolJVcuWbhKzWKt1fUp_YOMwkXp-A/exec`
- Доступ: «Все», запуск от имени владельца. Токена нет — URL и есть пароль.
- Проверено: `GET` возвращает
  `{"ok":true,"service":"ordercontrol","version":3,"historyTab":true,"tabs":[...]}`,
  `POST` с несуществующей вкладкой возвращает `tab_not_found`.
- `version` — версия **скрипта**, которую он сам сообщает в ответе. Не путать с
  номером версии проекта в «Управлении развертываниями»: там счётчик свой
  (после выкладки 18.11.2026 он равен 5).
  - `1` — район в таблицу не пишется;
  - `2` — район пишется в колонку `M`;
  - `3` — общая история операторов (`action: "sync"`, вкладка `history`).

URL зашит в приложении константой: менять — только новой сборкой.

## Что делает скрипт

**Заказы.** Пишет заказы в **центральный блок** вкладки: адрес в колонку `I`,
количество бутылей в `J`, комментарий в `L`, район в `M`. Колонку `K` (цена,
формула) не трогает — её считает формула. Строки добавляются после последней
заполненной строки блока с отступом в 5 пустых строк.

**История.** На `action: "sync"` принимает свои обработанные записи, отдаёт
всё, что накопилось у других операторов, и удаляет строки старше двух суток.

## Как развернуть (один раз, ~5 минут)

1. Откройте таблицу заказов в браузере на компьютере.
2. Меню **Расширения → Apps Script**.
3. Удалите заготовку в редакторе (`function myFunction() {}`) и вставьте код
   ниже целиком.
4. Сохраните: `Ctrl+S`. Имя проекта любое, например `OrderControl`.
5. Кнопка **Развернуть → Новое развёртывание**.
6. Тип — шестерёнка слева → **Веб-приложение**.
7. Поля:
   - **Описание** — `OrderControl`;
   - **Выполнять от имени** — **Я** (ваш аккаунт);
   - **У кого есть доступ** — **Все**.
8. **Развернуть** → Google попросит авторизоваться → «Дополнительные
   параметры» → «Перейти к проекту (небезопасно)» → **Разрешить**.
9. Скопируйте **URL веб-приложения** — длинная ссылка вида
   `https://script.google.com/macros/s/AKfycb.../exec`.
10. Проверка: откройте эту ссылку в браузере. Должен появиться ответ
    `{"ok":true,"service":"ordercontrol","version":3,...}`. Если просит войти —
    доступ выставлен не «Все».

Присланный URL попадает в код приложения, менять его потом можно только новой
сборкой.

## Если позже нужно поменять скрипт

Не создавайте **новое** развёртывание — URL сменится. Вместо этого:
**Развернуть → Управление развёртываниями → карандаш → Версия: Новая версия →
Развернуть**. URL остаётся прежним.

Так обновлялся скрипт 15.11.2026 (район в колонке `M`, версия 2) и 18.11.2026
(общая история операторов, версия 3). До обновления на версию 3 приложение
пишет заказы как обычно, но общая история не работает: вкладка «История»
показывает только свои записи и сообщает, что скрипт не обновлён.

## Что принимает скрипт

### Запись заказов

`POST` с JSON:

```json
{
  "tab": "14.11.26",
  "orders": [
    {
      "address": "Малахова 1-62",
      "bottles": "2",
      "comment": "звони тел +79860020200",
      "district": "Начальный центр",
      "otherDay": false
    }
  ]
}
```

- `tab` — имя вкладки (`дд.ММ.гг`), её выбирает приложение по дню доставки;
- `bottles` — число, но допускается и текст (у старых записей количество
  хранится строкой);
- `comment` — уже собранный текст, включая телефон, если оператор его добавил;
- `district` — район доставки в колонку `M`;
- `otherDay` — заказ на «другой день»: строку скрипт закрашивает цветом.

Ответ:

```json
{
  "ok": true,
  "tab": "14.11.26",
  "written": [{ "address": "Малахова 1-62", "row": 16 }]
}
```

Ошибки: `no_body`, `bad_json`, `no_tab`, `tab_not_found`, `busy`.

Вкладку заказов скрипт **не создаёт**: если её нет — возвращает
`tab_not_found`, и приложение оставляет заказы в необработанных.

### Обмен историей

`POST` с JSON:

```json
{
  "action": "sync",
  "operator": "Иванов Иван",
  "push": [
    {
      "id": "опер_37-A3F:12:1699999999999",
      "phone": "+79860020200",
      "address": "Малахова 1-62",
      "district": "Начальный центр",
      "bottles": "2",
      "comment": "звони",
      "processedAt": 1699999999999
    }
  ]
}
```

- `action: "sync"` — единственное значение, кроме отсутствующего (`write`);
- `operator` — подпись оператора: ФИО из настроек или `опер_NN-КОД`;
- `push` — свои записи, которые ещё не уходили; каждая с уникальным `id`,
  поэтому повторная отправка не создаёт дубль;
- `phone` — обязательное поле, без него строка пропускается;
- `processedAt` — миллисекунды.

Ответ:

```json
{
  "ok": true,
  "accepted": ["опер_37-A3F:12:1699999999999"],
  "history": [
    {
      "id": "опер_12-BCD:5:1699999999000",
      "author": "опер_12-BCD",
      "phone": "+79990000009",
      "address": "Козыря 21-77",
      "district": "Вишневский",
      "bottles": "1",
      "comment": "",
      "processedAt": 1699999999000
    }
  ]
}
```

- `accepted` — какие `id` таблица приняла (и новые, и уже записанные раньше);
- `history` — всё содержимое вкладки после очистки старых строк;
- при старой версии скрипта вместо этого приходит `{"ok":false,"error":"no_tab"}`.

Вкладку `history` скрипт создаёт сам при первом обмене.

## Код

```javascript
var SPREADSHEET_ID = "1HDK5_dbN52XIE3XScBhVJtvtJvgbEMwH8U6Trozjmqg";

var COL_ADDRESS = 9; // I
var COL_BOTTLES = 10; // J
var COL_COMMENT = 12; // L
var COL_DISTRICT = 13; // M
var GAP_ROWS = 5;
var MAX_ROWS = 2000;
var OTHER_DAY_COLOR = "#FFF2CC";

// Общая история операторов. Строки старше срока хранения удаляются при
// каждом обмене, чтобы таблица не разрасталась.
var HISTORY_TAB = "history";
var HISTORY_HEADER = [
  "id",
  "автор",
  "телефон",
  "адрес",
  "район",
  "бутыли",
  "комментарий",
  "обработано",
  "создано",
];
var HISTORY_COLUMNS = HISTORY_HEADER.length;
var HISTORY_RETENTION_DAYS = 2;

// Служебный список операторов, которые хоть раз забирали историю.
var OPERATORS_COL = HISTORY_COLUMNS + 2; // K
var OPERATORS_HEADER = ["оператор", "первый заход", "последний заход"];

function respond(payload) {
  return ContentService.createTextOutput(JSON.stringify(payload)).setMimeType(
    ContentService.MimeType.JSON,
  );
}

function doGet() {
  var ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  var tabs = ss.getSheets().map(function (s) {
    return s.getName();
  });
  return respond({
    ok: true,
    service: "ordercontrol",
    version: 3,
    historyTab: !!ss.getSheetByName(HISTORY_TAB),
    tabs: tabs,
  });
}

function doPost(e) {
  var lock = LockService.getScriptLock();
  if (!lock.tryLock(30000)) {
    return respond({ ok: false, error: "busy" });
  }
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return respond({ ok: false, error: "no_body" });
    }

    var body;
    try {
      body = JSON.parse(e.postData.contents);
    } catch (err) {
      return respond({ ok: false, error: "bad_json" });
    }

    var action = String(body.action || "write").trim();
    if (action === "sync") {
      return syncHistory(body);
    }
    if (action !== "write") {
      return respond({ ok: false, error: "unknown_action" });
    }
    return writeOrders(body);
  } finally {
    lock.releaseLock();
  }
}

function writeOrders(body) {
  var tabName = String(body.tab || "").trim();
  if (!tabName) {
    return respond({ ok: false, error: "no_tab" });
  }
  var orders = Array.isArray(body.orders) ? body.orders : [];

  var sheet = SpreadsheetApp.openById(SPREADSHEET_ID).getSheetByName(tabName);
  if (!sheet) {
    return respond({ ok: false, error: "tab_not_found", tab: tabName });
  }
  if (orders.length === 0) {
    return respond({ ok: true, tab: tabName, written: [] });
  }

  var last = lastFilledRow(sheet);
  var firstRow = last === 0 ? 1 : last + GAP_ROWS + 1;

  var values = [];
  var comments = [];
  var districts = [];
  var written = [];
  var otherDayRows = [];

  for (var i = 0; i < orders.length; i++) {
    var order = orders[i] || {};
    var address = String(order.address || "").trim();
    if (!address) {
      continue;
    }
    var row = firstRow + values.length;
    values.push([address, bottlesValue(order.bottles)]);
    comments.push([String(order.comment || "").trim()]);
    districts.push([String(order.district || "").trim()]);
    if (order.otherDay === true) {
      otherDayRows.push(row);
    }
    written.push({ address: address, row: row });
  }

  if (values.length > 0) {
    sheet.getRange(firstRow, COL_ADDRESS, values.length, 2).setValues(values);
    sheet
      .getRange(firstRow, COL_COMMENT, comments.length, 1)
      .setValues(comments);
    sheet
      .getRange(firstRow, COL_DISTRICT, districts.length, 1)
      .setValues(districts);
    for (var r = 0; r < otherDayRows.length; r++) {
      sheet
        .getRange(
          otherDayRows[r],
          COL_ADDRESS,
          1,
          COL_DISTRICT - COL_ADDRESS + 1,
        )
        .setBackground(OTHER_DAY_COLOR);
    }
  }

  return respond({ ok: true, tab: tabName, written: written });
}

function syncHistory(body) {
  var result = exchangeHistory(body);
  return respond({
    ok: true,
    accepted: result.accepted,
    history: result.rows,
  });
}

function historySheet() {
  var ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  var sheet = ss.getSheetByName(HISTORY_TAB);
  if (!sheet) {
    sheet = ss.insertSheet(HISTORY_TAB);
    sheet.setFrozenRows(1);
  }
  // Заголовки пишем и в только что созданную вкладку, и в пустую вкладку,
  // созданную руками: иначе колонки ничем не подписаны.
  if (String(sheet.getRange(1, 1).getValue() || "").trim() === "") {
    sheet.getRange(1, 1, 1, HISTORY_COLUMNS).setValues([HISTORY_HEADER]);
    sheet.getRange(1, 1, 1, HISTORY_COLUMNS).setFontWeight("bold");
  }
  return sheet;
}

function exchangeHistory(body) {
  var sheet = historySheet();
  var operator = String(body.operator || "").trim();
  var push = Array.isArray(body.push) ? body.push : [];
  var now = new Date();
  var cutoff = now.getTime() - HISTORY_RETENTION_DAYS * 24 * 60 * 60 * 1000;

  // Сначала читаем, что уже есть: заодно выбрасываем строки старше срока.
  var lastRow = Math.max(sheet.getLastRow(), 1);
  var existing =
    lastRow > 1
      ? sheet.getRange(2, 1, lastRow - 1, HISTORY_COLUMNS).getValues()
      : [];

  var ids = {};
  var kept = [];
  var rows = [];

  for (var i = 0; i < existing.length; i++) {
    var row = existing[i];
    var id = String(row[0] || "").trim();
    if (!id || ids[id]) {
      continue;
    }
    var createdAt = millis(row[8]);
    if (createdAt > 0 && createdAt < cutoff) {
      continue;
    }
    ids[id] = true;
    kept.push(row);
    rows.push(historyRow(row));
  }

  var accepted = [];
  for (var j = 0; j < push.length; j++) {
    var item = push[j] || {};
    var pushId = String(item.id || "").trim();
    var phone = String(item.phone || "").trim();
    if (!pushId || !phone) {
      continue;
    }
    accepted.push(pushId);
    if (ids[pushId]) {
      continue;
    }
    ids[pushId] = true;
    var record = [
      pushId,
      operator,
      phone,
      String(item.address || "").trim(),
      String(item.district || "").trim(),
      String(item.bottles || "").trim(),
      String(item.comment || "").trim(),
      new Date(Number(item.processedAt) || now.getTime()).toISOString(),
      now.toISOString(),
    ];
    kept.push(record);
    rows.push(historyRow(record));
  }

  if (lastRow > 1) {
    sheet.getRange(2, 1, lastRow - 1, HISTORY_COLUMNS).clearContent();
  }
  if (kept.length > 0) {
    sheet.getRange(2, 1, kept.length, HISTORY_COLUMNS).setValues(kept);
  }
  touchOperator(sheet, operator, now);

  return { accepted: accepted, rows: rows };
}

function historyRow(row) {
  return {
    id: String(row[0] || ""),
    author: String(row[1] || ""),
    phone: String(row[2] || ""),
    address: String(row[3] || ""),
    district: String(row[4] || ""),
    bottles: String(row[5] || ""),
    comment: String(row[6] || ""),
    processedAt: millis(row[7]),
  };
}

function millis(value) {
  if (value instanceof Date) {
    return value.getTime();
  }
  var text = value === undefined || value === null ? "" : String(value);
  var parsed = Date.parse(text);
  return isNaN(parsed) ? 0 : parsed;
}

function touchOperator(sheet, operator, now) {
  if (!operator) {
    return;
  }
  var height = Math.max(sheet.getLastRow(), 1);
  var values = sheet.getRange(1, OPERATORS_COL, height, 3).getValues();
  if (String(values[0][0] || "").trim() !== OPERATORS_HEADER[0]) {
    sheet.getRange(1, OPERATORS_COL, 1, 3).setValues([OPERATORS_HEADER]);
    sheet.getRange(1, OPERATORS_COL, 1, 3).setFontWeight("bold");
    values[0] = OPERATORS_HEADER;
  }

  var lastUsed = 1;
  for (var i = 1; i < values.length; i++) {
    var name = String(values[i][0] || "").trim();
    if (!name) {
      continue;
    }
    lastUsed = i + 1;
    if (name === operator) {
      sheet
        .getRange(i + 1, OPERATORS_COL + 2)
        .setValue(now.toISOString());
      return;
    }
  }

  sheet
    .getRange(lastUsed + 1, OPERATORS_COL, 1, 3)
    .setValues([[operator, now.toISOString(), now.toISOString()]]);
}

function lastFilledRow(sheet) {
  var height = Math.min(MAX_ROWS, Math.max(sheet.getLastRow(), 1));
  var addresses = sheet.getRange(1, COL_ADDRESS, height, 1).getValues();
  for (var i = addresses.length - 1; i >= 0; i--) {
    if (String(addresses[i][0]).trim() !== "") {
      return i + 1;
    }
  }
  return 0;
}

function bottlesValue(raw) {
  var text = String(raw === undefined || raw === null ? "" : raw).trim();
  if (text === "") {
    return "";
  }
  if (/^\d+$/.test(text)) {
    return Number(text);
  }
  return text;
}
```
