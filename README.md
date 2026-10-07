# Daily Goals — Android loyiha

**Holat: Android manba loyihasi yaratildi. O‘rnatiladigan APK bu paketda YO‘Q.**

Ushbu muhitda JDK va Kotlin kompilyatori mavjud, lekin Android SDK, Gradle,
Android kutubxonalari va emulator mavjud emas. Rasmiy yuklash manzillariga
murojaat DNS/tarmoq xatosi bilan tugadi. Shuning uchun Android ilovasining
kompilyatsiyasi, qurilmaga o‘rnatilishi va ishlashi tasdiqlanmagan.
`reports/android-build-attempt.txt` va `reports/gradle-bootstrap-attempt.txt`
holatni qayd etadi. Manba kodi yoki ZIP fayl APK deb ko‘rsatilmagan.

## Ushbu paketdagi tekshirilgan natija

- Sof Kotlin domen qatlami haqiqiy `kotlinc` bilan kompilyatsiya qilindi.
- 82 ta biznes qoida testi bajarildi: **82 PASS, 0 FAIL**.
- Resurs XMLlari, uch tilning matn kalitlari va loyihaning statik shartlari tekshirildi.
- Kotlin fayllari sintaktik tahlildan o‘tkazildi. Bu Android type-check/build emas.
- 10 ta Room integratsiya va 5 ta Compose UI testi yozildi, ammo bu muhitda bajarilmadi.
- GitHub Actions APK build, imzo/metadata tekshirish va emulator testlari uchun yozilgan;
  pipeline foydalanuvchining hisobida ishga tushirilmagan.

## APK olishning avtomatik yo‘li

1. Arxivni oching. `DailyGoals` papkasining **ichidagi** fayllarni, jumladan
   yashirin `.github` papkasini, o‘zingizning GitHub repozitoriyingiz ildiziga joylang.
   Maxfiy repo tanlash mumkin. GitHub’da hisob/Actions imkoniyati bo‘lishi kerak.
2. Repo → **Actions → Build Daily Goals APK → Run workflow**.
3. Build muvaffaqiyatli tugagach, **DailyGoals-APK** artifactini oling.
   Unda `DailyGoals-v1.0.0-debug.apk` va haqiqiy imzo/metadata hisoboti bo‘ladi.
4. Android sinovlari alohida `android-tests` ishida API 28 va 36 emulatorlarida
   bajariladi. APK artifact borligi UI testlari o‘tganini o‘zi isbotlamaydi;
   ushbu ishning natijasini ham tekshiring.

Bu pipeline hali bu yerdan bajarilmagan. Birinchi Android buildda aniqlanadigan
xatolar tuzatilmaguncha loyihani production release deb hisoblamang.

### Lokal Android build

JDK 17, Python 3, Android SDK 36 va build-tools 35.0.0/36.0.0 kerak.
Android Studio o‘rnatilgan SDKdan foydalanish mumkin. `ANDROID_HOME`ni SDKga
ko‘rsating; keyin `scripts/build-apk.sh`ni bajaring. Windowsda
`gradlew.bat --no-daemon :core:test :app:lintDebug :app:assembleDebug` ishlatiladi.

`gradlew`/`gradlew.bat` bu loyihada **ochiq yozilgan Python bootstrap**ni chaqiradi:
rasmiy Gradle 8.13 distributivi yuklanadi, rasmiy SHA-256 bilan tekshiriladi va
ishga tushiriladi. Rasmiy Gradle Wrapper JAR mavjud deb ko‘rsatilmagan.
Oddiy wrapper kerak bo‘lsa, birinchi muvaffaqiyatli bootstrapdan keyin
`./gradlew wrapper --gradle-version 8.13 --distribution-type bin` standart wrapperni
hosil qiladi. CI esa o‘rnatilgan Gradle’dan to‘g‘ridan-to‘g‘ri foydalanadi.

### Release imzosi

Release uchun kalit yoki parol manba ichiga joylashtirilmagan. CI secrets:
`DG_KEYSTORE_BASE64`, `DG_KEYSTORE_PASSWORD`, `DG_KEY_ALIAS`, `DG_KEY_PASSWORD`.
Ular berilsa, imzolangan `DailyGoals-v1.0.0-release.apk` yig‘iladi va tekshiriladi.
Berilmasa, unsigned release build installable deb tarqatilmaydi; debug APK qoladi.

