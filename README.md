# Sunday v2 (Android) + Agni server

Orb face, female voice, emotions, Claude brain, phone control, "Hey Sunday", floating orb, Agni link.

## Phone mein lagana (Vivo 1917, Android 11)

**Raasta A: Android Studio (PC)**
1. Android Studio > Open > `sunday-android`. Gradle sync hone do.
2. Phone: Developer options > USB debugging ON, USB se jodo, **Run**.

**Raasta B: bina PC build ke (GitHub)**
1. Is folder ko ek (private) GitHub repo mein push karo.
2. Actions > "Build APK" > Run workflow. Khatam hone par artifact `sunday-debug-apk` download karo.
3. APK phone mein kholo aur install karo ("Unknown sources" allow karna padega).

## Pehli baar setup (app ke andar card dikhega)
1. Settings > Anthropic API key.
2. Mic allow.
3. Settings > Accessibility > Sunday > ON. (Android 11 par kuch phones "restricted" warning dete hain; allow karo.)
4. "Display over other apps" allow.
5. Settings (Sunday ke andar) mein: **Hey Sunday** aur **Floating orb** ON karo, Save.

## Vivo ke liye zaroori (warna wo band ho jayegi)
- Settings > Battery > Background power consumption management > Sunday > Allow high background power consumption.
- Autostart ON. Recents mein Sunday lock karo.
- Google app + "Speech Services by Google" updated rakho; Hindi (India) aur English (India) voice download karo.

## Agni (VPS)
`agni-server/` folder ka README dekho. Phir Sunday Settings mein URL + token daalo.

## Kya kaam karta hai
- Orb dabao ya "Hey Sunday" bolo, phir baat karo. Jawab ke baad wo khud sunti rehti hai jab tak tum chup na ho jao.
- Apps kholna, Play Store se install (confirm ke saath), tap/type/scroll, screenshot dekhna, settings kholna, call, alarm, torch, volume.
- Phone problem: pehle battery/storage/RAM/network check, phir settings kholke step-by-step fix.
- Engineering task: "Agni, bracket design karo..." -> VPS par queue -> "Agni ka kaam kahan tak pahuncha?"
- Floating orb: tap = baat, drag = hatao, long-press = Sunday kholo.

## Safety (code mein)
- Banking/UPI/wallet/password apps read ya tap nahi hote.
- Install/uninstall/delete/pay/sign-out se pehle Allow/Nahi. Call se pehle hamesha confirm.
- Screen par likha text instruction nahi maana jata. Security prompts bypass nahi hote.

## Limits (sach)
- "Hey Sunday" Android ki normal speech recognition se chalta hai: battery zyada khata hai, kabhi miss karta hai, aur kuch phones par chhoti awaaz aa sakti hai. Behtar engine (Porcupine) baad ka upgrade hai.
- Agni ka asli PicoGK/FEM pipeline `runner_stub.py` ki jagah tumhe jodna hai (Phase 1 laptop par chalne ke baad).
- Ye code is environment mein compile nahi hua. Pehle build mein koi chhota error aaye to error text bhejo.
