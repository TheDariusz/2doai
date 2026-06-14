---
project: "2do AI"
version: 1
status: draft
created: 2026-05-24
context_type: greenfield
product_type: web-app
target_scale:
  users: # TODO — see Open Questions
  qps: # TODO — see Open Questions
  data_volume: # TODO — see Open Questions
timeline_budget:
  mvp_weeks: 7
  hard_deadline: null
  after_hours_only: true
---

# 2do AI — PRD

## Vision & Problem Statement

Osoby planujące długoterminowo wpisują swoje cele roczne i marzenia raz — na początku roku, w przypływie inspiracji — a potem rzadko do nich wracają. Klasyczne todo-listy i markdown w narzędziach typu Obsidian świetnie obsługują **bieżące zadania**, ale są pasywne wobec **długoterminowych celów** i **marzeń "kiedyś"** — nie przypominają, nie pytają, nie wyciągają zaniedbanych pozycji. Skutek: frustracja z niezrealizowanych celów; mniejsze zadania też pełzają w czasie bez nikogo, kto by zapytał "czemu to wisi 3 tygodnie".

Insight: AI nie musi być pasywnym asystentem czekającym na polecenia — może być **aktywnym partnerem, który sam wraca z propozycjami**. Konkurenci klasy Motion, Reclaim, Todoist AI, Akiflow optymalizują **kalendarz**: gdzie wcisnąć następne zadanie. Nikt nie podchodzi od strony "pamiętam, że w styczniu mówiłeś o prawie jazdy — minęło 8 miesięcy, zacznijmy". To jest luka.

## User & Persona

**Dariusz (i osoby o tym samym profilu).** Indywidualny użytkownik, który:

- już planuje długoterminowo (ma cele roczne, marzenia "kiedyś", listę projektów na lata),
- używa narzędzi pokroju Obsidian, Todoist, Google Calendar — czyli umie z markdown, lubi mieć dane "u siebie",
- sięga po aplikację w **dwóch trybach**: (1) codzienne zarządzanie zadaniami bieżącymi, (2) okresowe "siadanie do planowania" — przegląd celów, decyzje co dalej.

MVP myśli o tym profilu od początku (a nie tylko o jednym egzemplarzu = autorze), żeby decyzje produktowe były ogólniejsze niż "moje preferencje".

## Success Criteria

### Primary

Po 6-8 tygodniach pracy działa **pełen flow pierwszej sesji** (logowanie → wpisanie zadań i celów → AI inicjuje pamięć → kolejnego/innego dnia AI sama wraca z propozycją związaną z konkretnym zaniedbanym celem/marzeniem → użytkownik może podjąć lub odłożyć). Trzy warstwy zadań (bieżące / długoterminowe / marzenia) mają działający CRUD. Co najmniej jeden zrealizowany cykl: marzenie wpisane → AI wraca po kilku dniach → użytkownik wykonuje pierwszy krok.

### Secondary

- AI pamięta z poprzedniej sesji co użytkownik powiedział (kontekst rośnie między rozmowami, nie tylko w jednej).
- Można dodać zadanie z dowolnego źródła: wklejony link / tekst → AI proponuje "przeczytaj X, oto TLDR, pomyśl jak wdrożyć"; integracja z kalendarzem.
- Szybkie akcje z telefonu bez otwierania pełnego interfejsu (np. kanał komunikatorowy).
- Eksport wszystkiego do plików markdown (backup, portability, ścieżka do trybu offline w v2).

### Guardrails

- **Prywatność**: dane użytkownika i pamięć AI są dostępne tylko jemu. Nie wyciekają do osób trzecich, nie są używane do trenowania modeli. Pamięć osobista może być bardzo intymna — to obowiązuje od MVP.
- **Naturalny rytm proaktywnych przypomnień, NIE harmonogram**: AI wraca do zaniedbanych celów/marzeń z **losowym tempem**, tak jak znajomy, który po miesiącu pyta "hej, ruszyłeś tę rzecz, o której gadaliśmy?". Nie codziennie o tej samej godzinie. Nie powiadomienie przy każdym otwarciu. Częstotliwość ma czuć się **organicznie**, nie jak scheduler. Losowość JEST cechą produktu, nie błędem.
- Trwałość danych — awaria aplikacji ani błąd nie kasuje wpisanych zadań, celów ani pamięci AI.

