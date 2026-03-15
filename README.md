# Noda — Mind Map App

Приложение для визуального мышления с физической симуляцией узлов, жестами и заметками.

---

## Требования для сборки

| Инструмент | Версия |
|---|---|
| Android Studio | Panda 2025.3.1 или новее |
| JDK | 17 (Amazon Corretto или Eclipse Temurin) |
| Android SDK | API 34 или 35 |
| Gradle | 8.7+ (через Wrapper — скачивается автоматически) |
| Kotlin | 2.0.21 |

---

## Шаги для сборки

### 1. Клонировать или распаковать проект

```
Noda/
├── app/
│   ├── src/          ← исходный код
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
    └── wrapper/
        └── gradle-wrapper.properties
```

### 2. Открыть в Android Studio

**File → Open** → выбрать корневую папку проекта `Noda/`

### 3. Настроить JDK

**File → Settings → Build, Execution, Deployment → Build Tools → Gradle**

В поле **Gradle JDK** выбрать **JDK 17**.

Если JDK 17 нет в списке — нажать **Download JDK**, выбрать версию 17, Vendor: Amazon Corretto.

### 4. Синхронизировать Gradle

Нажать **Sync Now** в появившемся баннере, или:

**File → Sync Project with Gradle Files**

### 5. Подключить устройство или эмулятор

**Физическое устройство:**
- Включить режим разработчика: Настройки → О телефоне → нажать 7 раз на «Номер сборки»
- Включить «Отладка по USB» в настройках разработчика
- Подключить телефон кабелем USB
- На телефоне разрешить отладку

**Эмулятор:**
- **Tools → Device Manager → Create Virtual Device**
- Рекомендуется: Pixel 6, API 34

### 6. Собрать и запустить

Нажать кнопку **▶ Run** (Shift+F10)

---

## Сборка APK вручную

### Debug APK (для тестирования):

```bash
./gradlew assembleDebug
```

Файл появится по пути:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Release APK (для публикации):

Сначала создать keystore:

```bash
keytool -genkey -v -keystore noda-release.jks \
  -alias noda -keyalg RSA -keysize 2048 -validity 10000
```

Добавить в `app/build.gradle.kts`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("noda-release.jks")
        storePassword = "YOUR_PASSWORD"
        keyAlias = "noda"
        keyPassword = "YOUR_PASSWORD"
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        isMinifyEnabled = false
    }
}
```

Затем:

```bash
./gradlew assembleRelease
```

Файл появится по пути:
```
app/build/outputs/apk/release/app-release.apk
```

---

## Зависимости проекта

| Библиотека | Версия | Назначение |
|---|---|---|
| Hilt | 2.51.1 | Dependency Injection |
| Room | 2.6.1 | Локальная база данных |
| Kotlin Coroutines | 1.8.1 | Асинхронность |
| Lifecycle ViewModel | 2.8.7 | MVVM архитектура |
| AndroidX Activity | 1.10.1 | Back press handler |

---

## Архитектура

Проект построен по **Clean Architecture + MVVM**:

```
presentation/     ← UI (Activity, Canvas, ViewModel)
domain/           ← Бизнес-логика (модели, интерфейсы)
data/             ← Данные (Room DB, DAO, Repository)
di/               ← Hilt модули
```

---

## База данных

При изменении схемы (добавлении полей в `NodeEntity`) необходимо увеличить версию БД в `NodaDatabase.kt`:

```kotlin
@Database(entities = [NodeEntity::class, ConnectionEntity::class], version = 3)
```

`fallbackToDestructiveMigration()` уже включён — при несоответствии схемы БД пересоздаётся автоматически.

---

## Минимальные требования устройства

- Android 8.0 (API 26) и выше
- Поддержка OpenGL ES 2.0
- Рекомендуется: 2 ГБ RAM для плавной физики при большом количестве нод
