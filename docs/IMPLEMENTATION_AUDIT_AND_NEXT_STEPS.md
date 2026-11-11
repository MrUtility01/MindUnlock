# ممیزی فنی و برنامه اجرایی مرحله بعد MindUnlock

> تاریخ ممیزی: 2026-11-11
> دامنه: مخزن main، ساختار Android، پایگاه داده Room و GitHub Actions مربوط به آخرین Build بررسی‌شده (#110).
> وضعیت: ممیزی اولیه مستندات و Pipeline؛ نه ممیزی کامل کد اجرایی یا آزمون روی دستگاه واقعی.

## 1. نتیجه اجرایی

**فعلاً افزودن مستقیم LearningProfile به سورس موجود توصیه نمی‌شود.** ابتدا باید منبع واحد و قابل‌ردیابی برای Android تعیین شود؛ سپس schema واقعی Room، Migrationها و مسیر backup/restore ممیزی و ایمن شوند.

علت اصلی: GitHub Actions در حال حاضر سورس Android را از آرشیو ریشه مخزن استخراج می‌کند، نه از پوشه `android/` که در شاخه main دیده می‌شود. پس از استخراج نیز Workflow با اسکریپت‌ها و patchهای متعدد فایل‌های Kotlin و Gradle را تغییر می‌دهد. این وضعیت با اصل «ساخت از سورس شفاف و version-controlled بدون patch پنهان در زمان Build» که در معماری محصول تعریف شده، سازگار نیست.

## 2. منبع واقعی Build

در `.github/workflows/android.yml`، مرحله آماده‌سازی Build:
1. فایل `MindUnlock-Android-v0.3.1-source.zip` را به‌عنوان ورودی انتخاب می‌کند.
2. آن را در `source-bundle` استخراج می‌کند.
3. مسیر حاوی `settings.gradle.kts` را پیدا می‌کند.
4. محتوای آن مسیر را به `android-build/` کپی می‌کند.
5. سپس مراحل متعدد Python/sed و patchهای inline را روی سورس استخراج‌شده اجرا می‌کند.

بنابراین:
- `android/` فعلی را نباید بدون بررسی، منبع معتبر APK تلقی کرد.
- فایل ZIP در ریشه مخزن در عمل یک منبع حیاتی Build است.
- منطق تغییر سورس در Workflow و فایل‌های فشرده/رمزشده `.github/` پراکنده است.
- بررسی و ویرایش فایل‌های Kotlin داخل `android/` به‌تنهایی لزوماً روی APK بعدی اثر نمی‌گذارد.

## 3. شواهد آخرین Build بررسی‌شده

Workflow run شماره 110:
- https://github.com/MrUtility01/MindUnlock/actions/runs/42855602919
- Job شناسه 129967658923
- اجرای تست JVM و ساخت Debug APK موفق گزارش شده است.
- این موفقیت فقط نشان می‌دهد سورس استخراج‌شده و patchشده در همان Pipeline build شده؛ اثبات‌کننده صحت همه قابلیت‌ها یا سازگاری داده روی دستگاه واقعی نیست.

در لاگ ممیزی همان اجرا، Entityهای واقعی سورس استخراج‌شده شامل مدل‌های محتوای آموزشی و پیشرفت، یادداشت، نشانک، وظایف مطالعه و cache رسانه دیده شده‌اند. مدل‌های مستقل موردنیاز Mind Management و Personal Learning Profile هنوز به‌عنوان بخشی از schema واقعی بررسی‌شده اثبات نشده‌اند.

## 4. ریسک‌ها و موانع

### P0 — Source of Truth نامشخص و دوگانه
- سورس واقعی Build در ZIP است.
- سورس قابل مشاهده در `android/` یک پایه جداگانه/قدیمی‌تر است.
- Workflow اصلاحات زمان Build را به شکل اسکریپت‌های پراکنده اعمال می‌کند.
- نتیجه: تغییر کد، code review، عیب‌یابی و بازتولید Build دشوار و مستعد اختلاف است.

**اقدام لازم:** انتقال کامل سورس مؤثر Build به ساختار عادی و قابل مشاهده در Git، سپس ساده‌سازی Workflow به checkout، test، build و verification. قبل از حذف ZIP یا patchها باید برابری خروجی و قابلیت‌ها بررسی شود.

### P0 — ایمنی داده و Room Migration
- اضافه‌کردن Entityها و افزایش نسخه Room بدون شناخت schema نصب‌شده می‌تواند باعث از دست‌رفتن داده شود.
- فایل `android/app/src/main/java/com/mindunlock/app/data/local/AppDatabase.kt` در پوشه پایه از `fallbackToDestructiveMigration()` استفاده می‌کند؛ اما این فایل به‌تنهایی اثبات نمی‌کند همین Database Builder در APK فعلی استفاده می‌شود.
- در Pipeline باید Database Builder و Migrationهای سورس استخراج‌شده به‌طور قطعی شناسایی شوند.
- هیچ Migration مخرب یا تغییر نام فایل دیتابیس بدون backup و آزمون ارتقا مجاز نیست.

**اقدام لازم:** شناسایی Database واقعی، نسخه فعلی، همه Entityها و DAOها، نام فایل DB، Migrationهای ثبت‌شده و سازگاری با داده‌های نسخه نصب‌شده؛ سپس تست migration از schema واقعی قبل از هر تغییر.

### P1 — مرزبندی Content Data و User Data
- محتوای آموزشی، پیشرفت مطالعه، یادداشت‌ها و تنظیمات سرور در مدل‌های فعلی دیده می‌شوند.
- دامنه MindUnlock علاوه بر آن به داده‌های خصوصی مانند Inbox، Journal، Tasks، Daily Plan، Learning Profile، سؤال‌های جانبی و بازخورد جلسات نیاز دارد.
- این داده‌ها باید از محتوای دانلودی/همگام‌سازی‌شده جدا باشند و sync محتوا نباید داده شخصی را منتقل کند.

**اقدام لازم:** طراحی مالکیت داده و قرارداد Repositoryها پیش از اضافه‌کردن قابلیت‌های جدید.

### P1 — Backup و Restore
- پیش از توسعه مدل‌های شخصی، باید مشخص شود backup فعلی کدام جدول‌ها، فایل‌های رسانه، ترجیحات و تاریخچه‌ها را پوشش می‌دهد.
- backup باید نسخه schema، نسخه برنامه، checksum، اعتبارسنجی، و در صورت نیاز رمزنگاری داشته باشد.
- Restore باید پیش‌نمایش و تأیید داشته باشد و در صورت خطا داده فعلی را خراب نکند.

### P2 — ساختار معماری و تست
- تا وقتی سورس اصلی شفاف نشده، افزودن Gradle moduleهای متعدد زودهنگام است.
- ابتدا package boundary و لایه‌های data/domain/presentation روشن شوند.
- Unit test، DAO test، Migration test و تست نصب/ارتقای واقعی باید قبل از گسترش ماژول‌ها تقویت شوند.

## 5. ترتیب اجرایی مصوب

### گام 1 — تثبیت سورس واقعی
- استخراج و ثبت کامل سورس مؤثر Build در مسیر version-controlled.
- مقایسه سورس قبل و بعد از patchهای Workflow.
- انتقال patchهای ضروری به commitهای عادی و قابل بازبینی.
- حفظ نسخه قبلی و امکان rollback.
- ساده‌سازی Workflow بدون تغییر رفتار APK.

### گام 2 — ممیزی پایگاه داده واقعی
- فهرست تمام Entityها، DAOها، Database Builderها و Migrationها.
- تعیین نسخه schema و نام فایل DB.
- مشخص‌کردن جدول‌های Content و User.
- بررسی foreign key، index، cascade و سیاست حذف.
- تهیه نقشه schema و تست ارتقای داده موجود.

### گام 3 — طراحی لایه داده شخصی
- قرارداد Repository برای Learning Profile، Learning Session، Feedback و Learning Question.
- طراحی مدل‌های Room با شناسه پایدار، زمان استاندارد و روابط مشخص.
- نگهداری ترجیحات خوداظهاری جدا از مشاهدات عملکردی.
- طراحی backup/restore و migration پیش از انتشار Entityهای جدید.

### گام 4 — پیاده‌سازی کم‌ریسک پروفایل
- آزمون ۲۰ سؤالی و ذخیره نسخه پرسش‌نامه.
- صفحه مشاهده/ویرایش پروفایل.
- Guided/Flexible Mode و ترتیب آموزش.
- بازخورد جلسه و ثبت سؤال جانبی.
- مرور مفاهیم در برابر مرور جزئیات.
- اتصال به Inbox و Future Topics.

### گام 5 — توسعه سایر ستون‌های MindUnlock
Mind Dump/Inbox، Tasks، Daily Plan، Journal، Knowledge، Review، Reports، Search، Backup/Restore و Control Center مطابق نقشه راه محصول.

## 6. خط قرمزهای ایمنی

- هیچ فایل دیتابیس یا داده کاربر با Migration مخرب حذف نشود.
- نصب نسخه جدید نباید مستلزم uninstall نسخه قبلی باشد.
- قبل از تغییر ساختار دیتابیس، مسیر backup/restore و آزمون migration لازم است.
- تا زمانی که امضای APK و package ID و مسیر ارتقا بررسی نشده، تغییرات نصب را روی نسخه دارای داده واقعی آزمایش نکنید.
- هیچ قابلیت برنامه‌ریزی‌شده‌ای به‌عنوان قابلیت آماده اعلام نشود.
- Build موفق به معنی تأیید عملکرد روی دستگاه واقعی نیست.

## 7. خروجی مورد انتظار ممیزی بعدی

پیش از شروع توسعه Learning Profile باید این موارد به‌صورت مستند و قابل ارجاع موجود باشند:
1. مسیر دقیق و یگانه سورس Android که CI از آن Build می‌کند.
2. فهرست فایل‌های Kotlin مؤثر در Build.
3. فهرست کامل Room Entityها و DAOها.
4. Database Builder واقعی و schema version.
5. فهرست Migrationها و تست‌های موجود.
6. مسیرهای Backup/Restore و پوشش داده‌ها.
7. جدول تفکیک Content Data و User Data.
8. طرح Migration امن برای اولین نسخه شامل LearningProfile.

**وضعیت فعلی:** گام صفر/ممیزی اولیه انجام شده؛ گام 1 و 2 هنوز تکمیل نشده‌اند. این سند عمداً هیچ تغییری در کد اجرایی یا دیتابیس ایجاد نمی‌کند.


## 8. ممیزی تکمیلی دیتابیس مؤثر در Build #110

> منبع شواهد: لاگ Job شناسه `129967658923` از Workflow run شماره 110؛ فایل‌های زیر از مسیر `android-build/` پس از استخراج ZIP و اجرای patchها چاپ شده‌اند. بنابراین این بخش درباره سورس مؤثر همان Build است، نه پوشه پایه `android/`.

### 8.1 دو پایگاه داده مستقل Room

**الف) دیتابیس محتوایی**
- کلاس: `AppDatabase`
- نام فایل: `mindunlock-content.db`
- نسخه اعلام‌شده در `@Database`: عدد 1
- Entityها: `ChapterEntity`, `ContentItemEntity`, `ServerConfigEntity`, `MediaCacheEntity`
- DAOها: فصل‌ها، محتوای آموزشی، تنظیمات سرور و cache رسانه.
- Builder مؤثر: `Room.databaseBuilder(..., "mindunlock-content.db").build()`