## User Stories

### US-01: Pierwsza proaktywna propozycja zaniedbanego marzenia

- **Given** zalogowany użytkownik, który tydzień wcześniej wpisał marzenie "pojechać do Japonii kiedyś" w kategorii Czas wolny i hobby i od tamtego czasu nic z nim nie zrobił
- **When** AI rozpoznaje moment do proaktywnego wracania (naturalny rytm) i wybiera tę pozycję jako zaniedbaną
- **Then** użytkownik widzi propozycję w formie wiadomości od AI: "Hej, tydzień temu wpisałeś marzenie o Japonii — chcesz dziś zrobić pierwszy krok?"

#### Acceptance Criteria

- Propozycja zawiera odniesienie do **konkretnej** wpisanej pozycji (cytat tekstu marzenia), nie ogólnik
- Propozycja zawiera 3 opcje odpowiedzi: "zaczynam" / "nie teraz" / "nigdy o to nie pytaj"
- Wybranie "zaczynam" wywołuje FR-014 (AI proponuje pierwszy krok)
- Wybranie "nie teraz" → AI nie pyta o tę pozycję przez kolejny tydzień (ale może pytać o inne)
- Wybranie "nigdy o to nie pytaj" → pozycja jest oznaczona jako "wycofana z proaktywności"; nie jest usuwana ani ukończona
- Rytm jest **losowy** — dwa kolejne wybory nie idą jeden po drugim ani w sztywnym oknie; oczekiwana częstotliwość maksymalnie ~1 propozycja na 2-7 dni
- Pierwsza propozycja w sesji użytkownika jest z innej kategorii niż 2-3 poprzednie propozycje (bilansowanie z FR-012)

## Functional Requirements

### Konto i tożsamość

- FR-001: Użytkownik może założyć konto (email + hasło lub magic link). Priority: must-have
  > Socrates: Counter-argument rozważony: "magic link to friction / OAuth-Google byłby standardowy". Rozstrzygnięcie: stoi — metoda auth to detal implementacyjny, kluczowe że konto istnieje (pamięć AI wymaga tożsamości).

- FR-002: Użytkownik może zalogować się i wylogować. Priority: must-have
  > Socrates: Counter-argument rozważony: "logout jest nice-to-have w MVP". Rozstrzygnięcie: stoi — standardowy auth flow.

### Trzy warstwy zadań

- FR-003: Użytkownik może utworzyć, edytować, ukończyć i usunąć **zadanie bieżące** (krótka treść + termin opcjonalny + kategoria opcjonalna). Priority: must-have
  > Socrates: Counter-argument rozważony: "zadania bieżące są niepotrzebne w MVP — konkurujesz z klasycznym todo na ich terenie zamiast skupić się na różnicującym obszarze (długoterminowe + marzenia)". Rozstrzygnięcie: zostawione świadomie — aplikacja docelowo ma zastąpić klasyczne todo, więc bieżące są częścią value proposition. Ryzyko zakresu zauważone.

- FR-004: Użytkownik może utworzyć, edytować i ukończyć **cel długoterminowy** (treść + horyzont czasowy: ten rok / kilka miesięcy + kategoria opcjonalna z auto-tagiem AI gdy nie wybrano). Priority: must-have
  > Socrates: Counter-argument rozważony: "wymagana kategoria to friction, user może zrezygnować". Rozstrzygnięcie: zmieniono — kategoria opcjonalna; AI auto-taguje cicho jeśli user nic nie wybrał.

- FR-005: Użytkownik może utworzyć, edytować i ukończyć **marzenie "kiedyś"** (treść, bez ram czasowych + kategoria opcjonalna z auto-tagiem AI gdy nie wybrano). Priority: must-have
  > Socrates: Counter-argument rozważony: konsekwentnie z FR-004 — kategoria opcjonalna + AI auto-tag. Rozstrzygnięcie: zmieniono jak FR-004.

- FR-006: Użytkownik widzi wszystkie 3 warstwy w jednym widoku oraz może filtrować po warstwie i po kategorii. Priority: must-have
  > Socrates: Counter-argument rozważony: "3 warstwy w jednym widoku to bałagan; zakładki byłyby czytelniejsze". Rozstrzygnięcie: stoi — jednolity widok pozwala zobaczyć całość życia, filtry obsługują skupienie.

