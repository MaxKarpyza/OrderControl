# OrderControl — заметки для агентов

## GitHub и релизы

- Репозиторий: https://github.com/MaxKarpyza/OrderControl — **публичный**.
- Ветка `main`. Тег `v0.0.NNN` всегда совпадает с `versionName` в `app/build.gradle.kts`.
- Пуш тега запускает `.github/workflows/release.yml`: сборка подписанного
  `assembleRelease`, публикация `OrderControl-0.0.NNN.apk` в GitHub Releases.
- Обычный пуш в `main` релиз не создаёт — только тег `v*`.
- В workflow сборка обёрнута в 3 попытки: GitHub и `services.gradle.org`
  периодически отдают 504/таймаут.
- `android-actions/setup-android` не использовать — падает на sdkmanager 16
  (`Failed to find package 'tools'`); SDK на раннере достаточно.

## Выпуск версии

```powershell
.\scripts\release.ps1 -Note "Что сделано"
```

Скрипт поднимает `versionCode` на 1, ставит `versionName = 0.0.NNN`, добавляет
строку в `doc/versions.md`, коммитит, ставит тег и пушит. CI собирает и
публикует релиз за ~3–4 минуты. Вручную версию не бампать.

## Подпись

- Ключ: `release.jks` в корне проекта, параметры — `keystore.properties`
  (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`).
- Оба файла в `.gitignore`. Резервная копия ключа и пароли:
  `C:\Users\Maxim\.ordercontrol-keys\`.
- В GitHub Actions секреты: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
  `KEY_ALIAS`, `KEY_PASSWORD`.
- Ключ менять нельзя и нельзя коммитить: репозиторий публичный, а с другим
  ключом обновления поверх установленного приложения не установятся.

## Автообновление в приложении

- `ru.ordercontrol.update.UpdateClient` — GitHub API `releases/latest`,
  сравнение `versionCode` из тега с `BuildConfig.VERSION_CODE`.
- `UpdateRepository` + `SettingsRepository` — автопроверка раз в 12 часов,
  скрытая пользователем версия.
- UI: `UpdateBanner` (плашка внизу, ничего не блокирует) в `RootScreen`,
  `UpdateSection` на экране настроек.
- Установка: APK качается в `cacheDir/updates`, отдаётся установщику через
  FileProvider `${applicationId}.updates`; нужен `REQUEST_INSTALL_PACKAGES`.

## Правила

- Релизы не удалять: на них ссылается автообновление у установленных версий.
- Теги не переписывать без необходимости; если тег исправлен, релиз нужно
  перезалить (иначе APK и тег разойдутся).
- В корне могут лежать распакованные файлы Kotlin-компилятора (`org/`,
  `META-INF/`, `kotlin/`, `messages/`, `misc/`, `kotlinManifest.properties`) —
  они в `.gitignore` и не относятся к проекту.