**ب) دیتابیس اطلاعات کاربر**
- کلاس: `UserDatabase`
- نام فایل: `mindunlock-user.db`
- نسخه اعلام‌شده در `@Database`: عدد 1
- Entityها: `ProgressEntity`, `NoteEntity`, `BookmarkEntity`, `StudyTaskEntity`
- DAOها: پیشرفت، یادداشت، نشانک و وظیفه مطالعه.
- Builder مؤثر: `Room.databaseBuilder(..., "mindunlock-user.db").build()`

این مرزبندی در سورس مؤثر Build تأیید شده است. مدل‌های Mind Management و Personal Learning Profile هنوز در این schemaها وجود ندارند.

### 8.2 ناسازگاری مهم در تعریف Migration

در `AppDatabase.kt`، annotation دیتابیس نسخه 1 را اعلام می‌کند؛ در همان کلاس یک شیء `MIGRATION_1_2` تعریف شده، اما Builder چاپ‌شده فقط `.build()` را فراخوانی می‌کند و هیچ `.addMigrations(MIGRATION_1_2)` در مسیر ساخت مشاهده نشده است.

علاوه بر این، بدنه Migration_1_2 شامل تغییرات جدول `progress` است، در حالی که `progress` در دیتابیس جداگانه `UserDatabase` قرار دارد. همچنین همان Migration جدول‌های notes، bookmarks و study_tasks را ایجاد می‌کند که در schema دیتابیس کاربر تعریف شده‌اند. این نشانه قوی از باقی‌ماندن کد Migration قدیمی/منسوخ پس از تفکیک دیتابیس‌هاست.

