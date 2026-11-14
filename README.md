# Focus On

Shaxsiy Android ilova: diqqat bilan ishlash uchun taymer. Fokus vaqtida o'simlik o'sadi,
chalg'ituvchi ilovalar yopiladi. Login yo'q, internet kerak emas, hamma ma'lumot telefonda qoladi.

## Imkoniyatlar
- Taymer (10–120 daqiqa) va sekundomer, o'sadigan o'simlik, 16 xil o'simlik (kod bilan chizilgan)
- Fokus vaqtida ilovalarni bloklash: "hammasi, ruxsatlilardan tashqari" yoki "faqat tanlanganlar"
- Qattiq rejim (Deep Focus): ilovadan chiqsangiz, 10 soniyada qaytmasangiz o'simlik quriydi
- Bog': kun / hafta / oy / yil bo'yicha, 8 xil dizayn ketma-ket kunlar uchun ochiladi
- Statistika, teglar, tangalar va do'kon, tanaffus, kunlik maqsad, fon tovushlari
- Ilovalarga kunlik limit (limit tugasa ilova yopiladi, 5 daqiqalik ruxsat)
- Bosh ekran vidjeti, tungi rejim, haftalik avtomatik zaxira (Download/FocusOn)

## Ubuntu versiyasi
`desktop/` papkasida. Dizayn telefondagi bilan bir xil: kod `shared/` papkasida umumiy.
Kompyuterda bloklash yo'q; taymer, bog', do'kon, statistika, tray, bildirishnomalar va
eslatmalar bor. Telefon bilan zaxira fayl orqali birlashtiriladi.

```bash
cd desktop && ./install.sh
```

## Yig'ish (telefon)
Kotlin + Jetpack Compose. Android SDK kerak.

```bash
./gradlew assembleDebug
```

Imzo kaliti (`keystore/focuson.keystore`) repoga qo'shilmagan. Uni alohida saqlang.
