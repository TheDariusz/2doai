---
project: "2do AI"
version: 1
status: draft
created: 2026-06-13
updated: 2026-06-14
prd_version: 1
main_goal: market-feedback
top_blocker: decisions
---

# Roadmap: 2do AI

> Wyprowadzone z `context/foundation/prd.md` (v1) + auto-zbadany baseline kodu (2026-06-13).
> Edytuj w miejscu; archiwizuj, gdy zdezaktualizowane.
> Slice'y poniżej są w kolejności zależności. Tabela „At a glance" jest indeksem.
> Nagłówki sekcji są po angielsku (kontrakt dla narzędzi `/10x-plan`); treść po polsku — tak jak w PRD.

## Vision recap

Osoby planujące długoterminowo wpisują cele i marzenia raz, a potem rzadko do nich wracają — klasyczne todo i markdown są pasywne wobec długoterminowych celów i marzeń „kiedyś". 2do AI odwraca to: AI jest aktywnym partnerem, który **sam, w naturalnym (nieregularnym) rytmie, wraca z propozycją dotyczącą konkretnej zaniedbanej pozycji** („w styczniu mówiłeś o prawie jazdy — minęło 8 miesięcy, zacznijmy"). Trzy warstwy zadań (bieżące / długoterminowe / marzenia) w 11 domenach życia, z proaktywnymi przypomnieniami — to luka, której konkurenci optymalizujący kalendarz (Motion, Reclaim, Akiflow) nie wypełniają.

## North star

**S-05: AI sama wraca z propozycją zaniedbanego marzenia/celu w naturalnym rytmie (US-01)** — to kamień milowy walidacji całego produktu, dobrany pod cel sekwencjonowania (feedback od użytkownika): jeśli proaktywna pętla naprawdę działa i czuje się jak troskliwy znajomy, reszta produktu ma sens; jeśli nie — nie ma. Umieszczona tak wcześnie, jak pozwalają jej zależności.

> **Gwiazda przewodnia** = najmniejszy kompletny (end-to-end) przepływ, którego udane dostarczenie udowadnia rdzenną hipotezę produktu; umieszczany tak wcześnie, jak pozwalają zależności, bo wszystko inne ma znaczenie tylko jeśli to działa.

## At a glance

| ID   | Change ID                  | Outcome (użytkownik może …)                                          | Prerequisites      | PRD refs                     | Status   |
| ---- | -------------------------- | -------------------------------------------------------------------- | ------------------ | ---------------------------- | -------- |
| F-01 | persistence-baseline       | (fundament) trwała warstwa danych + zasiane 11 kategorii             | —                  | NFR (trwałość), FR-007       | done     |
| F-02 | ai-memory-integration      | (fundament) klient LLM + zrębowy mechanizm pamięci + polityka prywatności | F-01          | NFR (prywatność), Open Q2    | ready    |
| S-01 | account-and-auth           | założyć konto, zalogować/wylogować się; trasy bramkowane             | F-01               | FR-001, FR-002               | proposed |
| S-02 | goals-and-dreams           | tworzyć/edytować/kończyć cel długoterminowy i marzenie + kategoria   | S-01, F-01         | FR-004, FR-005, FR-007       | proposed |
| S-03 | ai-memory-seed             | zasiać pamięć AI onboardingiem; ukończone pozycje ją wzbogacają      | F-02, S-02         | FR-009, FR-010               | proposed |
| S-04 | proactive-proposal-engine  | na żądanie dostać propozycję, odpowiedzieć i otrzymać pierwszy krok   | F-02, S-02, S-03   | FR-012, FR-013, FR-014, FR-015 | proposed |
| S-05 | natural-rhythm-return      | **(gwiazda)** AI sama wraca w losowym rytmie, bilansując kategorie   | S-04, F-02         | FR-011, US-01                | proposed |
| S-06 | priority-categories        | oznaczyć 3-5 kategorii priorytetowych wpływających na bilansowanie   | S-04               | FR-016                       | proposed |
| S-07 | current-tasks              | tworzyć/edytować/kończyć/usuwać zadanie bieżące                      | S-01, F-01         | FR-003                       | proposed |
| S-08 | unified-three-layer-view   | widzieć 3 warstwy w jednym widoku i filtrować po warstwie/kategorii  | S-02, S-07         | FR-006, FR-007               | proposed |
| S-09 | ai-category-autotag        | dostać sugestię kategorii od AI przy tworzeniu pozycji              | F-02, S-02         | FR-008                       | proposed |
| S-10 | offline-read-only          | przeglądać (read-only) zapisane pozycje 3 warstw bez internetu       | S-02, S-07         | FR-017                       | proposed |