**نتیجه:** این Migration را نباید فعال یا مبنای توسعه قرار داد. پیش از هر تغییر schema باید تاریخچه نسخه‌های واقعی منتشرشده، فایل‌های DB نصب‌شده، مسیر مهاجرت legacy و schema هر دو دیتابیس با آزمون مستقل بازسازی شوند. این یافته به‌تنهایی اثبات نمی‌کند داده فعلی حذف شده یا Migration اجرا شده است.

### 8.3 انتقال از دیتابیس قدیمی

کلاس `LegacyDatabaseMigrator` دیتابیس قدیمی `mindunlock.db` را در حالت فقط‌خواندنی باز می‌کند و جدول‌ها را به دو مقصد منتقل می‌کند:
- به دیتابیس محتوا: `chapters`, `content_items`, `server_config`, `media_cache`
- به دیتابیس کاربر: `progress`, `notes`, `bookmarks`, `study_tasks`

پس از کپی، نشانگر `split_v1_complete` در SharedPreferences ثبت می‌شود. در کد چاپ‌شده، کنترل تعداد رکوردهای مبدأ/مقصد، checksum و گزارش نتیجه انتقال دیده نمی‌شود. دو دیتابیس مقصد نیز تراکنش‌های جداگانه دارند؛ بنابراین انتقال هم‌زمان آن‌ها یک تراکنش اتمیک واحد نیست.

