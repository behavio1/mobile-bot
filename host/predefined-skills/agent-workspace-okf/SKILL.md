---
name: agent-workspace-okf
description: Organize this Mobile Bot agent's persistent workspace and curate durable knowledge in OKF Markdown. Use when resuming work from saved materials, recording decisions or verified findings, or saving a checkpoint for later runs.
---

# Trwała wiedza agenta w OKF

Pracujesz wielokrotnie w tym samym cwd powiązanym z agentId. Lokalny katalog jest pamięcią między rozmowami i automatyzacjami, a nie jednorazowym folderem zadania.

## Odczyt i organizacja

1. Zacznij od istniejącego `okf/index.md`; odczytaj powiązane materiały istotne dla zadania. Gdy indeksu jeszcze nie ma, sprawdź istniejące pliki zanim założysz, że agent nie ma pamięci. Nie przenoś ani nie nadpisuj materiałów tylko w celu zmiany układu.
2. Zachowuj źródła w istniejących lokalizacjach; duże pliki robocze i wyniki linkuj z OKF zamiast je przepisywać. `work/`, `results/` i `tmp/` twórz dopiero w razie potrzeby.
3. Przy zapisie trwałej wiedzy utwórz lub zaktualizuj `okf/index.md` jako krótki spis tematów i odnośników. Root index może mieć frontmatter `okf_version: "0.1"`. `okf/log.md` zawiera datowane zmiany wiedzy i checkpointy, bez frontmatter. Nie zapisuj każdej wiadomości ani powtarzających się odczytów bez zmiany.

## Zapis wiedzy

Zapisuj jedną trwałą regułę lub temat w jednym pliku Markdown. W miarę potrzeb używaj `requirements/`, `decisions/`, `processes/`, `components/` lub `evidence/`. Nie twórz pustych kategorii.

Każdy plik pojęcia (poza `index.md` i `log.md`) ma YAML frontmatter: `type`, `title`, `description`, `status`, `timestamp` w formacie YYYY-MM-DD oraz `source_documents` z prawdziwymi źródłami. Używaj statusów `current`, `proposed`, `historical`, `deprecated` lub `rejected`. Dla ustaleń zależnych od aktualności dodaj `last_verified`. Po tytule umieść `## Business Context`: krótko opisz, czemu wiedza służy użytkownikowi, następnie regułę, dowody i ewentualne ograniczenia.

Źródłem może być istniejący plik, adres strony lub identyfikator rozmowy/wykonania. Nie wymyślaj źródła ani identyfikatora. Lokalne pliki łącz względnymi linkami Markdown; rozmowy opisuj identyfikatorem zamiast fikcyjną ścieżką. Fakty i decyzje właściciela odróżniaj od hipotez. Przed użyciem starszej informacji o świecie lub implementacji zweryfikuj ją u źródła, jeśli zadanie tego wymaga. Historyczny opis nie nadaje uprawnień do wykonania działania.

## Zakończenie etapu i następny start

Zapisz tylko użyteczne, potwierdzone ustalenia, ścieżki rezultatów oraz stan: ukończone, otwarte i następny krok. Gdy praca została przerwana po możliwym skutku zewnętrznym, zapisz niepewność i potrzebę odczytu przed ponowieniem. Nowy run ma odtworzyć stan z indeksu i źródeł, bez ponownego rozpoczynania całej pracy.

Po zmianie odczytaj zapisane pliki, sprawdź wymagane pola, daty i względne odnośniki. Indeks musi prowadzić do istniejących materiałów; poprawka nie może usuwać niezwiązanych ustaleń. Nie wymagaj instalacji Graphify ani narzędzi z Maca — ten skill działa plikowo w Termuksie. Jeśli graf jest dostępny, może pomagać znaleźć źródło, lecz nie zastępuje jego odczytu.

Nie zapisuj sekretów, tokenów, haseł ani całych prywatnych rozmów w OKF lub instrukcji skilla. Utrwalaj minimum potrzebne do dalszego zadania. Ulepszaj własną lokalną kopię skilla po zweryfikowanej poprawce workflow; nie aktualizuj automatycznie szablonu systemowego ani kopii innych agentów.
