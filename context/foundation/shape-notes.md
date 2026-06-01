---
project: "2do AI"
context_type: greenfield
created: 2026-05-24
updated: 2026-05-24
checkpoint:
  current_phase: 8
  phases_completed: [1, 2, 3, 4, 5, 6, 7]
  gray_areas_resolved:
    - topic: "pain category"
      decision: "brak pamięci proaktywnej + brak kontekstu o użytkowniku + brak warstwy marzeń 'kiedyś'"
    - topic: "differentiating insight vs Motion/Reclaim/Todoist-AI"
      decision: "AI proaktywnie wraca z zaniedbanymi celami/marzeniami — nie tylko czeka na input"
    - topic: "primary persona scope"
      decision: "ja + 'osoby jak ja': planujące długoterminowo, Obsidian/markdown-friendly"
    - topic: "auth model"
      decision: "konto w chmurze (email + hasło lub magic link); multi-user web/mobile od MVP"
    - topic: "role model"
      decision: "płaski (user = jego dane); brak workspace'ów; export/import md jako bezpieczna droga przenośności"
    - topic: "MVP scope vs timeline"
      decision: "commit do 6-8 tyg po godzinach z pełnym 5-krokowym flow; nie scope-down; świadoma akceptacja sustained-effort"
    - topic: "rhythm of proactive AI reminders"
      decision: "losowość JEST cechą — naturalny rytm 'znajomego po miesiącu', nie sztywny scheduler. Critical insight."
    - topic: "categorization scope"
      decision: "marzenia + długoterminowe wymagają kategorii; bieżące opcjonalnie"
    - topic: "category list editability"
      decision: "11 predefiniowanych kategorii (życiowe domeny), nieedytowalne w MVP"
    - topic: "auto-tagging"
      decision: "AI proponuje kategorię z treści; user może zmienić przed zapisem"
    - topic: "category balancing in proactivity"
      decision: "AI bilansuje propozycje proaktywne między kategoriami — nie zalewa jedną domeną"
  frs_drafted: 17
  quality_check_status: accepted
---

# 2do AI — Shape Notes

> Seed idea: `idea-notes.md` — Todo lista oraz asystent planowania z AI. Łączy zadania bieżące, długoterminowe i marzenia "kiedyś"; AI buduje pamięć o użytkowniku i proaktywnie przypomina o zaniedbanych celach.

## Vision & Problem Statement

Osoby planujące długoterminowo wpisują swoje cele roczne i marzenia raz — na początku roku, w przypływie inspiracji — a potem rzadko do nich wracają. Klasyczne todo-listy (Todoist, kalendarz) i markdown w Obsidianie świetnie obsługują **bieżące zadania**, ale są pasywne wobec **długoterminowych celów** i **marzeń "kiedyś"** — nie przypominają, nie pytają, nie wyciągają zaniedbanych pozycji. Skutek: frustracja z niezrealizowanych celów; mniejsze zadania też pełzają w czasie bez nikogo, kto by zapytał "czemu to wisi 3 tygodnie".

Insight: AI nie musi być pasywnym asystentem czekającym na polecenia — może być **aktywnym partnerem, który sam wraca z propozycjami**. Konkurenci (Motion, Reclaim, Todoist AI, Akiflow) optymalizują **kalendarz**: gdzie wcisnąć następne zadanie. Nikt nie podchodzi od strony "pamiętam, że w styczniu mówiłeś o prawie jazdy — minęło 8 miesięcy, zacznijmy". To jest luka.

## User & Persona

**Dariusz (i osoby o tym samym profilu).** Indywidualny użytkownik, który:

- już planuje długoterminowo (ma cele roczne, marzenia "kiedyś", listę projektów na lata),
- używa narzędzi pokroju Obsidian, Todoist, Google Calendar — czyli umie z markdown, lubi mieć dane "u siebie",
- sięga po aplikację w **dwóch trybach**: (1) codzienne zarządzanie zadaniami bieżącymi, (2) okresowe "siadanie do planowania" — przegląd celów, decyzje co dalej.