**اقدام لازم:** قبل از هر اصلاح یا حذف مسیر legacy، باید تست مهاجرت با دیتابیس نمونه واقعی، شمارش رکوردها برای هر جدول، کنترل کلیدها و محتوای مهم، امکان retry ایمن و ثبت نتیجه/خطا اضافه و بررسی شود.

### 8.4 Backup و Restore

در Manifest مؤثر چاپ‌شده، `android:allowBackup="false"` ثبت شده است. بنابراین نباید به Android Auto Backup به‌عنوان مسیر پشتیبان‌گیری تکیه کنیم. در شواهد این ممیزی، وجود یک راهکار کامل و آزموده‌شده برای backup/restore هر دو دیتابیس و فایل‌های وابسته اثبات نشده است.

### 8.5 تصمیم‌های فنی پس از این ممیزی

1. هر دو دیتابیس باید به‌عنوان دو schema مستقل با نسخه و Migration مستقل مدیریت شوند.
2. داده‌های خصوصی MindUnlock (Inbox، Journal، Learning Profile، جلسات و بازخوردها) در دیتابیس کاربر یا لایه خصوصی مستقل قرار گیرند؛ نه در دیتابیس محتوای آموزشی.
3. پیش از افزودن هر Entity، schema export و Migration test برای هر دو دیتابیس راه‌اندازی شود.
4. کد Migration_1_2 فعلی تا زمان ردیابی تاریخچه نسخه‌ها نباید دوباره فعال شود.
5. طراحی و آزمون backup/restore باید پیش‌نیاز انتشار تغییرات داده کاربر باشد.

## 9. وضعیت گام‌ها پس از ممیزی تکمیلی

- شناسایی ZIP ورودی و مراحل patch: انجام شد.
- شناسایی دو Database Builder مؤثر در Build #110: انجام شد.
- ثبت نام فایل‌ها، نسخه اعلام‌شده و Entityهای هر دیتابیس: انجام شد.
- شناسایی ناسازگاری Migration_1_2: انجام شد؛ علت تاریخی هنوز نیازمند بررسی است.
- ممیزی کامل تاریخچه DB نسخه‌های منتشرشده و آزمون migration روی داده واقعی: باز.
- تثبیت سورس یگانه در Git و حذف تدریجی patchهای زمان Build: باز.
- طراحی backup/restore قابل بازیابی: باز.

**محدودیت شواهد:** این ممیزی از سورس استخراج‌شده و لاگ Build #110 استفاده می‌کند. فایل دیتابیس خصوصی گوشی کاربر یا وضعیت واقعی داده‌های نصب‌شده در دسترس ممیزی نبوده است؛ بنابراین هیچ ادعایی درباره سالم‌بودن/خراب‌بودن دیتابیس نصب‌شده مطرح نمی‌شود.


## 10. Source reconstruction map — 2026-11-11

### 10.1 Verified workflow structure

The active `.github/workflows/android.yml` is 154,656 characters and contains 38 named steps. The build is not a direct Gradle build of the checked-in `android/` directory. It performs these classes of transformations against a temporary `android-build/` tree:

