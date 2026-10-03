GD Minecraft Bridge0.3.8 — Fabric / Minecraft1.20.1

Использование: outputs/README.txt. Ограничения импорта/F7/F6: README-Roundtrip.md.
Текущая связка: MC0.3.8 / GD0.4.0, установлена 2026-10-03 11:31:56.
dist/gd-minecraft-bridge-0.3.8.jar SHA256: 610E203C194B7C8125C198CFFD4AF0C39F6EEA49EE185E07B8AEB808FF02FC2A.

По умолчанию оригинальные native GD sprites/UV/RGBA/blend и trails без экструзии. Minecraft solids и окружение остаются блоками. Optional volume — эксперимент, визуально не подтверждённый.
GD считает физику; localhost TCP18471,30 GD units=1 блок, мир GDBridge-XO. Статус: config/gdbridge/status.json, включая visualStyle. Меню и build-mode — независимые причины паузы simulation/FMOD. Native процент и фактический levelComplete передаются в MC.
Native framebuffer recorder до20fps/20s/400PNG; явный resumeForRecording закрывает/условно возвращает только меню Minecraft. Desktop API не используется.

Сборка и validateAccessWidener прошли. Первые20s Auto58835426 проверены без смертей/noclip; полный финиш и качество новой native-default картинки ещё проверяются. Квитанция установки runtimeVerified=false не является подтверждением общего runtime QA.

Сборка: JDK17, Gradle8.8, Fabric Loom1.7.4. Windows ZipFS использует временный subst P: на каталог проекта.
JAVA_HOME=P:/work/toolchains/jdk-17.0.20.1+1; GRADLE_USER_HOME=P:/work/gradle-cache.
gradle -p P:/outputs/bridge/minecraft build --offline --no-daemon --project-cache-dir P:/work/mc-project-cache
Результат: work/minecraft-build/libs; подготовленные jar: dist. Установка — отдельным установщиком при закрытых играх.