MVP myśli o tym profilu od początku (a nie tylko o jednym egzemplarzu = autorze), żeby decyzje produktowe były ogólniejsze niż "moje preferencje".

## Access Control

Konto w chmurze (email + hasło lub magic link) — multi-user web/mobile od MVP. Model **płaski**: jeden użytkownik = jego własne zadania, cele, marzenia i pamięć AI. Brak współdzielonych workspace'ów, brak ról admin/member, brak sharingu.

Każdy użytkownik ma jednak **export i import danych w formacie markdown** — żeby dane były przenośne (filozofia "u siebie", zgodna z duchem Obsidiana) i żeby tryb offline (poza MVP, ale przewidywany) mógł później skorzystać z tych samych plików.

Niezalogowany użytkownik na bramkowanej trasie → przekierowanie do logowania/rejestracji. Brak trybu anonimowego — pamięć o użytkowniku wymaga tożsamości.

## Success Criteria

### Primary

Po 6-8 tygodniach pracy działa **pełen flow pierwszej sesji** (logowanie → wpisanie zadań i celów → AI inicjuje pamięć → kolejnego/innego dnia AI sama wraca z propozycją związaną z konkretnym zaniedbanym celem/marzeniem → użytkownik może podjąć lub odłożyć). Trzy warstwy zadań (bieżące / długoterminowe / marzenia) mają działający CRUD. Co najmniej jeden zrealizowany cykl: marzenie wpisane → AI wraca po kilku dniach → użytkownik wykonuje pierwszy krok.

### Secondary

- AI pamięta z poprzedniej sesji co użytkownik powiedział (kontekst rośnie między rozmowami, nie tylko w jednej).
- Można dodać zadanie z dowolnego źródła: wklejony link / tekst → AI proponuje "przeczytaj X, oto TLDR, pomyśl jak wdrożyć"; integracja z kalendarzem.
- Bot Telegram do szybkich akcji z telefonu (bez otwierania webu).
- Eksport wszystkiego do plików markdown (backup, portability, ścieżka do trybu offline w v2).

### Guardrails

- **Prywatność**: dane użytkownika i pamięć AI są dostępne tylko jemu. Nie wyciekają do osób trzecich, nie są używane do trenowania modeli. Pamięć osobista może być bardzo intymna — to obowiązuje od MVP.
- **Naturalny rytm proaktywnych przypomnień, NIE harmonogram**: AI wraca do zaniedbanych celów/marzeń z **losowym tempem**, tak jak znajomy, który po miesiącu pyta "hej, ruszyłeś tę rzecz, o której gadaliśmy?". Nie codziennie o tej samej godzinie. Nie powiadomienie push przy każdym otwarciu. Częstotliwość ma czuć się **organicznie**, nie jak scheduler. (To jest jednocześnie guardrail przeciw spamowi i kluczowy element insightu produktu — losowość JEST cechą, nie błędem.)
- Trwałość danych — padnięcie aplikacji ani błąd nie kasuje wpisanych zadań, celów ani pamięci AI.

## Timeline acknowledgment

Acknowledged on 2026-05-24: ~7-tygodniowy MVP po godzinach wymaga sustained dedication; użytkownik świadomie zaakceptował koszt. Surfaced trade-off: flow 5-krokowy z proaktywną pamięcią AI nie zmieści się w 3 tygodniach — wybrano commit zamiast scope-down.

## Functional Requirements

### Konto i tożsamość

- FR-001: Użytkownik może założyć konto (email + hasło lub magic link). Priority: must-have
  > Socrates: Counter-argument rozważony: "magic link to friction / OAuth-Google byłby standardowy". Rozstrzygnięcie: stoi — metoda auth to detal implementacyjny, kluczowe że konto istnieje (pamięć AI wymaga tożsamości).

- FR-002: Użytkownik może zalogować się i wylogować. Priority: must-have
  > Socrates: Counter-argument rozważony: "logout jest nice-to-have w MVP". Rozstrzygnięcie: stoi — standardowy auth flow.

### Trzy warstwy zadań