1. Extracts `MindUnlock-Android-v0.3.1-source.zip` and copies the nested Android project into `android-build/`.
2. Applies password-length changes and generates launcher icons from the checked-in portrait asset.
3. Integrates the offline Persian Piper TTS engine and later hardens its on-demand voice loading/management.
4. Applies the study workflow/UI redesign from `.github/refactor_android.py.xz.b64`.
5. Applies database split, sync, and speech optimizations using the concatenated compressed scripts `.github/mindunlock_optimization.py.xz.b64` and `.github/mindunlock_optimization_part2.b64`.
6. Applies numerous additional direct Python/sed transformations for settings, storage controls, reading preferences, notes/bookmarks, typography, reset/diagnostics, chapter pagination, database initialization/migration, crash diagnostics, lesson loading, image decoding, and Room projections.
7. Generates the Gradle wrapper, runs JVM unit tests, builds the debug APK, verifies package metadata/integrity, uploads the APK, and publishes a prerelease.

Other compressed patch assets referenced by the workflow include `.github/patch_chapters.py.xz.b64`. The workflow also references `.github/refactor_android.py.xz.b64`, which must be included in the reconstruction inventory. A complete reconstruction must include every named workflow step, every referenced patch asset, every inline script, and all input assets—not only the three optimization files initially identified.

### 10.2 Checked-in Android tree differs from the ZIP baseline

The ZIP was fetched from the main branch and its SHA-1 matches the previously recorded value: `bb35cab525b86b2ed5136360985c96451c4daf16`. Its central directory contains 50 entries, including the Android source baseline. The checked-in `android/` tree currently contains 33 Git tree entries (including directories), and is not a faithful copy of that baseline.

Examples of baseline source files present in the ZIP but absent from the checked-in `android/` tree include:
- `app/src/main/java/com/mindunlock/app/AppLock.kt`
- `app/src/main/java/com/mindunlock/app/PersianSpeech.kt`
- `app/src/main/java/com/mindunlock/app/StudyReminderReceiver.kt`
- `app/src/main/java/com/mindunlock/app/ui/StudyScreens.kt`

The checked-in tree also has a different shape in key source areas (for example, the tracked `MainActivity.kt`, `Entities.kt`, and `AppDatabase.kt` have different file sizes from the ZIP baseline). File-size differences alone do not prove semantic changes, but combined with missing baseline files and build-time patching they confirm that `android/` cannot currently be treated as the effective APK source.

### 10.3 Current decision and next safe action

No Android source files, build scripts, workflow files, APKs, or database files were changed as part of this reconstruction-map pass. No migration or replacement was attempted.

Next, create a reproducible source snapshot from the ZIP plus all 38 workflow transformations in a disposable CI workspace, then record a manifest containing relative path, byte size, and SHA-256 for every effective source file. Compare that manifest with the tracked `android/` tree and identify exact file-level differences. Only after this snapshot can be reproduced should the effective source be promoted into the normal tracked Android project and the workflow simplified.

**Status:** workflow inventory and source-tree mismatch confirmed; exact effective-source reconstruction and file-by-file diff remain pending. This is not yet source stabilization.


## 11. File-by-file source comparison — Build #121

A comparison step was added after the effective-source manifest generation. It compares paths relative to the project roots (android/ versus android-build/) and computes SHA-256 for each file, excluding generated/cache directories named .gradle, build, and .git.

The comparison itself completed successfully in Build #121 (commit dd44b4393384687514dd32440a159e357c5b57ea). The subsequent artifact upload failed only because the artifact name accidentally contained a literal backslash before the run-number expression. The comparison output was nevertheless present in the job log and is recorded here:

| Measure | Count |
|---|---:|
| Tracked files under android/ | 17 |
| Effective build files under android-build/ | 31 |
| Identical relative paths and SHA-256 | 2 |
| Same relative path, different content | 13 |
| Only in tracked android/ | 2 |
| Only in effective android-build/ | 16 |

**Identical files:** the comparison reports 2 matching files. The available log excerpt does not enumerate their names, so they are not guessed here.

**Different content (13 paths):**
- README.md
- README_FA.md
- app/build.gradle.kts
- app/proguard-rules.pro
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/mindunlock/app/MainActivity.kt
- app/src/main/java/com/mindunlock/app/data/local/AppDatabase.kt
- app/src/main/java/com/mindunlock/app/data/local/Entities.kt
- app/src/main/java/com/mindunlock/app/data/repository/SyncRepository.kt
- app/src/main/java/com/mindunlock/app/ui/theme/Theme.kt
- gradle.properties
- gradle/wrapper/gradle-wrapper.properties
- settings.gradle.kts