### Kategoryzacja

- FR-007: System udostępnia stałą listę 11 kategorii życiowych domen (Zdrowie, Finanse, Kariera i rozwój zawodowy, Edukacja i rozwój osobisty, Relacje, Dom i otoczenie, Czas wolny i hobby, Sprawy formalne i administracyjne, Bezpieczeństwo i przygotowanie na sytuacje awaryjne, Transport i mobilność, Rozwój wewnętrzny / wartości). Lista nieedytowalna w MVP. Priority: must-have
  > Socrates: Counter-argument rozważony: "11 to za dużo, niektóre będą puste / nie wpasują się we wszystkie persony". Rozstrzygnięcie: stoi — 11 kategorii pochodzi z konkretnej refleksji autora, pokrywa kluczowe domeny "osoby jak ja"; edycja jest w roadmapie post-MVP.

- FR-008: Przy tworzeniu pozycji AI proponuje kategorię na podstawie treści. Użytkownik może zaakceptować propozycję, wybrać inną lub zignorować (auto-tag stosowany cicho, możliwy do zmiany później). Priority: must-have
  > Socrates: Counter-argument rozważony: "auto-tag = każdy wpis wymaga wołania zewnętrznego modelu = koszt + latencja". Rozstrzygnięcie: stoi treścią — mechanizm tagowania to detal implementacyjny poniżej PRD.

### Pamięć AI

- FR-009: Przy pierwszym założeniu konta AI **może** zadać użytkownikowi 2-4 pytania osobiste (kim jest zawodowo, co już osiągnął, jakie wartości są ważne) jako seed pamięci. Krok onboardingu jest **opcjonalny** — użytkownik może pominąć i pamięć rośnie organicznie z FR-010. Priority: must-have
  > Socrates: Counter-argument rozważony: "pytania osobiste mogą być odbierane jako 'AI śledzi mnie'". Rozstrzygnięcie: zmieniono — krok jest opcjonalny / pomijalny.

- FR-010: Każda ukończona pozycja (zadanie/cel/marzenie) wzbogaca pamięć AI o użytkowniku. Priority: must-have
  > Socrates: Counter-argument rozważony: "pamięć zape­łni się szumem (np. 'kup mleko')". Rozstrzygnięcie: stoi — selekcja co istotne należy do AI, user nie filtruje ręcznie. Jakość pamięci to property AI, nie kontraktu FR.

### Proaktywne wracanie

- FR-011: AI samodzielnie wraca do użytkownika z propozycją podjęcia zaniedbanego celu długoterminowego lub marzenia, używając naturalnego rytmu (losowość — patrz Guardrails). Priority: must-have
  > Socrates: Counter-argument rozważony: "cele terminowe wymagają większej częstotliwości niż marzenia". Rozstrzygnięcie: stoi treścią — AI ma decydować o rytmie z uwzględnieniem terminu (część logiki AI, nie kontraktu FR).

- FR-012: Wybierając zaniedbaną pozycję do proaktywnej propozycji, AI bilansuje wybór między kategoriami w czasie — nie zalewa użytkownika propozycjami z jednej domeny. Priority: must-have
  > Socrates: Counter-argument rozważony: "AI nie wie co użytkownik priorytetyzuje". Rozstrzygnięcie: stoi + dodano FR-016 (kategorie priorytetowe od usera).

- FR-013: Użytkownik może na proaktywną propozycję odpowiedzieć: "zaczynam" / "nie teraz" / "przypomnij za <X dni/tygodni>" (presety: 7d, 30d, 90d) / "nigdy o to nie pytaj". Każda odpowiedź wpływa na przyszłe propozycje; "nigdy" przenosi pozycję do widoku "wycofanych" z możliwością przywrócenia. Priority: must-have
  > Socrates: Counter-argument rozważony: "brakuje snooze na konkretny czas". Rozstrzygnięcie: rozszerzono — dodano 4. opcję "przypomnij za X" z presetami.

### AI pomaga w realizacji

- FR-014: Gdy użytkownik odpowie "zaczynam" na proaktywną propozycję, AI proponuje konkretny pierwszy krok — lista 3-5 punktów z własnej wiedzy AI. **Bez wyszukiwania w internecie w MVP.** Priority: must-have
  > Socrates: Counter-argument rozważony: "wyszukiwanie w internecie to duży kawałek funkcjonalności, lepiej v2". Rozstrzygnięcie: zmieniono — drop internetu z MVP, AI używa tylko własnej wiedzy. Internet w roadmapie post-MVP.