- FR-003: Użytkownik może utworzyć, edytować, ukończyć i usunąć **zadanie bieżące** (krótka treść + termin opcjonalny + kategoria opcjonalna). Priority: must-have
  > Socrates: Counter-argument rozważony: "zadania bieżące są niepotrzebne w MVP — konkurujesz z Todoistem na ich terenie zamiast skupić się na różnicującym obszarze (długoterminowe + marzenia)". Rozstrzygnięcie: zostawione świadomie — aplikacja docelowo ma zastąpić Todoist, więc bieżące są częścią value proposition. Ryzyko zakresu zauważone.

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
  > Socrates: Counter-argument rozważony: "auto-tag = wołanie LLM na każdy wpis = koszt + latencja". Rozstrzygnięcie: stoi treścią — mechanizm tagowania (heurystyka vs LLM vs hybryda) to detal implementacyjny poniżej PRD.

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

- FR-014: Gdy użytkownik odpowie "zaczynam" na proaktywną propozycję, AI proponuje konkretny pierwszy krok — lista 3-5 punktów z własnej wiedzy modelu. **Bez wyszukiwania w internecie w MVP.** Priority: must-have
  > Socrates: Counter-argument rozważony: "wyszukiwanie w internecie to duży kawałek agent tools, lepiej v2". Rozstrzygnięcie: zmieniono — drop internetu z MVP, AI używa tylko własnej wiedzy. Internet w roadmapie post-MVP.

### Kontrola użytkownika nad proaktywnością

- FR-015: Użytkownik może w dowolnym momencie wymusić proaktywną propozycję ("daj mi coś teraz") — AI wybiera zaniedbaną pozycję z bazy stosując te same reguły co automat (bilansowanie kategorii, priorytety). Priority: must-have
  > Socrates: Counter-argument rozważony: "manual trigger psuje filozofię 'znajomy po miesiącu', user będzie kompulsywnie klikać". Rozstrzygnięcie: stoi — user czasem chce kontroli; automat dalej istnieje jako "znajomy". Limit (np. cooldown) może być detalem implementacyjnym.

- FR-016: Użytkownik może (opcjonalnie) oznaczyć 3-5 kategorii jako priorytetowe. AI bilansuje proaktywne propozycje głównie wewnątrz priorytetowych kategorii; pozostałe wraca rzadziej. Domyślnie wszystkie kategorie są równe. Priority: must-have
  > Socrates: Counter-argument rozważony: "statyczne priorytety się zdezaktualizują — lepiej żeby AI sam wykrywał z aktywności usera". Rozstrzygnięcie: stoi treścią — explicit priorytety od usera są czystym sygnałem; AI-detected priority może być dodany w roadmapie ale nie zastępuje user-explicit.

### Dostępność offline

- FR-017: Aplikacja jako PWA pozwala zalogowanemu użytkownikowi przeglądać (read-only) swoje zapisane pozycje wszystkich trzech warstw bez połączenia z internetem. Edycja pozycji oraz wszystkie funkcje AI (auto-tag, propozycje proaktywne, pomoc w realizacji) wymagają online. Priority: must-have

## Business Logic

**Aplikacja sama, w nieregularnym rytmie, przypomina użytkownikowi o jego zaniedbanych celach i marzeniach — wybierając co, kiedy i z której domeny życia, na podstawie pamięci o nim.**

Reguła konsumuje cztery rodzaje wejścia: (1) pozycje wpisane przez użytkownika z ich metadanymi — typ warstwy, kategoria, horyzont czasowy, status, historia interakcji; (2) pamięć o użytkowniku zbudowaną z odpowiedzi onboardingowych i ukończonych pozycji; (3) historię proaktywnych propozycji aplikacji — co już zostało zaproponowane, kiedy, z jakim wynikiem (zaczynam / nie teraz / przypomnij za / nigdy); (4) ewentualne oznaczenie kategorii priorytetowych.