**Only in tracked android/ (2 paths):**
- app/src/main/java/com/mindunlock/app/data/model/SyncModels.kt
- app/src/main/java/com/mindunlock/app/data/remote/SyncApi.kt

**Only in effective android-build/ (16 paths):**
- .gitignore
- app/src/main/java/com/mindunlock/app/AppLock.kt
- app/src/main/java/com/mindunlock/app/CrashDiagnostics.kt
- app/src/main/java/com/mindunlock/app/PersianSpeech.kt
- app/src/main/java/com/mindunlock/app/StudyReminderReceiver.kt
- app/src/main/java/com/mindunlock/app/data/local/LegacyDatabaseMigrator.kt
- app/src/main/java/com/mindunlock/app/data/local/UserDatabase.kt
- app/src/main/java/com/mindunlock/app/ui/StudyScreens.kt
- app/src/main/res/mipmap-hdpi/ic_launcher.png
- app/src/main/res/mipmap-mdpi/ic_launcher.png
- app/src/main/res/mipmap-xhdpi/ic_launcher.png
- app/src/main/res/mipmap-xxhdpi/ic_launcher.png
- app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
- gradle/wrapper/gradle-wrapper.jar
- gradlew
- gradlew.bat

### 11.1 Interpretation and safety decision

This is a substantial source divergence, not a small drift: only 2 of 17 tracked files are byte-identical to a same-path effective file, while 13 same-path files differ and 16 effective files have no tracked counterpart. The result validates that the current android/ directory is not a faithful, build-ready representation of the source used for the APK.

The comparison is path-and-hash evidence only. It does not establish that each difference is a defect, nor does it establish semantic equivalence or correctness. Some differences are expected from generated launcher assets, wrapper generation, package version changes, and CI patches.

**Do not copy the 31 effective files blindly into android/ yet.** The next safe operation is to publish the comparison report as an artifact, then reconstruct a clean source tree from the exact effective build output and classify generated files, intentional CI modifications, and durable source changes. Only after that should a reviewed promotion into the tracked Android project be considered.

### 11.2 Build #121 and correction

Build #121 reached and completed the comparison step, but the artifact upload failed because its configured name contained a literal backslash before the run-number expression. This stopped later tests and APK packaging in that run. The workflow expression was corrected in commit 90f92ee4375a19979eb5fb3f4b811130d4175f6c; Build #122 is the verification run. No Android app source or database code was changed by either workflow-only commit.


## 12. آرشیو واقعی سورس مؤثر و ممیزی اولیه آن — Build #126

### 12.1 دریافت و اعتبارسنجی آرشیو

در Build #126، پس از پایان تمام Patchهای CI، از پوشه `android-build/` یک Snapshot ساخته و به‌عنوان Artifact بارگذاری شد.

- Build: https://github.com/MrUtility01/MindUnlock/actions/runs/43011962285
- Commit: `c5b9124f206081dfbf34d127a2e78ef8bfaaae79`
- Artifact: `MindUnlock-effective-source-snapshot-126`
- Artifact ID: `13851346127`
- ZIP داخلی: 31 فایل، حدود 178,556 بایت
- JVM unit-test task: موفق
- assembleDebug: موفق
- بررسی ساختار APK و شناسه بسته: موفق
- بارگذاری APK و انتشار Release: موفق

توجه: Artifact دانلودشدهٔ GitHub خود یک ZIP بیرونی است که ZIP Snapshot را درون خود دارد. آرشیو داخلی با موفقیت استخراج و فهرست 31 فایل آن بررسی شد. این آرشیو شامل فایل‌های منبع مؤثر پس از Patchهاست؛ به‌تنهایی اثبات نمی‌کند که همه قابلیت‌ها روی دستگاه واقعی درست کار می‌کنند.

### 12.2 دسته‌بندی اولیه فایل‌ها

**کدهای برنامه که باید به منبع اصلی منتقل و بازبینی شوند:**
- `AppLock.kt`
- `CrashDiagnostics.kt`
- `PersianSpeech.kt`
- `StudyReminderReceiver.kt`
- `LegacyDatabaseMigrator.kt`
- `UserDatabase.kt`
- `ui/StudyScreens.kt`
- نسخه مؤثر `MainActivity.kt`، `AppDatabase.kt`، `Entities.kt`، `SyncRepository.kt` و `Theme.kt`

