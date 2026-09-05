# WayArs — Smart Delivery & Taxi Assistant

Android-приложение (Kotlin + Jetpack Compose) для водителей/курьеров Bolt, Uber,
Glovo, Wolt. Читает параметры заказа с экрана (только чтение, без автокликов),
считает выгодность по формуле `earnings / distanceKm` и `earnings / timeMinutes`
относительно выбранного пресета, показывает вердикт в плавающем виджете.

## Что реально работает "из коробки"

- Полный Clean Architecture скелет: `data / domain / presentation / service / util`.
- Room (история заказов) + DataStore (язык, валюта, пресет, онбординг).
- 5 языков (en/pl/ro/uk/ru) с автоопределением системного языка и фолбэком на en.
- 3 пресета (Economy / Balance / Profitable Only), пороги авто-масштабируются
  под выбранную валюту (PLN/EUR/MDL/UAH/USD/GBP).
- Compose UI: Splash → выбор пресета → Dashboard (сводка за день + вердикт) →
  Stats (история) → Settings (язык/валюта/пресет/разрешения).
- `OrderAccessibilityService` — рекурсивно сканирует `rootInActiveWindow`,
  парсит текст regex'ами (сумма/км/мин), **никогда** не вызывает
  `performAction`/`dispatchGesture` — только чтение.
- `OverlayService` — плавающий виджет через `WindowManager` (перетаскивание,
  Accept/Reject пишут решение в локальную БД, никаких кликов по чужому приложению).
- Иконка приложения (адаптивная, в стиле твоего лого) и монохромная белая
  иконка для статус-бара/уведомлений.

## Что нужно доделать на твоей стороне (объективные ограничения)

1. **Package name приложений-целей.** В `res/xml/accessibility_service_config.xml`
   сейчас стоят предположительные ID (`ee.mtakso.driver`, `com.ubercab.driver`,
   `com.glovoapp.courier`, `com.wolt.courier.app`). Проверь реальные через:
   ```
   adb shell dumpsys window | grep mCurrentFocus
   ```
   открыв нужное приложение, и поправь список.

2. **Точность regex-парсера.** `ScreenTextParser` универсален (ищет
   `12,50 zł`, `€9.50`, `2,8 km`, `12 min` и т.п.), но реальная верстка экрана
   заказа у каждого сервиса своя. Если на первом тесте парсер что-то не
   ловит — добавь `Log.d("WayArsAccessibility", ...)` вывод текстов узлов
   (уже включён на уровне вердикта) и подстрой regex под конкретный формат.
   Это 10–20 минут работы с реальным телефоном, не архитектурная правка.

3. **Финальная иконка.** Я сделал адаптивную векторную иконку в стиле твоего
   лого (зелёный градиент + "W"), но это не точная копия. У тебя есть
   готовый набор (PNG/WEBP/SVG) — просто положи экспортированные PNG в
   `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`
   (и `ic_launcher_round.png`) — это заменит векторную заглушку без правок кода.

## Сборка через GitHub Actions

1. Создай репозиторий и склонируй в Termux:
   ```
   pkg install git gh -y
   gh auth login
   gh repo create wayars --private --clone
   ```
2. Скопируй содержимое этого проекта в папку репозитория.
3. `git add . && git commit -m "WayArs MVP" && git push origin main`
4. Открой вкладку **Actions** на GitHub — сборка запустится автоматически
   (workflow `.github/workflows/build.yml`), APK появится в **Artifacts**.

Workflow использует `gradle/actions/setup-gradle` и напрямую вызывает `gradle
assembleDebug` — коммитить `gradlew`/wrapper-бинарник не нужно.

## Разрешения, которые попросит приложение

- **Accessibility Service** (`Settings → Специальные возможности`) — чтение
  экрана. Включается кнопкой в Settings-экране приложения.
- **Overlay / "Отображение поверх других окон"** — для плавающего виджета.
  Тоже отдельная кнопка в Settings-экране.

## Структура

```
app/src/main/java/com/wayars/app/
├── data/            Room, DataStore, реализации репозиториев
├── domain/          модели, интерфейсы репозиториев, EvaluateOrderUseCase
├── presentation/     Compose UI (screens, components, theme), MainViewModel
├── service/
│   ├── accessibility/  OrderAccessibilityService (read-only сканер)
│   └── overlay/        OverlayService (WindowManager-виджет)
└── util/            LocaleManager, ScreenTextParser, CurrencyFormatter
```