Wyjściem reguły jest **konkretna propozycja w naturalnym języku**: cytat zaniedbanej pozycji + zaproszenie do działania ("Hej, dwa miesiące temu wpisałeś marzenie X — chcesz zrobić pierwszy krok?"). Decyzja dotyczy czterech wymiarów jednocześnie: **co** (która pozycja), **kiedy** (rytm — losowo, organicznie, ~1 propozycja na kilka dni), **skąd** (która kategoria — bilansowanie domen + priorytetowość), **jak** (sformułowanie odwołujące się do pamięci użytkownika).

Użytkownik spotyka się z regułą w dwóch miejscach: (a) **automatycznie** — aplikacja wraca samodzielnie, jak znajomy po miesiącu; (b) **na żądanie** — przycisk "wyciągnij mi coś teraz" stosuje tę samą regułę z pominięciem rytmu. To nie jest CRUD: użytkownik nie pytany dostaje aktywną decyzję o czym ma teraz pomyśleć — coś, czego siedząc nad statyczną listą w Obsidianie sam by nie zrobił.

## Non-Functional Requirements

- Treść pozycji oraz pamięć AI nie wyciekają do osób trzecich; nie są używane do trenowania zewnętrznych modeli ani dzielone z innymi użytkownikami systemu. Obowiązuje od MVP — to twardy guardrail osobistych danych.
- Akcje CRUD (utworzenie, edycja, oznaczenie pozycji jako ukończonej) skutkują widocznym efektem w interfejsie w mniej niż 500 ms. Operacje wymagające wywołania modelu AI zaczynają wyświetlać widoczną zwrotną informację o postępie w mniej niż 500 ms, niezależnie od końcowej długości operacji.
- Po awarii klienta, restarcie urządzenia lub utracie połączenia użytkownik po ponownym zalogowaniu odzyskuje 100% wpisanych pozycji i 100% pamięci AI. Żadna zapisana akcja nie znika cicho.
- Interfejs i komunikacja AI prowadzone są wyłącznie po polsku w MVP. Inne języki są poza zakresem wersji pierwszej.

## Non-Goals

- **AI nie zarządza kalendarzem ani nie optymalizuje czasu użytkownika.** Aplikacja może mieć wgląd w cele i pozycje, ale nie planuje "gdzie wcisnąć następne zadanie" — to świadoma różnica od Motion/Reclaim/Akiflow.
- **Brak czatu głosowego z AI.** Komunikacja wyłącznie tekstowa w MVP.
- **Brak natywnej aplikacji mobilnej.** PWA / web responsywny pokrywa zarówno desktop, jak i mobile.
- **Brak współdzielonych workspace'ów, rodzin, ról.** Aplikacja jest single-tenant per użytkownik; rodzina/zespół to roadmapa post-MVP.
- **AI w MVP nie szuka informacji w internecie.** Pomoc w realizacji (FR-014) używa tylko własnej wiedzy modelu — bez agent-tools z dostępem do web.
- **Brak integracji z zewnętrznymi kalendarzami** (Google Calendar, Outlook, etc.). Aplikacja jest "czystym todo + planowaniem", nie sync'ującym do innych systemów.
- **Brak komercjalizacji / paywallu w MVP.** Aplikacja w wersji pierwszej darmowa; monetyzacja po walidacji insightu.
- **Brak pełnego offline-first z dwukierunkowym sync.** MVP ma tylko read-only offline (FR-017); edycja offline i synchronizacja md ↔ chmura to roadmapa.
- **Brak edycji listy 11 kategorii przez użytkownika.** User-defined taxonomy to roadmapa post-MVP.

## Forward: tech-stack (informational — out of PRD scope)

> Notatki kierunkowe pod tech-stack-selector — NIE część PRD.

- Aplikacja webowa jako **PWA** (offline-read z service worker; dodawanie na ekran główny mobile).
- **Monorepo**: backend i frontend rozdzielone, ale w jednym repozytorium (decyzja autora).
- Język interfejsu: polski (MVP single-language → wpływa na pamięć AI i prompty).
- Komunikacja z LLM (auto-tag, propozycje proaktywne, pomoc w realizacji, pytania onboardingowe) wymaga dostawcy AI z dobrym wsparciem polskiego.
- Pamięć AI o użytkowniku — szczegóły mechanizmu (RAG vs system prompt vs structured profile vs hybryda) do rozstrzygnięcia w tech-stack-selector.

