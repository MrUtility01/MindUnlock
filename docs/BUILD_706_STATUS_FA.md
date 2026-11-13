# MindUnlock — گزارش آخرین ساخت و وضعیت تکمیل

تاریخ: 2026-11-13

## آخرین نسخه قابل دریافت
- مخزن: `MrUtility01/MindUnlock`
- شاخه: `feature/mindunlock-core-modules`
- Commit: `c42676d95b1f415058492578c6d9c19ad40bfa8c`
- نسخه برنامه: `0.6.24-debug`، versionCode: `39`
- GitHub Actions: Build #706، run ID: `43481153484` — نتیجه: `success`
- تست‌های JVM و ساخت APK در CI موفق شدند.
- Artifact: [MindUnlock-Android-v0.6.24-debug](https://github.com/MrUtility01/MindUnlock/actions/runs/43481153484)
- Artifact ID: `14064688696`
- SHA-256 فایل ZIP آرتیفکت: `25c10dde0359affbe16024d191121dd7cb3ad657099b89b8a447f5635f9e5ea2`
- SHA-256 فایل APK داخل ZIP: `b2e782edc175fcca8cc35d8a5dd9c5f6e9f1201974004f216b873d61c2f4d083`

## تغییرات این ساخت
- ویرایش Inbox امکان تغییر هم‌زمان متن و دسته‌بندی را فراهم می‌کند.
- دسته‌بندی‌های موجود در UI شامل فکر، کار، ایده، یادگیری، کار دولتی، کار آزاد، کار شرکتی، پروژه، مالی، شخصی، خانواده و سایر است.
- ذخیره متن و دسته‌بندی از مسیر DAO جدید و به‌صورت یک عملیات انجام می‌شود.
- متد قدیمی `updateContent` برای سازگاری با تست‌ها و مصرف‌کنندگان قبلی حفظ شده است.
- تست `inboxClarificationUpdatesTextAndCategoryTogether` اضافه شده است.

## وضعیت انتشار و موارد باز
- PR #3 همچنان Draft و باز است؛ ادغام نشده است.
- شاخه مقصد PR همچنان `review/android-migration-tests` است.
- این APK از نوع Debug است، نه Release امضاشده.
- نصب/ارتقای واقعی روی دستگاه، آزمون بازیابی Backup روی دستگاه، بررسی کامل RTL/Jalali و دسترس‌پذیری، و ارزیابی امنیتی Sync هنوز تأیید نشده‌اند.
- بخش‌هایی از چرخه کامل Inbox، پیوندهای بین‌ماژولی، خروجی Markdown، ارزیابی عملی Lab و مرزهای Repository/Domain هنوز در ماتریس اصلی ناقص ثبت شده‌اند.

موفقیت Build #706 فقط مواردی را تأیید می‌کند که در همین اجرای خودکار اجرا شده‌اند؛ به معنی تکمیل تمام دامنه محصول یا تأیید Release نیست.
