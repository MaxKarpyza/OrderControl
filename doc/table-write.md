# Запись заказов в Google Таблицу

Приложение отправляет заказы в таблицу через Apps Script Web App. Скрипт живёт
рядом с таблицей и пишет строки от имени владельца — Google-вход в приложении не
нужен.

## Текущее развёртывание (14.11.2026)

- Проект Apps Script: **OrderControl таблица** (автономный, не связан с чужими
  скриптами таблицы).
- URL веб-приложения:
  `https://script.google.com/macros/s/AKfycbx--hHP_xxnAL-_Hth_RPK8Rt7n1COT6SlpdBIi8qolJVcuWbhKzWKt1fUp_YOMwkXp-A/exec`
- Доступ: «Все», запуск от имени владельца. Токена нет — URL и есть пароль.
- Проверено: `GET` возвращает `{"ok":true,"service":"ordercontrol","tabs":[...]}`,
  `POST` с несуществующей вкладкой возвращает `tab_not_found`.

URL зашит в приложении константой: менять — только новой сборкой.

## Что делает скрипт

Пишет заказы в **центральный блок** вкладки: адрес в колонку `I`, количество
бутылей в `J`, комментарий в `L`. Колонку `K` (цена, формула) и район `M` не
трогает — их заполняют люди.

Строки добавляются после последней заполненной строки блока с отступом в
5 пустых строк. Внутри одной пачки строки идут подряд.

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
    `{"ok":true,"service":"ordercontrol",...}`. Если просит войти — доступ
    выставлен не «Все».

Присланный URL попадает в код приложения, менять его потом можно только новой
сборкой.

## Если позже нужно поменять скрипт

Не создавайте **новое** развёртывание — URL сменится. Вместо этого:
**Развернуть → Управление развёртываниями → карандаш → Версия: Новая версия →
Развернуть**. URL остаётся прежним.

## Что принимает скрипт

`POST` с JSON:

```json
{
  "tab": "14.11.26",
  "orders": [
    {
      "address": "Малахова 1-62",
      "bottles": "2",
      "comment": "звони тел +79860020200"
    },
    { "address": "Козыря 21-77", "bottles": "1", "comment": "" }
  ]
}
```

- `tab` — имя вкладки (`дд.ММ.гг`), её выбирает приложение по дню доставки;
- `bottles` — число, но допускается и текст (у старых записей количество
  хранится строкой);
- `comment` — уже собранный текст, включая телефон, если оператор его добавил.

Ответ:

```json
{
  "ok": true,
  "tab": "14.11.26",
  "written": [{ "address": "Малахова 1-62", "row": 16 }]
}
```

Ошибки: `no_body`, `bad_json`, `no_tab`, `tab_not_found`, `busy`.

Вкладку скрипт **не создаёт**: если её нет — возвращает `tab_not_found`, и
приложение оставляет заказы в необработанных.

## Код

```javascript
var SPREADSHEET_ID = "1HDK5_dbN52XIE3XScBhVJtvtJvgbEMwH8U6Trozjmqg";

var COL_ADDRESS = 9; // I
var COL_BOTTLES = 10; // J
var COL_COMMENT = 12; // L
var GAP_ROWS = 5;
var MAX_ROWS = 2000;

function respond(payload) {
  return ContentService.createTextOutput(JSON.stringify(payload)).setMimeType(
    ContentService.MimeType.JSON,
  );
}

function doGet() {
  var tabs = SpreadsheetApp.openById(SPREADSHEET_ID)
    .getSheets()
    .map(function (s) {
      return s.getName();
    });
  return respond({ ok: true, service: "ordercontrol", tabs: tabs });
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
    var written = [];

    for (var i = 0; i < orders.length; i++) {
      var order = orders[i] || {};
      var address = String(order.address || "").trim();
      if (!address) {
        continue;
      }
      values.push([address, bottlesValue(order.bottles)]);
      comments.push([String(order.comment || "").trim()]);
      written.push({ address: address, row: firstRow + values.length - 1 });
    }

    if (values.length > 0) {
      sheet.getRange(firstRow, COL_ADDRESS, values.length, 2).setValues(values);
      sheet
        .getRange(firstRow, COL_COMMENT, comments.length, 1)
        .setValues(comments);
    }

    return respond({ ok: true, tab: tabName, written: written });
  } finally {
    lock.releaseLock();
  }
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
