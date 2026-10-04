<div align="center">

# ✦ OpenPass

**Современная, быстрая и криптографически защищённая система авторизации нового поколения для Minecraft**

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3%20%E2%80%94%201.21-3b82f6?style=for-the-badge&logo=minecraft&logoColor=white)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25%20%7C%2021-3b82f6?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net)
[![Platforms](https://img.shields.io/badge/Platforms-Paper%20%7C%20Purpur%20%7C%20Folia%20%7C%20Velocity%20%7C%20Bungee%20%7C%20Sponge-3b82f6?style=for-the-badge)](https://github.com)
[![License](https://img.shields.io/badge/License-MIT-3b82f6?style=for-the-badge)](LICENSE)

</div>

---

## ✦ О проекте

**OpenPass** — модульный плагин авторизации и защиты учётных записей для серверов Minecraft, изначально спроектированный под архитектуру **Minecraft 26.3** с сохранением полной обратной совместимости вплоть до **1.21**.

Плагин лишён устаревших архитектурных компромиссов, использует современный криптографический стандарт **Argon2id**, встроенный движок **AntiBot** и поддерживает многопоточные ядра (**Folia**), прокси-серверы (**Velocity, Waterfall, BungeeCord**) и серверные платформы (**Paper, Purpur, Spigot, Bukkit, Sponge**).

---

## ✦ Ключевые возможности

* **Криптографическая защита Argon2id**: Защита хэшей паролей алгоритмом Argon2id через BouncyCastle. Защита от перебора на GPU/ASIC и сверка с постоянным временем (`constant-time`) против тайминг-атак.
* **Встроенный AntiBot Engine**:
  * Фильтрация шторма подключений (глобальный детектор атак и режим защиты).
  * Ограничение частоты подключений с одного IP адреса.
  * Блокировка шаблонных бот-ников и проверка регулярных выражений.
* **Прогрессивная защита от брутфорса**:
  * Автоматическая временная блокировка при серии неверных попыток ввода пароля.
  * Прогрессивный интервал наказания: **15 мин** → **30 мин** → **60 мин** с множителем прогрессии.
  * Точное информирование нарушителя о времени до разблокировки.
* **Сессии по умолчанию**:
  * Игрокам не требуется вводить пароль при повторном входе с того же IP в течение 24 часов (настраивается).
* **Поддержка лицензий Mojang (`/premium`)**:
  * Проверка аккаунтов через официальный Mojang API в реальном времени.
  * Бесшовный автовход для владельцев лицензии без необходимости ввода пароля.
* **Zero-Lag Void Limbo**:
  * Зависание неавторизованного игрока в звёздной пустоте Энда с нулевой гравитацией (без лишних блоков и порталов).
  * По умолчанию телепортация на спавн отключена — игрок остаётся на месте выхода (полностью настраивается в конфиге).
  * Полная изоляция: игроки в ожидании входа не видят друг друга, не могут наносить урон, двигаться или открывать инвентарь.
* **Элегантный синий дизайн**:
  * Чистая палитра `#3b82f6` для ключевых акцентов без раздражающего жирного (`bold`) или курсивного (`italic`) текста.
  * Аккуратные UTF-8 иконки (`✦`, `✔`, `✖`, `🔒`, `🔑`, `🛡`, `⚡`).

---

## ✦ Матрица платформ

| Платформа | Поддерживаемые ядра | Модуль |
| :--- | :--- | :--- |
| **Paper-совместимые** | Paper, Purpur, Folia, Spigot, Bukkit (1.21 – 26.3) | `openpass-bukkit` |
| **Bungee-прокси** | BungeeCord, Waterfall | `openpass-bungee` |
| **Velocity-прокси** | Velocity 3.x | `openpass-velocity` |
| **Sponge** | SpongeVanilla, SpongeForge (SpongeAPI 8+) | `openpass-sponge` |

---

## ✦ Команды и права

| Команда | Алиасы | Описание | Право |
| :--- | :--- | :--- | :--- |
| `/register <пароль> <повтор>` | `/reg` | Регистрация нового аккаунта | `openpass.use` |
| `/login <пароль>` | `/l` | Вход в систему | `openpass.use` |
| `/changepassword <старый> <новый>` | `/changepass`, `/cp` | Смена пароля | `openpass.use` |
| `/premium` | `/license` | Управление лицензионным режимом | `openpass.use` |
| `/openpass reload` | `/opass reload` | Перезагрузка конфигураций | `openpass.admin` |
| `/openpass unban <цель>` | `/opass unban` | Снятие временной блокировки брутфорса | `openpass.admin` |

---

## ✦ Конфигурация (`config.yml`)

```yaml
database:
  type: sqlite # sqlite или mysql
  mysql:
    host: localhost
    port: 3306
    database: openpass
    username: root
    password: ""
    pool-size: 10

security:
  timeout-seconds: 75
  min-password-length: 6
  max-password-length: 32
  max-failed-attempts: 5
  initial-ban-minutes: 15
  ban-multiplier: 2

session:
  enabled: true
  duration-hours: 24

limbo:
  enabled: false
  teleport-to-spawn-on-join: false
  world-name: auth_limbo
  spawn-x: 0.5
  spawn-y: 64.0
  spawn-z: 0.5
  spawn-yaw: 0.0
  spawn-pitch: 0.0

antibot:
  enabled: true
  max-joins-per-second: 8
  max-joins-per-ip: 3

license:
  mojang-api-check: true
```

---

## ✦ Сборка из исходников

Для сборки всех платформ проекта требуется установленный **JDK 25** и **Gradle**:

```bash
# Клонирование репозитория
git clone https://github.com/your-username/OpenPass.git
cd OpenPass

# Сборка всех модулей
gradle build
```

Собранные JAR-файлы будут доступны в соответствующих подпапках:
* `openpass-bukkit/build/libs/OpenPass-Paper-Spigot-Folia-1.0.0.jar`
* `openpass-bungee/build/libs/OpenPass-Bungee-Waterfall-1.0.0.jar`
* `openpass-velocity/build/libs/OpenPass-Velocity-1.0.0.jar`
* `openpass-sponge/build/libs/OpenPass-Sponge-1.0.0.jar`

---

## ✦ Лицензия

Проект распространяется под открытой лицензией [MIT](LICENSE).