## Forward: technical-roadmap (informational — out of PRD scope)

> Capability post-MVP. Nie część PRD, ale captured żeby nie zaginęło.

- **v2**: bot Telegram do szybkich akcji z telefonu.
- **v2**: integracja AI z internetem (agent tools — wyszukiwanie, podpowiedzi z linków).
- **v2**: dodawanie zadania z dowolnego źródła (wklejony link → AI generuje "przeczytaj X, oto TLDR").
- **v2**: pełen offline-first z lokalnymi plikami md i dwukierunkowym sync.
- **v2**: lokalny model AI dla trybu offline.
- **v2**: integracja z zewnętrznymi kalendarzami (Google Calendar).
- **v2**: edytowalne kategorie przez użytkownika; user-defined taxonomy.
- **v2**: współdzielone workspace'y dla rodzin / zespołów.
- **v2**: monetyzacja (subskrypcja / one-time).
- **v3**: voice chat z AI.

## Quality cross-check

Wszystkie 5 elementów obecnych — soft gate przepuszczony.

- Access Control: present
- Business Logic: present (jednozdaniowa reguła zapisana)
- Project artifacts: present (shape-notes.md, frontmatter checkpoint)
- Timeline-cost acknowledgment: present (~7 tyg po godzinach, świadomie zaakceptowane)
- Non-Goals: present (9 explicit non-goals)

Brak gapów do mirrored do Open Questions w PRD.

## Open Questions (do rozstrzygnięcia później, niewymagające block)

1. **Jaki dostawca AI dla auto-tagowania (FR-008)?** — Wpływa na koszt + latencję. Decyzja w tech-stack-selector. Owner: autor.
2. **Czy "naturalny rytm" proaktywnych propozycji to algorytm losowy, ML, czy reguły heurystyczne?** — Implementation detail; nie blokuje PRD. Owner: autor (faza implementacji).
3. **Jak konkretnie kategorie priorytetowe (FR-016) wpływają na bilansowanie (FR-012)?** — Algorytmiczna decyzja; może być zwykła waga ×N na propozycje z kategorii priorytetowych. Owner: autor.
4. **Czy onboarding-pytania (FR-009) generuje AI dynamicznie, czy są stałą listą?** — Można zacząć od stałych 4 pytań; iterować potem.




## User Stories

### US-01: Pierwsza proaktywna propozycja zaniedbanego marzenia

- **Given** zalogowany użytkownik, który tydzień wcześniej wpisał marzenie "pojechać do Japonii kiedyś" w kategorii Czas wolny i hobby i od tamtego czasu nic z nim nie zrobił
- **When** AI rozpoznaje moment do proaktywnego wracania (naturalny rytm) i wybiera tę pozycję jako zaniedbaną
- **Then** użytkownik widzi propozycję w formie wiadomości od AI: "Hej, tydzień temu wpisałeś marzenie o Japonii — chcesz dziś zrobić pierwszy krok?"

#### Acceptance Criteria

- Propozycja zawiera odniesienie do **konkretnej** wpisanej pozycji (cytat tekstu marzenia), nie ogólnik
- Propozycja zawiera 3 przyciski akcji: "zaczynam" / "nie teraz" / "nigdy o to nie pytaj"
- Kliknięcie "zaczynam" wywołuje FR-014 (AI proponuje pierwszy krok)
- Kliknięcie "nie teraz" → AI nie pyta o tę pozycję przez kolejny tydzień (ale może pytać o inne)
- Kliknięcie "nigdy o to nie pytaj" → pozycja jest oznaczona jako "wycofana z proaktywności"; nie jest usuwana ani ukończona
- Rytm jest **losowy** — dwa kolejne wybory nie idą jeden po drugim ani w sztywnym oknie; oczekiwana częstotliwość maksymalnie ~1 propozycja na 2-7 dni
- Pierwsza propozycja w sesji użytkownika jest z innej kategorii niż 2-3 poprzednie propozycje (bilansowanie z FR-012)