## Streams

Pomoc nawigacyjna — grupuje pozycje dzielące łańcuch Prerequisites. Kanoniczna kolejność wciąż żyje w grafie zależności niżej; ta tabela to proponowana kolejność czytania równoległych torów.

| Stream | Theme                          | Chain                                              | Note                                                                              |
| ------ | ------------------------------ | -------------------------------------------------- | --------------------------------------------------------------------------------- |
| A      | Konto, pozycje, widok          | `F-01` → `S-01` → `S-02` / `S-07` → `S-08`         | Szkielet danych; `S-10` (offline) odgałęzia się od `S-02`/`S-07`. Buildowalny niezależnie od `F-02` (równoległy tor). |
| B      | Proaktywna pętla AI (gwiazda)  | `F-02` → `S-03` → `S-04` → `S-05` → `S-06`         | Ścieżka gwiazdy przewodniej; łączy się ze Stream A w `S-02`; `F-02` wymaga `F-01`. |
| C      | Auto-tag AI                    | `S-09`                                             | Odgałęzia się od `F-02` + `S-02`, równolegle do pętli; niezależne od pamięci/propozycji. |

## Baseline

Co już jest w kodzie na 2026-06-13 (auto-zbadane + potwierdzone przez autora). Fundamenty poniżej zakładają obecność tych elementów i ich NIE odtwarzają.