**فایل‌های تولیدشده یا ابزار ساخت که نباید با منطق برنامه یکی گرفته شوند:**
- آیکن‌های mipmap تولیدشده
- `gradle-wrapper.jar`، `gradlew` و `gradlew.bat` (باید با نسخه Wrapper و سیاست نگهداری مخزن تطبیق داده شوند)
- `.gitignore` و فایل‌های Gradle (باید جداگانه با تنظیمات CI تطبیق داده شوند)

این دسته‌بندی اولیه است؛ پیش از انتقال، منشأ هر فایل و Patch تولیدکننده باید ثبت شود.

### 12.3 یافته‌های مهم در کد مؤثر

**بحرانی برای بررسی پیش از هر انتشار دارای داده واقعی — مهاجرت دیتابیس:**
- `LegacyDatabaseMigrator.copyTable` تعداد ردیف‌های مبدأ و مقصد را مقایسه نمی‌کند.
- مقدار بازگشتی `target.insert(...)` بررسی نمی‌شود؛ در نتیجه درج ناموفق می‌تواند بدون ثبت خطای صریح از حلقه عبور کند.
- پس از پایان تراکنش‌ها، نشانگر `split_v1_complete` ثبت می‌شود؛ بنابراین باید اثبات شود تمام جدول‌ها و ردیف‌ها واقعاً منتقل شده‌اند، نه صرفاً اینکه حلقه اجرا شده است.
- تراکنش دو دیتابیس مستقل است و اتمیک مشترک نیست. مسیر توقف برنامه بین پایان تراکنش اول و دوم باید با اجرای مجدد امن و آزمون‌شده پوشش داده شود.
- فایل قدیمی فعلاً حذف نمی‌شود؛ این رفتار باید تا زمان تأیید شمارش و صحت داده حفظ شود.

**ناسازگاری مهاجرت Room:**
- `AppDatabase` نسخه 1 دارد اما یک `MIGRATION_1_2` تعریف شده است.
- بدنه این Migration علاوه بر `content_items`، جدول‌های `progress`، `notes`، `bookmarks` و `study_tasks` را دست‌کاری می‌کند؛ این جدول‌ها اکنون در `UserDatabase` قرار دارند.
- Builder فعلی `AppDatabase` این Migration را ثبت نمی‌کند. فعلاً نباید آن را فعال یا نسخه دیتابیس را تغییر داد؛ ابتدا تاریخچه نسخه‌های منتشرشده و Schema واقعی باید بررسی شود.

**امنیت همگام‌سازی:**
- Manifest مقدار `usesCleartextTraffic=true` دارد و `SyncRepository.baseUrl` آدرس `http://` می‌سازد.
- بنابراین نام کاربری و رمز عبور نشست به‌صورت HTTP بدون رمزنگاری انتقال می‌یابند. مستندات فعلی استفاده را به شبکه محلی مورد اعتماد محدود می‌کنند، اما این محدودیت جایگزین TLS نیست.
- پیش از استفاده خارج از شبکه کاملاً مورد اعتماد، باید طراحی TLS و مدیریت گواهی انجام شود. تا آن زمان سرویس نباید روی اینترنت عمومی در معرض دسترسی قرار گیرد.

**محدودیت پشتیبان‌گیری و آزمون:**
- Manifest دارای `allowBackup=false` است؛ این موضوع به‌تنهایی اثبات نمی‌کند که برنامه راهکار مستقل Backup/Restore دارد.
- Snapshot هیچ مسیر `src/test` یا `src/androidTest` ندارد. موفقیت Gradle task ثبت شده، اما از این شواهد نمی‌توان پوشش تست معنادار منطق برنامه یا تست روی دستگاه را نتیجه گرفت.

### 12.4 تصمیم و ترتیب اقدام

1. آرشیو Build #126 به‌عنوان مرجع ممیزی نگهداری شود؛ هنوز جایگزین سورس Git نیست.
2. Patchهای CI به تفکیک فایل و ترتیب اجرا مستند شوند.
3. منطق مهاجرت دیتابیس با آزمون‌های شمارش ردیف، تطبیق کلیدها، تکرارپذیری، خرابی میان دو تراکنش و گزارش خطا پوشش داده شود.
4. Schemaهای Room و نسخه‌های تاریخی پیش از هر تغییر دیتابیس تطبیق داده شوند.
5. امنیت انتقال HTTP و راهکار Backup/Restore پیش از انتشار عمومی تعیین تکلیف شود.
6. پس از آن، کدهای دائمی به پوشه `android/` منتقل و Workflow از Patchهای کدساز به Build عادی تبدیل شود.

