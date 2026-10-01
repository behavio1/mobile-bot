# Kontrakt warsztatu Mobile Bot

Host przekazuje etap: `research`, `build` albo `verify`; opis i odpowiedzi użytkownika są w `request.json`. Nie zapisuj poza bieżącym katalogiem warsztatu. Nie zmieniaj konfiguracji innych agentów ani katalogu opublikowanych mocy. Publikacją zajmuje się Host po weryfikacji.

- `research`: sprawdź źródła i środowisko, zapisz `research.md`; wynik `ready` oznacza gotowość do budowy, nie publikację.
- `build`: odczytaj `research.md`; kompletny pakiet zapisz w `output/skill/`. Nazwa we frontmatter musi być dokładnie identyfikatorem przekazanym w `request.json` (`skillId`). Przyjazną nazwę i jednozdaniowy opis w języku opisu użytkownika zwróć w polach `name` i `summary`. Dodawaj tylko zasoby rzeczywiście wymagane przez umiejętność. `checks.md` i materiał researchu pozostają poza pakietem, chyba że istotne źródła są potrzebne podczas korzystania z mocy — wtedy dodaj zwięzłą referencję w pakiecie.
- `verify`: użyj `output/skill/SKILL.md` i zasobów bez zmian; testy i wyniki zapisuj w `verification/` oraz `verification.md`. Nie zapisuj plików testowych w pakiecie.

Teksty dla użytkownika (`name`, `summary`, `question`, `evidence`) pisz w języku opisu użytkownika z `request.json`. Po angielsku moc to „power” (liczba mnoga „powers”); nie wstawiaj polskiego słowa „moc” do tekstu w innym języku.

W każdym etapie zwracaj wyłącznie JSON zgodny ze schematem dostarczonym przez Host: `status` (`ready`, `needs_input`, `failed`), `name`, `summary`, `question`, `evidence`. `question` zawiera pytanie tylko przy needs_input; `evidence` opisuje wykonane sprawdzenie lub konkretną przyczynę błędu. Host nie publikuje pytań i nieudanych prób jako mocy.

Środowisko: Android i Termux, Node.js oraz zalogowany Codex CLI. Nie zakładaj zainstalowanego Pythona ani narzędzi desktopowych. Sprawdź faktyczne binarki. Nie zaszywaj prywatnych danych z próbek, poświadczeń, ścieżki konkretnego warsztatu ani tożsamości użytkownika w pakiecie.

W nazwie, opisie, pytaniach i komunikatach dla użytkownika używaj słowa „moc” (liczba mnoga „moce”), zamiast „skill” lub „skillsy”. Nazwy techniczne `SKILL.md`, identyfikatory i ścieżki zachowaj bez zmian.

„Moc” to nazwa produktu dla skilla Codexa, a nie inny mechanizm. Prośbę „zbuduj moc” realizuj jako kompletny pakiet `SKILL.md`, zgodnie z powyższym kontraktem.