- **Frontend:** partial — React 19.2 + Vite 8 + TypeScript (`frontend/vite.config.ts`); tylko demo `App.tsx` (licznik, `src/App.tsx:1-122`). Brak routingu, brak PWA/service worker.
- **Backend / API:** partial — Spring Boot 4.0.6 (webmvc); tylko `PingController` (`/api/v1/ping`, smoke test) + `Application.java`. Brak warstwy serwisów/repozytoriów/domeny.
- **Data:** absent — brak sterownika Postgres / JPA / Hibernate w `pom.xml`; brak encji, repozytoriów, migracji (Flyway/Liquibase); brak konfiguracji DB.
- **Auth:** absent — brak Spring Security; brak `SecurityFilterChain`, endpointów login/register, middleware. (`tech-stack.md`: `has_auth=true`, ale niepodłączone.)
- **Deploy / infra:** present — `backend/Dockerfile` + `backend/fly.toml` (app „2doai", AMS, always-on); 2 workflow GitHub Actions (frontend→Cloudflare Pages, backend→Fly); reverse-proxy Pattern B `frontend/functions/api/[[path]].ts`; `context/foundation/deployment-runbook.md`.
- **Observability:** partial — Spring Actuator + `/actuator/health` wystawione i probowane przez Fly; brak Sentry/Datadog/OTel/Micrometer, brak strukturalnego logowania, frontend bez observability. (Dla MVP wystarczające — bez osobnego fundamentu.)

## Foundations

### F-01: Fundament trwałości danych

- **Outcome:** (fundament) Postgres + Spring Data JPA/Hibernate + narzędzie migracji (Flyway) podłączone do istniejącego backendu; zasiana stała lista 11 kategorii jako dane referencyjne. Brak efektu widocznego dla użytkownika.
- **Change ID:** persistence-baseline
- **PRD refs:** NFR (trwałość: 100% odzysk po awarii); FR-007 (zasiew listy 11 kategorii — pokrycie FR-007 jako capability w S-02/S-08)
- **Unlocks:** S-01 (konto), S-02, S-07 oraz każdy slice zapisujący dane; udostępnia 11 kategorii (FR-007) dla S-02/S-08/S-09
- **Prerequisites:** — (deploy/infra już obecne; ten fundament dokłada warstwę danych do gotowego szkieletu backendu)
- **Parallel with:** —
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Bez warstwy danych żadna pozycja nie jest trwała — cała roadmapa stoi na tym fundamencie. Zakres minimalny (połączenie + migracje + seed kategorii), bez encji domenowych — te definiuje pierwszy slice, który integruje DB przez realne zachowanie użytkownika.
- **Status:** done

### F-02: Fundament integracji AI + pamięci

- **Outcome:** (fundament) wybrany dostawca AI + klient LLM podłączony; zrębowy mechanizm pamięci AI o użytkowniku; ustalona polityka prywatności (dane i pamięć nie trenują zewnętrznych modeli — twardy guardrail). Brak efektu widocznego dla użytkownika.
- **Change ID:** ai-memory-integration
- **PRD refs:** NFR (prywatność danych/pamięci; widoczny feedback AI <500ms); PRD Otwarte pytanie 2
- **Unlocks:** S-03 (pamięć), S-04 (silnik propozycji), S-09 (auto-tag); redukuje Open Roadmap Question #1 (dostawca AI + mechanizm pamięci)
- **Prerequisites:** F-01 (mechanizm pamięci najpewniej persystuje do warstwy danych)
- **Parallel with:** S-01 (oba zależą tylko od F-01; żaden nie blokuje drugiego)
- **Blockers:** —
- **Unknowns:** — (kluczowa decyzja rozstrzygnięta 2026-06-13)
- **Resolved:** Dostawca = OpenRouter przed first-party Anthropic; modele rozdzielone (`anthropic/claude-haiku-4.5` auto-tag, `anthropic/claude-sonnet-4.6` propozycje); prywatność „no-training" (spełnia guardrail, bez ZDR); mechanizm pamięci = profil strukturalny + log epizodyczny wstrzykiwany do kontekstu (bez bazy wektorowej w MVP). Pełna decyzja + implikacje integracyjne: `context/foundation/ai-provider.md`.
- **Risk:** Decyzja podjęta; ryzyko zredukowane do wykonania. Pozostałe do weryfikacji przy implementacji (zob. `ai-provider.md`): wsparcie `json_schema` `strict` dla Haiku 4.5 na OpenRouter (fallback: tool-calling / prompted JSON / Sonnet do tagu) oraz tania A/B jakości polskiego Haiku vs Sonnet. Integracja przez OpenAI-compatible Chat Completions → Spring `RestClient` za portem `LlmClient` (nie Anthropic Java SDK).
- **Status:** ready

## Slices

### S-01: Konto i uwierzytelnienie

- **Outcome:** Użytkownik może założyć konto (email + hasło lub magic link), zalogować się i wylogować; wejście na bramkowaną trasę bez logowania przekierowuje do logowania/rejestracji. Ustanawia Spring Security, kontrakt izolacji danych per-użytkownik oraz szkielet aplikacji frontowej (routing + proxy `/api` w dev).
- **Change ID:** account-and-auth
- **PRD refs:** FR-001, FR-002 (oraz sekcja Access Control — brak trybu anonimowego, bramkowanie tras)
- **Prerequisites:** F-01
- **Parallel with:** F-02
- **Blockers:** —
- **Unknowns:**
  - Model sesji: cookie vs token JWT — nierozstrzygnięty (CLAUDE.md: potwierdzić przed implementacją auth; wybór wpływa na PWA/offline i proxy Pattern B). — Owner: autor. Block: no (rozstrzygalne na etapie `/10x-plan`).
- **Risk:** Standardowy flow auth; ryzyko skupia się w wyborze modelu sesji, który dotyka same-origin Pattern B i przyszły tryb offline (FR-017). Ustanawia izolację per-użytkownik konsumowaną przez wszystkie slice'y danych.
- **Status:** proposed

### S-02: Cele długoterminowe i marzenia

- **Outcome:** Użytkownik może utworzyć, edytować i ukończyć cel długoterminowy (treść + horyzont: ten rok / kilka miesięcy) oraz marzenie „kiedyś" (treść, bez ram czasowych), z opcjonalną kategorią wybraną z listy 11 domen.
- **Change ID:** goals-and-dreams
- **PRD refs:** FR-004, FR-005, FR-007
- **Prerequisites:** S-01, F-01
- **Parallel with:** S-07, F-02
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Substrat dla gwiazdy przewodniej — proaktywna pętla działa na celach i marzeniach, więc to one (a nie zadania bieżące) muszą istnieć najpierw. Proste CRUD, niskie ryzyko; kategoria ręczna (auto-tag dochodzi w S-09).
- **Status:** proposed

### S-03: Pamięć AI (seed + wzbogacanie)

- **Outcome:** Użytkownik może (opcjonalnie) odpowiedzieć na 2-4 pytania onboardingowe zasiewające pamięć AI; każda ukończona pozycja cicho wzbogaca tę pamięć. Krok onboardingu jest pomijalny — pamięć rośnie organicznie z ukończeń.
- **Change ID:** ai-memory-seed
- **PRD refs:** FR-009, FR-010
- **Prerequisites:** F-02, S-02
- **Parallel with:** S-07, S-09
- **Blockers:** —
- **Unknowns:**
  - Pytania onboardingowe (FR-009) — stała lista 4 czy generowane dynamicznie przez AI? — Owner: autor. Block: no (można zacząć od stałych i iterować).
- **Risk:** Pamięć to wejście #2 reguły proaktywnej (Business Logic). Kształt pamięci zależy od mechanizmu wybranego w F-02 — dlatego slice jest po F-02. Jakość selekcji „co istotne" to property AI, nie kontraktu FR.
- **Status:** proposed

### S-04: Silnik propozycji (wyzwalany ręcznie)

- **Outcome:** Użytkownik może na żądanie („daj mi coś teraz") dostać propozycję AI cytującą konkretną zaniedbaną pozycję, wybraną z bilansowaniem kategorii; odpowiedzieć „zaczynam / nie teraz / przypomnij za X (7/30/90 dni) / nigdy"; a po „zaczynam" otrzymać 3-5 punktowy pierwszy krok z własnej wiedzy AI (bez internetu w MVP). „Nigdy" przenosi pozycję do widoku „wycofanych" z możliwością przywrócenia.
- **Change ID:** proactive-proposal-engine
- **PRD refs:** FR-012, FR-013, FR-014, FR-015
- **Prerequisites:** F-02, S-02, S-03
- **Parallel with:** S-07, S-08, S-09
- **Blockers:** —
- **Unknowns:**
  - Jak bilansowanie kategorii (FR-012) waży wybór pozycji — np. heurystyka odległości czasowej + rotacja domen? — Owner: autor. Block: no (detal algorytmiczny dla `/10x-plan`).
- **Risk:** Tu materializuje się **najbardziej ryzykowne założenie** produktu (to, którego nietrafność najbardziej zagraża sensowi projektu): czy AI trafnie wybierze zaniedbaną pozycję i sformułuje przekonującą, osadzoną w pamięci propozycję. Budowane przed schedulerem (S-05), by zwalidować jakość propozycji tanio — zgodnie z celem „feedback od użytkownika".
- **Status:** proposed

### S-05: Automatyczny powrót w naturalnym rytmie  *(gwiazda przewodnia)*

- **Outcome:** AI sama, w naturalnym (losowym) rytmie — nie codziennie o tej samej porze, oczekiwane ~1 propozycja na 2-7 dni — wraca do użytkownika z propozycją zaniedbanego celu/marzenia, bilansując kategorie w czasie. Pełen cykl US-01 (propozycja → odpowiedź → pierwszy krok) działa bez udziału użytkownika.
- **Change ID:** natural-rhythm-return
- **PRD refs:** FR-011, US-01
- **Prerequisites:** S-04, F-02
- **Parallel with:** S-06, S-09
- **Blockers:** —
- **Unknowns:**
  - Naturalny rytm — algorytm losowy z biasem, reguły heurystyczne czy ML? Dwie propozycje nie idą jedna po drugiej ani w sztywnym oknie. — Owner: autor (faza implementacji). Block: no (detal implementacyjny; nie blokuje planowania slice'a).
- **Risk:** Gwiazda przewodnia: dokłada autonomiczny losowy rytm (background job) na silnik z S-04. Ryzyko: rytm ma czuć się organicznie, jak znajomy po miesiącu — nie jak scheduler. Losowość JEST cechą produktu (Guardrails), nie błędem.
- **Status:** proposed

### S-06: Kategorie priorytetowe

- **Outcome:** Użytkownik może opcjonalnie oznaczyć 3-5 kategorii jako priorytetowe; AI bilansuje proaktywne propozycje głównie wewnątrz nich, a pozostałe wraca rzadziej. Domyślnie wszystkie kategorie są równe.
- **Change ID:** priority-categories
- **PRD refs:** FR-016
- **Prerequisites:** S-04 (stosuje się też do automatycznych propozycji z S-05)
- **Parallel with:** S-05, S-09
- **Blockers:** —
- **Unknowns:**
  - Jak priorytety przekładają się na bilansowanie (FR-012) — np. waga ×N na propozycje z kategorii priorytetowych? — Owner: autor. Block: no.
- **Risk:** Rozszerzenie bilansowania; niskie ryzyko, czysto opcjonalne dla użytkownika. Explicit priorytety to czysty sygnał (AI-detected priority to roadmapa post-MVP, nie zastępuje user-explicit).
- **Status:** proposed

### S-07: Zadania bieżące

- **Outcome:** Użytkownik może utworzyć, edytować, ukończyć i usunąć zadanie bieżące (krótka treść + opcjonalny termin + opcjonalna kategoria).
- **Change ID:** current-tasks
- **PRD refs:** FR-003
- **Prerequisites:** S-01, F-01
- **Parallel with:** S-02, S-03, S-04, S-05, S-09
- **Blockers:** —
- **Unknowns:** —
- **Risk:** PRD sam zaznacza ryzyko zakresu (konkurencja z klasycznym todo na jego terenie). Nieróżnicujące — dlatego po pętli proaktywnej; możliwe równolegle przez osobny przebieg agenta. Świadomie zostawione (docelowo zastępuje klasyczne todo).
- **Status:** proposed

### S-08: Jednolity widok trzech warstw

- **Outcome:** Użytkownik widzi wszystkie trzy warstwy (bieżące / długoterminowe / marzenia) w jednym widoku i może filtrować po warstwie oraz po kategorii.
- **Change ID:** unified-three-layer-view
- **PRD refs:** FR-006, FR-007
- **Prerequisites:** S-02, S-07
- **Parallel with:** S-04, S-05, S-09, S-10
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Widok zbiorczy wymaga pozycji wszystkich warstw (S-02 + S-07). Poza ścieżką gwiazdy (propozycja przychodzi do użytkownika, nie przez listę) — możliwy równolegle. Jednolity widok pozwala zobaczyć całość życia; filtry obsługują skupienie.
- **Status:** proposed

### S-09: Auto-tag kategorii przez AI

- **Outcome:** Przy tworzeniu pozycji AI proponuje kategorię na podstawie treści; użytkownik akceptuje propozycję, wybiera inną lub ignoruje. Auto-tag stosowany cicho, gdy nic nie wybrano; możliwy do zmiany później.
- **Change ID:** ai-category-autotag
- **PRD refs:** FR-008
- **Prerequisites:** F-02, S-02
- **Parallel with:** S-03, S-04, S-05, S-06, S-07, S-10
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Niezależne od pętli pamięci/propozycji (równoległe). Ryzyko kosztu/latencji wołania AI przy każdym tworzeniu pozycji — detal implementacyjny poniżej PRD.
- **Status:** proposed

### S-10: Offline read-only

- **Outcome:** Użytkownik może przeglądać (read-only) zapisane pozycje wszystkich trzech warstw bez połączenia z internetem. Edycja pozycji oraz wszystkie funkcje AI (auto-tag, propozycje, pomoc w realizacji) wymagają połączenia.
- **Change ID:** offline-read-only
- **PRD refs:** FR-017
- **Prerequisites:** S-02, S-07
- **Parallel with:** S-03, S-04, S-05, S-06, S-08, S-09
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Read-only offline, bez AI; PWA service worker + cache. Poza ścieżką gwiazdy — możliwy na końcu lub równolegle. Wybór modelu sesji z S-01 wpływa na cache'owanie tras offline.
- **Status:** proposed

## Backlog Handoff

| Roadmap ID | Change ID                  | Suggested issue title                                  | Ready for `/10x-plan` | Notes |
| ---------- | -------------------------- | ------------------------------------------------------ | --------------------- | ----- |
| F-01       | persistence-baseline       | Podłącz Postgres + JPA + Flyway, zasiej 11 kategorii   | yes                   | Korzeń całej roadmapy; pierwszy ruch (F-02 też gotowy, ale zależy od F-01). Uruchom `/10x-plan persistence-baseline`. |
| F-02       | ai-memory-integration      | Podłącz klient LLM (OpenRouter/Anthropic) + zrębowy profil pamięci + polityka prywatności | yes               | Odblokowane — decyzja dostawcy/modeli/pamięci podjęta; zob. `context/foundation/ai-provider.md`. Uruchom `/10x-plan ai-memory-integration`. |
| S-01       | account-and-auth           | Konto: rejestracja, logowanie, wylogowanie, bramkowanie tras | no              | Czeka na F-01; otwarta decyzja cookie vs JWT (nieblokująca). |
| S-02       | goals-and-dreams           | CRUD celów długoterminowych i marzeń z kategorią       | no                    | Czeka na S-01 + F-01. Substrat gwiazdy. |
| S-03       | ai-memory-seed             | Pamięć AI: onboarding seed + wzbogacanie z ukończeń    | no                    | Czeka na F-02 (ready, niezaimplementowany) + S-02. |
| S-04       | proactive-proposal-engine  | Silnik propozycji + odpowiedzi + pierwszy krok (ręczny trigger) | no            | Czeka na F-02 (ready, niezaimplementowany) + S-02 + S-03. Tu waliduje się jakość propozycji. |
| S-05       | natural-rhythm-return      | Automatyczny powrót w naturalnym rytmie (gwiazda)      | no                    | Czeka na S-04 + F-02. Gwiazda przewodnia. |
| S-06       | priority-categories        | Kategorie priorytetowe wpływające na bilansowanie      | no                    | Czeka na S-04. Refinement. |
| S-07       | current-tasks              | CRUD zadań bieżących                                    | no                    | Czeka na S-01 + F-01. Nieróżnicujące; możliwe równolegle. |
| S-08       | unified-three-layer-view   | Jednolity widok 3 warstw z filtrami                    | no                    | Czeka na S-02 + S-07. |
| S-09       | ai-category-autotag        | Auto-tag kategorii przez AI przy tworzeniu             | no                    | Czeka na F-02 (ready, niezaimplementowany) + S-02. Równoległe do pętli. |
| S-10       | offline-read-only          | Offline read-only (PWA) dla 3 warstw                   | no                    | Czeka na S-02 + S-07. |

## Open Roadmap Questions

1. ~~**Dostawca AI + mechanizm pamięci AI**~~ — **ROZSTRZYGNIĘTE (2026-06-13).** OpenRouter + first-party Anthropic; modele `anthropic/claude-haiku-4.5` (auto-tag) + `anthropic/claude-sonnet-4.6` (propozycje); prywatność „no-training"; pamięć = profil strukturalny + log epizodyczny wstrzykiwany do kontekstu. Odblokowuje `F-02` (i pośrednio `S-03`, `S-04`, `S-05`, `S-09`). Pełna decyzja: `context/foundation/ai-provider.md`. (= PRD Otwarte pytanie 2.)
2. **Ballpark skali** (`target_scale`: users / qps / data_volume) — wpływa na wymiarowanie ops/DB, nie na kolejność slice'ów. — Owner: autor. Block: — (informacyjne; PRD wewnętrznie spójny). (= PRD Otwarte pytanie 1.)

> Pytania per-slice (rytm proaktywny → S-05, priorytety×bilansowanie → S-04/S-06, onboarding statyczny/dynamiczny → S-03, cookie vs JWT → S-01) zostają przy swoich slice'ach jako Unknowns.

## Parked

- **AI zarządza kalendarzem / optymalizuje czas** — Non-Goal: świadoma różnica od Motion/Reclaim/Akiflow.
- **Czat głosowy z AI** — Non-Goal MVP (tylko tekst); shape-notes: v3.
- **Natywna aplikacja mobilna** — Non-Goal: responsywne PWA pokrywa desktop i mobile.
- **Współdzielone workspace'y / rodziny / role** — Non-Goal: single-tenant per użytkownik; shape-notes: v2.
- **AI z dostępem do internetu (agent tools, TLDR z linków)** — Non-Goal MVP: FR-014 używa tylko własnej wiedzy AI; shape-notes: v2.
- **Dodawanie zadania z dowolnego źródła (wklejony link → TLDR)** — Kryterium Secondary; zależy od AI+internet; shape-notes: v2.
- **Integracja z zewnętrznymi kalendarzami (Google Calendar)** — Non-Goal + Kryterium Secondary; shape-notes: v2.
- **Bot Telegram do szybkich akcji z telefonu** — Kryterium Secondary (szybkie akcje bez pełnego UI); shape-notes: v2.
- **Pełen offline-first z dwukierunkowym sync md ↔ chmura** — Non-Goal: MVP ma tylko read-only offline (FR-017 → S-10); shape-notes: v2.
- **Lokalny model AI dla trybu offline** — shape-notes: v2.
- **Export / import danych w formacie markdown** — Kryterium Secondary + narracja Access Control; NIE jest must-have FR. Odkładam do walidacji insightu (portability/backup, ścieżka do offline v2).
- **Edytowalne kategorie / user-defined taxonomy** — Non-Goal: lista 11 stała w MVP; shape-notes: v2.
- **Komercjalizacja / paywall / subskrypcja** — Non-Goal MVP: monetyzacja po walidacji insightu; shape-notes: v2.

## Done

(Pusta przy pierwszej generacji. `/10x-archive` dopisuje tu wpis — i przełącza `Status` pozycji na `done` — gdy archiwizowana zmiana ma pasujący `Change ID`. Format:)

- **<Slice ID>: <Outcome>** — Archived <YYYY-MM-DD> → `context/archive/<YYYY-MM-DD-change-id>/`. Lesson: <wskaźnik do lessons.md jeśli jest, lub `—`>.

- **F-01: (fundament) Postgres + Spring Data JPA/Hibernate + narzędzie migracji (Flyway) podłączone do istniejącego backendu; zasiana stała lista 11 kategorii jako dane referencyjne. Brak efektu widocznego dla użytkownika.** — Archived 2026-06-14 → `context/archive/2026-06-13-persistence-baseline/`. Lesson: —.
