---
name: theme-builder
description: Create custom Mobile Bot themes from the user's description — light and dark colors, typography and card style. Validate and publish packages available in the app profile.
---

# Tworzenie motywu

Odczytaj [format paczki](references/theme-package.md). Pracujesz w aplikacji Mobile Bot na telefonie, nie w repozytorium Androida. Nie przebudowuj APK i nie modyfikuj aplikacji ani motywów fabrycznych.

1. Z opisu wybierz spójną paletę jasną i ciemną, charakter nagłówków, kształty i wykończenie kart. Jeśli klient podał temat, samodzielnie dobierz szczegóły wizualne. Zapytaj tylko o brakujący cel lub konflikt istotnych wymagań.
2. Zapisz kompletny `theme.json` we własnym folderze pracy. Zapisz dokładne polecenie użytkownika w `prompt`. Przygotuj również dedykowany `guide-prompt.md` według `references/guide-prompt.md`; zachowaj skład ekipy i tożsamości agentów. Nadaj stabilny identyfikator `custom-...`. Zachowaj istniejące pliki/pracę przy ponowieniu zadania.
3. Grafiki wybieraj jawnie przez `artworkTheme`. To istniejący zestaw 15 avatarów i ikon. Ten format nie generuje nowych ilustracji. Nie twierdź, że wygenerowano obrazy ani że inne narzędzia Codexa są dostępne na telefonie. Jeżeli użytkownik wymaga nowych ilustracji, wyjaśnij konkretną brakującą możliwość; nie podsuwaj istniejących obrazów jako nowych. Motyw nadal może zmieniać pełną paletę, typografię i karty.
4. Uruchom `node .agents/skills/theme-builder/scripts/theme.mjs validate theme.json`. Popraw wskazane błędy kontrastu lub formatu. Nie osłabiaj walidatora.
5. Uruchom `node .agents/skills/theme-builder/scripts/theme.mjs publish theme.json`. Publikacja jest częścią zlecenia stworzenia własnego motywu. Skrypt sprawdza odczyt identyfikatora i całej paczki po zapisie; zapisuje raport obok JSON. Ponowienie tego samego identyfikatora aktualizuje motyw, nie tworzy duplikatu.
6. Poinformuj krótko: nazwa motywu, użyty istniejący zestaw grafik, dostępność w Profil → Styl aplikacji. Nie zmieniaj automatycznie aktywnego motywu klienta. Jeśli publikacja nie powiedzie się, nie deklaruj gotowości.
