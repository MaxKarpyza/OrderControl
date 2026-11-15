param(
    [string]$Note = "",
    [switch]$NoPush,
    [int]$Bump = 1
)

# Поднимает версию, коммитит, ставит тег v0.0.NNN и пушит.
# Пуш тега запускает GitHub Actions: сборка, подпись и публикация релиза.
# -Bump задаёт шаг версии: по умолчанию +1, можно пропустить версии.

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$buildFile = Join-Path $root "app\build.gradle.kts"
$versionsFile = Join-Path $root "doc\versions.md"

$utf8 = New-Object System.Text.UTF8Encoding($false)
$text = [IO.File]::ReadAllText($buildFile)

$codeMatch = [regex]::Match($text, 'versionCode = (\d+)')
if (-not $codeMatch.Success) { throw "versionCode не найден в app/build.gradle.kts" }
if ($Bump -lt 1) { throw "Bump должен быть не меньше 1" }

$newCode = [int]$codeMatch.Groups[1].Value + $Bump
$newName = "0.0.$newCode"

$text = [regex]::Replace($text, 'versionCode = \d+', "versionCode = $newCode")
$text = [regex]::Replace($text, 'versionName = "[^"]+"', "versionName = `"$newName`"")
[IO.File]::WriteAllText($buildFile, $text, $utf8)

if ($Note) {
    $row = "| $newName | $Note |"
    $doc = [IO.File]::ReadAllText($versionsFile).TrimEnd()
    [IO.File]::WriteAllText($versionsFile, "$doc`r`n$row`r`n", $utf8)
}

git -C $root add -A
git -C $root commit -m "release $newName"
git -C $root tag "v$newName"

if ($NoPush) {
    Write-Host "Версия $newName подготовлена. Запушите: git push; git push --tags"
} else {
    git -C $root push
    git -C $root push --tags
    Write-Host "Тег v$newName отправлен. Сборка и релиз: https://github.com/MaxKarpyza/OrderControl/actions"
}
