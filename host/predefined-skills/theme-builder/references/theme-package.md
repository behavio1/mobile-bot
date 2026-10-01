# Paczka motywu v1

JSON z polami:
- `schemaVersion`: 1
- `id`: custom- oraz 1–48 małych liter ASCII/cyfr/myślników, np. custom-kosmiczna-zaloga.
- `name`, `description`: niepuste, do 160 znaków; krótki polski opis. `prompt`: dokładne zlecenie (do 8000 znaków).
- `artworkTheme`: heroes | office | animals | influencers. Jawne ponowne wykorzystanie katalogu grafik, bez przypisywania nowych ról indeksom avatarów.
- `light`, `dark`: każda paleta zawiera dokładnie osiem kolorów #RRGGBB: primary, container, accent, background, surface, raised, ink, muted.
- `typography`: family (sans | serif | mono), weight (400,500,600,700,800,900), tracking (-0.5..1).
- `shapes`: small, medium, large (każde 0..36 dp).
- `card`: treatment (FOIL | EDITORIAL | SOFT | POP), radius (0..36), border (0..3), elevation (0..8), inset (0..8), gridGap (8..24).

FOIL: gradient i narożniki kolekcjonerskie. EDITORIAL: podpis z lewej i dolny akcent. SOFT: miękka karta z podpisem na środku. POP: asymetryczne rogi i dolny akcent.

Kontrast co najmniej 4.5:1: ink/background, ink/surface, ink/container, muted/raised; biały/primary w jasnym, #201923/primary w ciemnym oraz #201923/accent w obu. Primary w jasnym musi być odpowiednio ciemny, a w ciemnym jasny. Accent powinien być jasny w obu. Zachowaj różnicę między tłem, kartą i zaznaczeniem.

API lokalnego Hosta: POST /themes/validate oraz POST /themes z JSON {"theme": <paczka>}. GET /workspace zawiera customThemes. Skrypt korzysta z http://127.0.0.1:8767. Nie wysyłaj paczki ani danych profilu do zewnętrznej usługi.

## Przewodnik pierwszej misji
Motyw dziedziczy również pełną ilustrację przewodnika z artworkTheme; skład domyślnej ekipy i indeksy avatarów pozostają bez zmian. Napisz dedykowany prompt w pliku `guide-prompt.md` obok theme.json, wykorzystując `references/guide-prompt.md`. Jeżeli brak generatora ilustracji, zapisz prompt i jawnie wskaż istniejącego przewodnika — nie ogłaszaj go jako nowo wygenerowanego. Prompt pozostaje materiałem do przyszłej generacji; sam nie podmienia grafiki.