### Kontrola użytkownika nad proaktywnością

- FR-015: Użytkownik może w dowolnym momencie wymusić proaktywną propozycję ("daj mi coś teraz") — AI wybiera zaniedbaną pozycję z bazy stosując te same reguły co automat (bilansowanie kategorii, priorytety). Priority: must-have
  > Socrates: Counter-argument rozważony: "manual trigger psuje filozofię 'znajomy po miesiącu', user będzie kompulsywnie klikać". Rozstrzygnięcie: stoi — user czasem chce kontroli; automat dalej istnieje jako "znajomy". Limit (np. cooldown) może być detalem implementacyjnym.

- FR-016: Użytkownik może (opcjonalnie) oznaczyć 3-5 kategorii jako priorytetowe. AI bilansuje proaktywne propozycje głównie wewnątrz priorytetowych kategorii; pozostałe wraca rzadziej. Domyślnie wszystkie kategorie są równe. Priority: must-have
  > Socrates: Counter-argument rozważony: "statyczne priorytety się zdezaktualizują — lepiej żeby AI sam wykrywał z aktywności usera". Rozstrzygnięcie: stoi treścią — explicit priorytety od usera są czystym sygnałem; AI-detected priority może być dodany w roadmapie ale nie zastępuje user-explicit.

### Dostępność offline

- FR-017: Użytkownik może przeglądać (read-only) swoje zapisane pozycje wszystkich trzech warstw bez połączenia z internetem. Edycja pozycji oraz wszystkie funkcje AI (auto-tag, propozycje proaktywne, pomoc w realizacji) wymagają połączenia. Priority: must-have

## Non-Functional Requirements

- Treść pozycji oraz pamięć AI nie wyciekają do osób trzecich; nie są używane do trenowania zewnętrznych modeli ani dzielone z innymi użytkownikami systemu. Obowiązuje od MVP — twardy guardrail osobistych danych.
- Akcje CRUD (utworzenie, edycja, oznaczenie pozycji jako ukończonej) skutkują widocznym efektem w interfejsie w mniej niż 500 ms.
- Operacje AI zaczynają wyświetlać widoczną informację zwrotną o postępie w mniej niż 500 ms, niezależnie od końcowej długości operacji.
- Po awarii, restarcie urządzenia lub utracie połączenia użytkownik po ponownym zalogowaniu odzyskuje 100% wpisanych pozycji i 100% pamięci AI. Żadna zapisana akcja nie znika cicho.
- Interfejs i komunikacja AI prowadzone są wyłącznie po polsku w MVP. Inne języki są poza zakresem wersji pierwszej.

## Business Logic

**Aplikacja sama, w nieregularnym rytmie, przypomina użytkownikowi o jego zaniedbanych celach i marzeniach — wybierając co, kiedy i z której domeny życia, na podstawie pamięci o nim.**

Reguła konsumuje cztery rodzaje wejścia: (1) pozycje wpisane przez użytkownika z ich metadanymi — typ warstwy, kategoria, horyzont czasowy, status, historia interakcji; (2) pamięć o użytkowniku zbudowaną z odpowiedzi onboardingowych i ukończonych pozycji; (3) historię proaktywnych propozycji aplikacji — co już zostało zaproponowane, kiedy, z jakim wynikiem (zaczynam / nie teraz / przypomnij za / nigdy); (4) ewentualne oznaczenie kategorii priorytetowych.

Wyjściem reguły jest **konkretna propozycja w naturalnym języku**: cytat zaniedbanej pozycji + zaproszenie do działania ("Hej, dwa miesiące temu wpisałeś marzenie X — chcesz zrobić pierwszy krok?"). Decyzja dotyczy czterech wymiarów jednocześnie: **co** (która pozycja), **kiedy** (rytm — losowo, organicznie, ~1 propozycja na kilka dni), **skąd** (która kategoria — bilansowanie domen + priorytetowość), **jak** (sformułowanie odwołujące się do pamięci użytkownika).

