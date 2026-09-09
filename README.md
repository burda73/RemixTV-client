# RemixTV Client

Android TV клиент для системы управления видеоплейлистами [RemixTV](https://github.com/burda73/RemixTV-server).

## Описание

RemixTV — это приложение для Android TV (и обычных Android-устройств), которое воспроизводит видеоплейлисты, синхронизируемые с сервера. Клиент загружает видео в кэш для офлайн-воспроизведения и поддерживает автоматический запуск при старте системы.

## Возможности

- Воспроизведение видеоплейлистов с сервера
- Синхронизация плейлистов по расписанию или вручную
- Кэширование видео для офлайн-доступа
- Автозапуск при загрузке Android
- Панель отладки на экране
- Настраиваемая заставка ожидания
- Foreground-сервис для фонового воспроизведения
- Поддержка D-pad навигации (Android TV)
- Настройки: URL сервера, имя проигрывателя, лимит кэша, интервал синхронизации

## Требования

- Android 5.0+ (API 21)
- Android TV или обычное Android-устройство
- Сервер [RemixTV-server](https://github.com/burda73/RemixTV-server)

## Сборка

```bash
git clone https://github.com/burda73/RemixTV-client.git
cd RemixTV-client
```

Откройте проект в Android Studio и выполните сборку, либо:

```bash
./gradlew assembleDebug
```

APK будет位于 `app/build/outputs/apk/debug/`

## Настройка

1. Установите APK на Android TV / Android-устройство
2. В настройках приложения укажите URL сервера RemixTV
3. Задайте имя проигрывателя (уникальное для каждого устройства)
4. При необходимости настройте интервал синхронизации и лимит кэша

### Заставка

Поместите файл `splash.png` или `splash.jpg` в каталог `app/src/main/assets/remixtv/` и пересоберите APK. Рекомендуемое разрешение — 1920×1080 или 3840×2160.

## Архитектура

- **data** — Repository, DAO, ApiService, модели данных
- **domain** — Use cases (SyncPlaylist, DownloadVideo)
- **presentation** — Activity, Fragments, ViewModels
- **service** — Foreground-сервис воспроизведения
- **receiver** — Boot-автозапуск
- **utils** — CacheManager, NetworkUtils, PreferencesManager

## Стек технологий

| Компонент | Библиотека |
|---|---|
| Видео | Media3 / ExoPlayer 1.4.1 |
| Сеть | Retrofit 2.11 + OkHttp 4.12 |
| БД | Room 2.6.1 |
| Корутины | Kotlinx Coroutines 1.8.1 |
| Архитектура | MVVM + Clean Architecture |
| TV UI | Leanback 1.0.0 |

## Серверная часть

Сервер: https://github.com/burda73/RemixTV-server

## Лицензия

MIT
