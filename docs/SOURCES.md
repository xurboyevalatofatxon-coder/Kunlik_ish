# Birlamchi texnik manbalar

Tekshirish sanasi: **2026-10-07**. Talablar manbasi — `USER_REQUIREMENTS.txt`.
Quyidagi rasmiy hujjatlar versiya/platforma qarorlarini tekshirish uchun ishlatildi.
Versiyalar prerelease emas, tanlangan stable liniyaga mahkamlangan; ular har bir
kutubxonaning hozirgi eng yangi nashri degan da’vo qilinmaydi. Maven paketlarini
yuklash va ularning birgalikdagi Android buildi bu muhitda tekshirilmadi.

| Qism | Loyihadagi tanlov | Birlamchi manba |
|---|---|---|
| Android Gradle Plugin | 8.13.2; Gradle 8.13; JDK 17 | https://developer.android.com/build/releases/agp-8-13-0-release-notes |
| Kotlin / Compose compiler plugin | 2.2.21 | https://kotlinlang.org/docs/releases.html |
| Compose kutubxonalari | BOM 2025.10.01 | https://developer.android.com/develop/ui/compose/bom/bom-mapping |
| Compose BOM tamoyili | BOM compiler versiyasini almashtirmaydi | https://developer.android.com/develop/ui/compose/bom |
| Room | 2.8.4; KAPT/Java generation; schema export | https://developer.android.com/jetpack/androidx/releases/room |
| DataStore | 1.1.7 | https://developer.android.com/jetpack/androidx/releases/datastore |
| Activity | 1.11.0 | https://developer.android.com/jetpack/androidx/releases/activity |
| Navigation Compose | 2.9.5 | https://developer.android.com/jetpack/androidx/releases/navigation |
| Android SDK boshqaruvi | SDK 36, build-tools 35.0.0/36.0.0 | https://developer.android.com/tools/sdkmanager |

## Muhim cheklovlar

Room hujjatida 2026-yilgi 2.8.5 ham mavjud. Manba loyihada mavjud 2.8.4
liniyasi tanlangan; bu eng yangi patch ishlatilganini anglatmaydi. KAPT sozlamasi
`room.generateKotlin=false` bilan yozilgan. Oddiy konstruktor orqali dependency
injection va testlanadigan qat’iy JSON codec — afzal ko‘rilgan Hilt hamda
kotlinx.serialization o‘rnidagi ochiq hujjatlashtirilgan tanlovlar.

Rasmiy Gradle ZIP/SHA-256 manzillari bootstrap skriptida ishlatiladi. Tarmoq
bloklangani uchun distributiv yoki checksum fayli bu muhitda yuklab olinmadi;
checksum muvaffaqiyatli solishtirildi degan da’vo yo‘q. CI pipeline ham hali
ishlatilmagan. Barcha dependency va Android API mosligi birinchi haqiqiy build
hamda platforma testlaridan keyingina tasdiqlanishi mumkin.