Użytkownik spotyka się z regułą w dwóch miejscach: (a) **automatycznie** — aplikacja wraca samodzielnie, jak znajomy po miesiącu; (b) **na żądanie** — opcja "wyciągnij mi coś teraz" stosuje tę samą regułę z pominięciem rytmu. To nie jest CRUD: użytkownik nie pytany dostaje aktywną decyzję o czym ma teraz pomyśleć — coś, czego siedząc nad statyczną listą sam by nie zrobił.

## Access Control

Konto w chmurze (email + hasło lub magic link) — multi-user od MVP. Model **płaski**: jeden użytkownik = jego własne zadania, cele, marzenia i pamięć AI. Brak współdzielonych workspace'ów, brak ról admin/member, brak sharingu.

Każdy użytkownik ma **export i import danych w formacie markdown** — żeby dane były przenośne (filozofia "u siebie", zgodna z duchem narzędzi markdown-first) i żeby tryb offline (poza MVP, ale przewidywany) mógł później skorzystać z tych samych plików.

Niezalogowany użytkownik na bramkowanej trasie → przekierowanie do logowania/rejestracji. Brak trybu anonimowego — pamięć o użytkowniku wymaga tożsamości.

## Non-Goals

- **AI nie zarządza kalendarzem ani nie optymalizuje czasu użytkownika.** Aplikacja może mieć wgląd w cele i pozycje, ale nie planuje "gdzie wcisnąć następne zadanie" — to świadoma różnica od Motion/Reclaim/Akiflow.
- **Brak czatu głosowego z AI.** Komunikacja wyłącznie tekstowa w MVP.
- **Brak natywnej aplikacji mobilnej.** Aplikacja webowa responsywna pokrywa zarówno desktop, jak i mobile.
- **Brak współdzielonych workspace'ów, rodzin, ról.** Aplikacja jest single-tenant per użytkownik; rodzina/zespół to roadmapa post-MVP.
- **AI w MVP nie szuka informacji w internecie.** Pomoc w realizacji (FR-014) używa tylko własnej wiedzy AI — bez dostępu do bieżących informacji z sieci.
- **Brak integracji z zewnętrznymi kalendarzami.** Aplikacja jest "czystym todo + planowaniem", nie sync'ującym do innych systemów.
- **Brak komercjalizacji / paywallu w MVP.** Aplikacja w wersji pierwszej darmowa; monetyzacja po walidacji insightu.
- **Brak pełnego offline-first z dwukierunkowym sync.** MVP ma tylko read-only offline (FR-017); edycja offline i synchronizacja md ↔ chmura to roadmapa.
- **Brak edycji listy 11 kategorii przez użytkownika.** User-defined taxonomy to roadmapa post-MVP.

## Open Questions

1. **Jaki ballpark skali (`target_scale.users`, `target_scale.qps`, `target_scale.data_volume`)?** — Frontmatter pozostawiony jako TODO; shape-notes nie zawiera explicit szacunków. Owner: autor. Block: nie (PRD wewnętrznie spójny), ale wpływ na decyzje stack-selection.
2. ~~**Jaki dostawca AI dla auto-tagowania (FR-008) oraz propozycji proaktywnych (FR-011-FR-014)?**~~ — **ROZSTRZYGNIĘTE (2026-06-13).** OpenRouter (brama) przed first-party Anthropic; modele rozdzielone: `anthropic/claude-haiku-4.5` dla auto-tagu (FR-008), `anthropic/claude-sonnet-4.6` dla propozycji proaktywnych (FR-011–014); prywatność w konfiguracji „no-training" (spełnia twardy guardrail); pamięć AI jako profil strukturalny + log epizodyczny wstrzykiwany do kontekstu. Szczegóły i uzasadnienie: `context/foundation/ai-provider.md`.
3. **Czy "naturalny rytm" proaktywnych propozycji to algorytm losowy z biasem, ML, czy reguły heurystyczne?** — Implementation detail; nie blokuje PRD. Owner: autor (faza implementacji).
4. **Jak konkretnie kategorie priorytetowe (FR-016) wpływają na bilansowanie (FR-012)?** — Algorytmiczna decyzja; może być zwykła waga ×N na propozycje z kategorii priorytetowych. Owner: autor.
5. **Czy onboarding-pytania (FR-009) generuje AI dynamicznie, czy są stałą listą?** — Można zacząć od stałych 4 pytań i iterować. Owner: autor.