Debug applicationId: `uz.dailygoals.app.debug`.
Release applicationId: `uz.dailygoals.app`.
Ikkalasi boshqa ilovalar sifatida o‘rnatiladi; ma’lumotni o‘tkazish faqat explicit
JSON eksport/import orqali amalga oshiriladi. Signing kalitini almashtirish
oldingi ilova ustiga yangilashga yo‘l bermasligi mumkin: kalitni xavfsiz saqlang.

### O‘rnatish — faqat haqiqiy APK hosil bo‘lgach

Telefonda APK faylini oching, kerak bo‘lsa shu fayl ochuvchi ilova uchun o‘rnatish
ruxsatini bering va Install/O‘rnatish tugmasini bosing. ZIP yoki ushbu loyiha
papkasini Android ilova sifatida o‘rnatib bo‘lmaydi.

## Manbada amalga oshirilgan tuzilma

Kotlin + Compose Material 3, Navigation Compose, ViewModel, StateFlow,
Room, DataStore, AlarmManager, Storage Access Framework. Internet ruxsati yo‘q.
Bosh sahifa / Maqsadlar / Statistika / Arxiv / Sozlamalar — aynan 5 bo‘lim.

Domen qatlami Androidga bog‘lanmagan. Ketma-ket tekshiruv, yakunlash, arxiv,
foizlar, backup validatsiyasi va PIN xeshlash o‘sha qatlamda. Room yozuvlari
tranzaksiyalarda bajariladi. Yakunlangan natijalar UPDATE trigger bilan himoyalangan.
Explicit maqsadni o‘chirish bundan alohida va tasdiqlash bilan amalga oshiriladi.

## Muhim semantika

- Biznes sanasi: faqat Asia/Tashkent. Qurilmaning boshqa timezoni qo‘llanmaydi.
- Oxirgi sana ham hisobga kiradi (inclusive). Yaratilgan kun hisobga kirmaydi.
- “Bugun” — aynan bugun yakunlangan eng oxirgi tekshiruv sanasi.
- Yakunlangan kunlar foizga kiradi; pending kunlar kirmaydi.
- Umumiy foiz — natijasi bor mantiqiy maqsadlar foizlarining teng vaznli o‘rtachasi.
- Qayta faollashtirilgan davrlar tarixda alohida. Umumiy reytingda bitta root goal
  teng vazn oladi; davr tafsilotlarida har davrning o‘z statistikasi ko‘rsatiladi.
- Manual archive bugunni kiritmaydi; oldingi pending kunlarni yo‘qotmaydi.
- PIN faqat kirish to‘sig‘i: 4 raqamli, PBKDF2 xeshli; ma’lumotlar bazasini
  shifrlamaydi. Urinishlar soni talabga ko‘ra cheklanmagan.
- Eksport shifrlanmagan JSON. PIN xesh/tuz va qurilma sozlamalari eksport qilinmaydi.
- Import faqat bo‘sh bazaga; birlashtirish/ustiga yozish yo‘q.

Batafsil tanlovlar: `docs/DECISIONS.md`. Arxitektura: `docs/ARCHITECTURE.md`.
Cheklovlar va tekshirish reyestri: `docs/QA_AND_LIMITATIONS.md`.
Asl topshiriq: `docs/USER_REQUIREMENTS.txt`.

## Testlarni qayta ishlatish

Internet/SDKsiz domen testi: `scripts/test-core-offline.sh` (JDK + kotlinc kerak).
Oddiy Gradle/JUnit: `./gradlew :core:test`.
Android testlari: `./gradlew :app:connectedDebugAndroidTest` (emulator/telefon kerak).
APKni tekshirish: `scripts/verify_apk.py` (Android SDK vositalari talab etiladi).

**Bu ZIP APK talabining bajarilganini anglatmaydi.** APK, install/launch va
Android UI/Room sinovlari muvaffaqiyatli tugamaguncha release tayyor emas.
