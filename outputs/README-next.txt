Geometry Dash внутри Minecraft — использование

Текущая установка: Minecraft bridge 0.3.8 и GD bridge 0.4.0, 2026-10-03 11:31:56. Квитанция outputs/visual-batch-install.json содержит SHA256 и резервную копию. Minecraft SHA256: 610E203C194B7C8125C198CFFD4AF0C39F6EEA49EE185E07B8AEB808FF02FC2A. GD SHA256: 58BA6B81DAA5F11133F32118CE41D28C37E5951169E2FCDFB2689640676F8121. Старые пакеты bridge/gd не устанавливайте.

По умолчанию visualStyle=native: оригинальные GD sprites, UV, цвета и trails, без объёмных замен орбов, падов, порталов, шипов и иконки. Native blendSource/blendDestination сохраняются; GL_ONE использует premultiplied PNG. Minecraft показывает собственные твёрдые блоки и окружение, GD считает настоящую физику, столкновения, смерть и взаимодействия. Экспериментальный режим volume включается отдельно; его соответствие выбранному образцу пока не подтверждено.

Подтверждено: Auto XO 58835426 проходит первые 20 секунд с нулём смертей при diagnosticNoclip=false. Полное прохождение и финальный баннер ещё не проверены. Меню паузы Minecraft удерживает GD simulation и FMOD; телеметрия подтверждает причины паузы и bridgeMusicPaused. Пауза редактора и меню независимы. Общий runtimeVerified новой установки пока false: качество native-default изображения сейчас проверяется отдельно.

Запуск: Launch-Minecraft.lnk, затем GD через Steam, либо Start-Bridge.ps1. Профиль Prism gdbridge, отдельный мир GDBridge-XO; localhost TCP18471; 30 GD units = 1 Minecraft-блок. Space / Up / левая кнопка мыши — native нажатие/отпускание, R — restart. Обычное прохождение показывает native процент; фактический GD levelComplete включает баннер. Диагностическое завершение подписывается как проверочный проход.

Команды PowerShell 7 выполняются из outputs:

  .\Import-GDLevel.ps1 -Path (Join-Path $PWD 'bridge\levels\xo-auto-popular-source.json')
  .\Import-GDLevel.ps1 -Path (Join-Path $PWD 'bridge\levels\xo-easy-jukaras-source.json')

Первый источник — Auto XO 58835426; второй — ручной Easy XO 58898913. Также сохранены xo-easy-dragon-source.json, xo-auto-realistic-source.json и xo-original-source.json. Это публичные GD-уровни, не подтверждённая точная карта видео Nasgubb. По умолчанию помощник оставляет полный исходный native уровень и импортирует только маркеры первой области, без F6/export. Источник JSON <=64MiB, compressed levelString <=4MiB, распакованный уровень <=20MiB.

F7 — Creative и пауза GD. Редактор ограничен z=0, x=0..512, y=67..100. F6 экспортирует до20000 объектов во временный GD-вариант: физические записи внутри X-окна заменяются маркерами; native продолжение за окном и нефизические записи сохраняются. Объекты внутри того же X-окна, не представленные ограниченным Y-импортом, могут потеряться. F6 не является полной конвертацией XO без потерь. Import-GDLevel.ps1 -EditableMinecraft явно запрашивает этот экспорт. Пользовательские GD-сохранения не перезаписываются. Маркеры скрыты на Minecraft-клиенте в play-mode и остаются на сервере для F7.

Основной художественный ориентир — Scene-Target-Selected.png: солнечный лес и близкая боковая камера; это концепт, не runtime-кадр. Порядок текущих сцен: cave, library, Nether, forest в первых 512 блоках. multi-location-layout.json применяется отдельно; cave-height-expansion-layout.json после него поднимает крышу начальной пещеры до Y89..90. Строго ограниченный terrainFloor перекрашивает пол Y66 в камень; авторские маркеры Y67+ остаются нетронутыми. selected-forest-layout.json сохранён как отдельный вариант. Декорации не задают фиксированные игровые объекты; главы не являются полной картой Nasgubb.

Iris1.7.6 / Sodium0.5.12-beta.2 / Complementary Reimagined r5.9.3. Активная поправка CLOUD_STYLE_DEFINE=1; вариант Vanilla=50 был отвергнут из-за чёрных облаков. Активные параметры — shaderpacks/ComplementaryReimagined_r5.9.3.zip.txt внутри профиля, камера — config/gdbridge/camera.json. Близкая камера: distance8, offsetX3.6, height2.3, yaw178, pitch3.5. Горячая смена установленного пакета и режима изображения:

  .\Control-Bridge.ps1 -Action shaders -PayloadJson '{"pack":"ComplementaryReimagined_r5.9.3.zip","enabled":true}'
  .\Control-Bridge.ps1 -Action visual-style -PayloadJson '{"mode":"native"}'

Режим volume — отдельный эксперимент через {"mode":"volume"}; выбранный режим сохраняется в config/gdbridge/visual-style.json.

Запись только native framebuffer Minecraft, без Desktop capture, фокуса или ввода ОС:

  .\Control-Bridge.ps1 -Action record -PayloadJson '{"durationSeconds":12,"fps":20,"resumeForRecording":true}'
  .\Encode-BridgeRecording.ps1 -RecordingDirectory 'C:\...\outputs\bridge\recordings\recording-...'

Длительность 1..20 секунд, fps1..20, максимум400 PNG. resumeForRecording по умолчанию false; true временно закрывает только Minecraft GameMenuScreen, затем возвращает прежнее меню, если мир тот же и пользователь не менял экран. Recording.json хранит фактические timestamps, actualFps, native координаты и режим проверки. Кодировщик ждёт завершения PNG, сохраняет реальные интервалы кадров в MP4. Полный обзор: node .\Build-LevelOverview.cjs — читает свежий blueprint и создаёт Level-Overview.html.

Своя музыка:

  .\Add-Music.ps1 -Path 'C:\Music\track.mp3' -Offset 2.5 -Volume 0.8 -Restart
  .\Add-Music.ps1 -Disable

MP3/OGG/WAV <=256MiB копируются в outputs/music; Offset — секунды, Volume0..1. Применение при следующем native music start/restart через GD/FMOD; restart при паузе сохраняет активные причины паузы. Без Path помощник открывает выбор файла; -PrepareOnly готовит файл без применения.

Read-BridgeStatus.ps1 и Inspect-MinecraftProfile.ps1 читают состояние. Control-Bridge.ps1 использует nonce; квитанция подтверждает приём, серверная операция может завершиться позже. Скриншоты — .minecraft/screenshots; записи — outputs/bridge/recordings. staleAgeMs — возраст состояния, не полная задержка кнопка→изображение. Диагностический noclip выключен по умолчанию, ограничен таймером и явно обозначается; его результаты не подтверждают нормальное прохождение.