**ممنوعیت فعلی:** کپی یک‌باره Snapshot روی `android/`، فعال‌سازی Migration قدیمی، حذف دیتابیس legacy یا تغییر Schema بدون آزمون و نسخه پشتیبان مجاز نیست.


## 13. ممیزی تطبیق قابلیت‌های فعلی با مشخصات MindRebuild

این بخش بر اساس سورس مؤثر Snapshot شماره 126 بررسی شده است؛ «وجود یک صفحه مشابه» به معنی پیاده‌سازی کامل مدل دامنه یا چرخه یادگیری مشخص‌شده در سند یکپارچه‌سازی نیست.

### 13.1 قابلیت‌های موجود در سورس فعلی

- مطالعه محتوای فصل‌ها و آیتم‌ها، نمایش Reader و دریافت محتوا از سرور ویندوز
- فلش‌کارت، Quiz/Exam و مرور زمان‌بندی‌شده بر اساس فیلدهای `intervalDays` و `dueAt` در `ProgressEntity`
- یادداشت‌ها، نشانک‌ها، برنامه‌ریز وظایف، یادآورها و گزارش پیشرفت
- تنظیمات، قفل محلی برنامه، موتور گفتار فارسی و گزارش کرش
- ذخیره محتوای دریافت‌شده و اطلاعات کاربر در دو دیتابیس محلی جداگانه

### 13.2 شکاف‌های مشخص نسبت به مشخصات MindRebuild

در مدل‌های Room موجود، موجودیت‌های مستقل زیر دیده نمی‌شوند:
- Inbox / Mind Dump
- JournalEntry
- FutureTopic / Later
- CuriosityItem
- KnowledgeEntry
- LearningProfile و داده‌های پاسخ/مشاهده برای تکامل تدریجی آن
- LearningSession و LearningSessionFeedback
- LearningQuestion و ارتباط سؤال‌ها با موضوع‌ها
- ReviewItem / ReviewAttempt مستقل از Progress مربوط به محتوای درسی

همچنین در مسیرهای Navigation فعلی، صفحه‌های مستقل برای دفتر روزانه، تخلیه ذهن، موضوعات آینده/کنجکاوی و پروفایل یادگیری مشاهده نشدند. وجود Planner و Notes به‌تنهایی جایگزین این ماژول‌ها نیست.

### 13.3 تصمیم توسعه

این شکاف‌ها باید به‌عنوان کار توسعه آینده ثبت شوند، نه اینکه به‌عنوان قابلیت موجود معرفی شوند. پیش از ساخت این ماژول‌ها، باید مدل داده و مالکیت داده‌های خصوصی تثبیت شود؛ سپس مهاجرت Room نسخه‌بندی‌شده با تست و Backup/Restore طراحی شود. قابلیت‌های موجود (مطالعه، یادداشت، نشانک، وظایف، مرور و همگام‌سازی محتوا) باید حفظ شوند.

## 14. دامنه واقعی اعتبار Build و تست‌ها

در Build #126 فرمان `./gradlew testDebugUnitTest` با موفقیت پایان یافت، اما لاگ Gradle به‌صراحت این Taskها را `NO-SOURCE` نشان می‌دهد:
- `kspDebugUnitTestKotlin`
- `compileDebugUnitTestKotlin`
- `compileDebugUnitTestJavaWithJavac`
- `processDebugUnitTestJavaRes`
- `testDebugUnitTest`

بنابراین نتیجه دقیق این است: **Task تست JVM بدون خطای Gradle پایان یافت، اما هیچ تست JVM اجرا نشد.** این نتیجه نباید به‌عنوان تأیید عملکرد یا پوشش تست تلقی شود.

ساخت APK، بررسی صحت ZIP داخلی APK و بررسی Package Name با موفقیت انجام شده‌اند. این موارد فقط صحت ساختار بسته و موفقیت کامپایل را نشان می‌دهند؛ تست نصب/ارتقا روی دستگاه، رفتار واقعی UI، صدا، مهاجرت داده، امنیت شبکه و بازیابی پشتیبان همچنان لازم است.
