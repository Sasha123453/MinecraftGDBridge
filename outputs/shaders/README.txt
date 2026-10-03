Шейдеры для Minecraft 1.20.1 Fabric — установлены, рендер пока не проверен

Iris, Sodium и Complementary Reimagined получаются из официальных проектов Modrinth.
Все три файла скачаны в staged; SHA1 и размер каждого совпадают с официальным Modrinth API.
manifest.json сохраняет версии и источники.
Установлены при закрытых играх 2026-10-02 23:40. Запуск игр установщиком не выполнялся.
Проверены fabric.mod.json: Iris 1.7.6 поддерживает Minecraft 1.20.1 и Sodium 0.5.x;
Sodium 0.5.12-beta.2 выбран по точной обязательной зависимости официальной версии Iris.
Требования Fabric Loader покрывает профиль 0.16.10; Fabric API уже установлен.

В config/iris.properties выбран ComplementaryReimagined_r5.9.3.zip, enableShaders=true.
Режим MEDIUM: тени128, естественный стиль1, motion blur/world blur/TAA выключены.
Следующий шаг: один общий запуск с новым Minecraft bridge, затем реальный снимок Minecraft,
проверка original GD player/trails, frame age/FPS и F6/F7. Снимок можно сохранить встроенным F2
и открыть файл из .minecraft/screenshots; Inspect-MinecraftProfile.ps1 покажет последний путь.
Install-Shaders.ps1 сохраняет резервные конфиги/моды и не запускает игру; -Disabled оставляет
Iris/Sodium установленными, но выключает шейдер при следующем запуске.
Проверка установленных SHA1: verification.json. Пассивный снимок профиля: installed-profile.json.

Официальные источники:
https://modrinth.com/mod/iris
https://modrinth.com/mod/sodium
https://modrinth.com/shader/complementary-reimagined
https://github.com/ComplementaryDevelopment/ComplementaryReimagined
