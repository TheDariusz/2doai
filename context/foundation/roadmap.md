---
project: "2do AI"
version: 1
status: draft
created: 2026-06-13
updated: 2026-08-01
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
| F-02 | ai-memory-integration      | (fundament) klient LLM + zrębowy mechanizm pamięci + polityka prywatności | F-01          | NFR (prywatność), Open Q2    | done     |
| S-01 | account-and-auth           | założyć konto (email+hasło), zalogować/wylogować, usunąć konto; trasy bramkowane; szkielet frontu | F-01 | FR-001, FR-002, FR-019       | done     |
| S-02 | goals-and-dreams           | tworzyć/edytować/kończyć cel długoterminowy i marzenie + kategoria   | S-01, F-01         | FR-004, FR-005, FR-007       | done     |
| S-03 | ai-memory-seed             | zasiać pamięć AI onboardingiem; ukończone pozycje ją wzbogacają      | F-02, S-02         | FR-009, FR-010               | proposed |
| S-04 | proactive-proposal-engine  | na żądanie dostać propozycję, odpowiedzieć i otrzymać pierwszy krok   | F-02, S-02 (S-03 wycięte) | FR-012, FR-013, FR-014, FR-015 | done     |
| S-05 | natural-rhythm-return      | **(gwiazda)** AI sama wraca w losowym rytmie (e-mail + in-app), bilansując kategorie | S-04, F-02 | FR-011, FR-018, US-01        | proposed |
| S-06 | priority-categories        | oznaczyć 3-5 kategorii priorytetowych wpływających na bilansowanie   | S-04               | FR-016                       | proposed |
| S-07 | current-tasks              | tworzyć/edytować/kończyć/usuwać zadanie bieżące                      | S-01, F-01         | FR-003                       | done     |
| S-08 | unified-three-layer-view   | widzieć 3 warstwy w jednym widoku i filtrować po warstwie/kategorii  | S-02, S-07         | FR-006, FR-007               | done     |
| S-09 | ai-category-autotag        | dostać sugestię kategorii od AI przy tworzeniu pozycji              | F-02, S-02         | FR-008                       | proposed |
| S-10 | offline-read-only          | przeglądać (read-only) zapisane pozycje 3 warstw bez internetu       | S-02, S-07         | FR-017                       | proposed |

> **Fast-path (przegląd 2026-07-07, zaktualizowany 2026-08-01):** najkrótsza ścieżka do gwiazdy: F-02 → S-01 (minimalny: email+hasło, bez magic linka) → S-02 → S-03 → S-04 → S-05. S-07 / S-08 / S-10 świadomie odroczone do czasu walidacji gwiazdy (S-05); S-06 i S-09 równolegle po swoich prerekwizytach. F-02 zmergowany i zarchiwizowany (2026-08-01). S-01 zamknięty (2026-08-03, PR #8): backend i frontend na `master`. **S-02 zmergowany 2026-08-23 (PR #20, `ecb3301`); S-07 zmergowany 2026-08-24 (PR #23, `012609c`). S-08 zmergowany 2026-08-25 (PR #27, `ed4ef2b`); S-04 zamknięty 2026-08-26 obiema połowami (S-04a PR #25, `ec8762d`; S-04b DEV-23) — bez S-03, które zostaje wycięte z okna.** Od 2026-08-23 kolejność do dnia zgłoszenia reguluje sekcja **Deadline plan (2026-09-14)** niżej — fast-path wraca po zgłoszeniu.

## Deadline plan (2026-09-14)

> **Twardy termin:** poniedziałek **2026-09-14** — zgłoszenie projektu do zaliczenia bloku 10xBuilder.
> Ta sekcja **nadpisuje kolejność z Fast-path** do dnia zgłoszenia; potem fast-path wraca (S-05 = gwiazda).
> Ustalone 2026-08-23. Okno: 3 tygodnie, praca wieczorami.

### Minimalne wymagania 10xBuilder → stan na 2026-08-24

| Wymaganie | Stan | Luka |
| --------- | ---- | ---- |
| **CRUD** (dodaj / wylistuj / edytuj / usuń) | ✅ — `GET` / `POST` / `PUT` / `DELETE` na `/api/goals` | domknięte przez DEV-44 (PR #21); twardy delete, nie wycofanie (S-04) ani kasowanie konta (FR-019) |
| **Logika biznesowa** | ✅ — `ProposalSelector`: heurystyka zaniedbania (7/14/30 dni wg warstwy, plus zaległy termin) + bilansowanie kategorii za `POST /api/proposals` | domknięte przez DEV-46 (PR #25, `ec8762d`); silnik nadal czysta Java, ale od S-04b (DEV-23) `LlmClient` ma pierwsze wywołanie produkcyjne — model formułuje propozycję za wybranym wpisem |
| **Testy adresujące ryzyko z dokumentu test-plan** | ✅ — `context/foundation/test-plan.md`: 7 nazwanych ryzyk, każde zmapowane na istniejące suity | domknięte przez DEV-45; żaden test nie pisany „na zapas" |
| **Autentykacja** | ✅ S-01 | — |

### Zakres — „MVP, które da się pokazać i używać"

Ponad minimum wymagań: trzy warstwy w jednym widoku i jedna widoczna funkcja AI.

| # | Pozycja | Wieczory | Po co |
| - | ------- | -------- | ----- |
| 1 | `DELETE /api/goals/{id}` + spec + komentarze + testy + UI | 1 | wymaganie CRUD |
| 2 | `context/foundation/test-plan.md` (`/10x-test-plan`) + mapowanie istniejących testów na ryzyka | 1 | wymaganie testów |
| 3 | S-07 jako **trzecia warstwa** `GoalLayer.TASK` + nullable `due_date` | 2 | codzienna używalność |
| 4 | S-08 — filtry po warstwie i kategorii | 1 | jeden widok |
| 5 | **S-04a** — heurystyka zaniedbania + bilansowanie kategorii + `POST /api/proposals` | 1,5 | wymaganie logiki biznesowej |
| 6 | **S-04b** — LLM formułuje propozycję, 4 odpowiedzi, pierwszy krok | 3 | to, co się pokazuje |
| 7 | README, demo, weryfikacja produkcji | 2 | zgłoszenie |

Razem **~11–12 wieczorów / 3 tygodnie ≈ 4 na tydzień**. Tempo z W31–W32 (28 i 34 commity) to daje; średnia z 12 tygodni (2,2 dnia aktywnego / tydzień) nie. Tydzień W33 (10–16.08) był zerowy — jedna taka przerwa w tym oknie kosztuje pozycję 6.

### Harmonogram

| Okno | Praca | Kamień milowy |
| ---- | ----- | ------------- |
| 08-23 | merge PR #20 (S-02) | ✅ zrobione (`ecb3301`) |
| 08-24 | `DELETE` — repozytorium, serwis, kontroler, `openapi.yaml`, javadoc, testy, UI | **CRUD ✅** (PR #21) |
| 08-24 | `test-plan.md` + mapowanie testów na ryzyka | **Testy ✅** (DEV-45, dwa dni przed oknem) |
| 08-24 | S-07 — trzecia warstwa `GoalLayer.TASK` + nullable `due_date` | **codzienna używalność ✅** (DEV-27, PR #23, `012609c`) — trzy dni przed oknem |
| 08-24 | S-04a — silnik wyboru | 🎯 **komplet wymagań ✅ — `master` zdatny do zgłoszenia** (DEV-46, PR #25, `ec8762d`) — tydzień przed pierwotnym oknem 08-31 – 09-02 |
| 08-24 | S-08 — filtry po warstwie i kategorii | **jeden widok ✅** (DEV-28, PR #27, `ed4ef2b`) — dzień przed oknem 08-25 – 08-30; cały zakres pozycji 1–4 zamknięty jednego wieczoru |
| 09-03 – 09-08 | S-04b — propozycja formułowana przez LLM | 🎯 **aplikacja do pokazania ✅** (DEV-23) — zamknięte 2026-08-26, tydzień przed oknem; S-04 komplet |
| 09-09 – 09-10 | bufor | |
| 09-11 | zamrożenie kodu; deploy i weryfikacja produkcji end-to-end na realnych danych | |
| 09-12 – 09-13 | README, demo, **zgłoszenie w niedzielę** (nie w dniu terminu) | |

### Decyzje i cięcia

- **S-09 wypada z okna.** S-04a pokrywa wymaganie „logika biznesowa" mniejszym kosztem (1,5 vs 2–3 wieczory), a buduje funkcję różnicującą produkt zamiast funkcji obok niego. Auto-tag wraca po zgłoszeniu.
- **S-05 (gwiazda) wypada z okna.** Scheduler + e-mail to jedyna nowa infrastruktura z nieznanymi (dostawca, weryfikacja domeny, cisza nocna, strefa czasowa). Demo odpala propozycję przyciskiem — wygląda tak samo. S-05 jest pierwszą pozycją po zgłoszeniu.
- **S-06 i S-10 wypadają** — nie dotykają żadnego wymagania.
- **S-07 nie dostaje własnego agregatu.** `GoalLayer.TASK` + nullable `due_date` na tabeli `goal`; niezmiennik rozszerza się do: `GOAL` → horyzont wymagany, `DREAM` → horyzont zabroniony, `TASK` → horyzont zabroniony + opcjonalny `due_date`. Cały frontend S-02 (formularz, lista, grupowanie, ukończenie) jest wtedy do ponownego użycia. Rozdzielenie agregatu dopiero, gdy zadania dostaną inny cykl życia (cykliczność, alarmy po terminie).
- **S-04a wyprzedza S-08 (2026-08-24).** S-07 wszedł trzy dni przed swoim oknem, więc zapas idzie na jedyne niespełnione wymaganie — „logika biznesowa" — a nie na polish. Odkrycie, że heurystyka zaniedbania kosztuje więcej niż 1,5 wieczoru, jest odwracalne 25.08 i nieodwracalne 31.08; bramka z 02.09 zostaje bez zmian. Numeracja w tabeli zakresu (poz. 4 = S-08, poz. 5 = S-04a) zostaje, bo odwołuje się do niej proza wyżej — zmienia się kolejność wykonania, nie wycena.
- **`category-contract-guards` zaparkowane** (plan z 2026-08-07, niewykonany). Realny defekt — `CategorySyncCheck` psuje rollback obrazu — ale nie dotyka żadnego wymagania. Po zgłoszeniu.
- **Ceremonia per-slice skrócona** dla pozycji 3–6: `/10x-plan` i TDD zostają, `/10x-impl-review` oraz archiwizacja czekają do po zgłoszeniu.

### Bramki (decyduj, nie rozważaj)

- **02.09 — S-04a niezmergowane** → stop dokładania zakresu; polish i zgłoszenie tego, co jest na `master`.
- ~~**08.09 — S-04b nie działa**~~ → **nieaktualne (2026-08-26).** S-04b zmergowane przed oknem, a szablon tekstowy zbudowany od razu jako ramię `catch (LlmException)` (`ProposalTemplate`), nie trzymany w rezerwie: to ten sam kod w obie strony, a mając go cała pętla jest testowalna bez modelu. Bramka nie ma już czego pilnować.
- **11.09 — zamrożenie bezwzględne.** Po tej dacie na `master` wchodzą wyłącznie poprawki blokujące zgłoszenie. S-04b, jeśli niegotowe, zostaje na gałęzi.

### Ryzyka

- **Produkcja nigdy nie była zweryfikowana end-to-end** — probe'owany jest tylko `/actuator/health`. Neon autosuspend + realne dane to zadanie na 11.09, nie na 13.09.
- **Brak testów e2e** (Playwright niepodpięty). Wymagania 10xBuilder ich nie żądają — świadomie nie dokładamy.
- **README ma 71 linii.** Jeśli cokolwiek w zgłoszeniu jest czytane przez człowieka, to on — jeden wieczór, najtańsze punkty.
- **Tempo.** Jedyne realne ryzyko harmonogramu; wszystkie bramki wyżej istnieją po to, żeby przekroczenie kosztowało zakres, nie termin.

## Streams

Pomoc nawigacyjna — grupuje pozycje dzielące łańcuch Prerequisites. Kanoniczna kolejność wciąż żyje w grafie zależności niżej; ta tabela to proponowana kolejność czytania równoległych torów.

| Stream | Theme                          | Chain                                              | Note                                                                              |
| ------ | ------------------------------ | -------------------------------------------------- | --------------------------------------------------------------------------------- |
| A      | Konto, pozycje, widok          | `F-01` → `S-01` → `S-02` / `S-07` → `S-08`         | Szkielet danych; `S-10` (offline) odgałęzia się od `S-02`/`S-07`. Buildowalny niezależnie od `F-02` (równoległy tor). Fast-path 2026-07-07: `S-07`/`S-08`/`S-10` odroczone do po walidacji `S-05`. |
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

> **Aktualizacja 2026-08-01:** F-01 i F-02 done (zarchiwizowane). F-02 na `master` — port `LlmClient`
> + adapter Spring AI → OpenRouter (guardrail no-training na każdym żądaniu, zweryfikowany żywym
> round-tripem), agregat `AiMemory` (profil + log epizodyczny, migracje `V3` + `V5` FK na `app_user`),
> renderer z limitem ostatnich N epizodów, testy jednostkowe + Testcontainers + bramkowany test live.
> `RegistrationService` tworzy korzeń `AiMemory` w tej samej transakcji co `User`; `LlmClient` nie ma
> jeszcze wywołań produkcyjnych (seamy podłączają S-03/S-04/S-09). S-01 zamknięty (PR #8, 2026-08-03):
> backend oraz frontend (routing, klient API z CSRF, ekrany auth, powłoka z 11 domenami) na `master`;
> PWA (S-10) nadal otwarte.
> ~~Testy biegają wyłącznie w workflow deploy (push na `master`) — brak CI na PR.~~ **Zaadresowane
> (2026-08-03, chore `ci-pipeline`):** quality gate na każdym PR (backend `mvn test`, frontend
> lint/test/build/typecheck), deploy bramkowany za `needs: quality`, skany Trivy (zależności +
> sekrety), Dependabot i agentic AI code review przez OpenRouter.

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
- **Status:** done

## Slices

### S-01: Konto i uwierzytelnienie

- **Outcome:** Użytkownik może założyć konto (email + hasło — decyzja 2026-07-07, magic link post-MVP), zalogować się i wylogować oraz usunąć konto wraz ze wszystkimi danymi (FR-019); wejście na bramkowaną trasę bez logowania przekierowuje do logowania/rejestracji. Ustanawia Spring Security, kontrakt izolacji danych per-użytkownik oraz **jawny szkielet aplikacji frontowej**: routing, klient API, app shell, ekrany logowania/rejestracji (+ proxy `/api` w dev) — największy dotąd niejawny kawałek zakresu, dostarczony w całości.
- **Change ID:** account-and-auth
- **PRD refs:** FR-001, FR-002, FR-019 (oraz sekcja Access Control — brak trybu anonimowego, bramkowanie tras)
- **Prerequisites:** F-01
- **Parallel with:** F-02
- **Blockers:** —
- **Unknowns:**
  - ~~Model sesji: cookie vs token JWT~~ — **ROZSTRZYGNIĘTE (2026-07-22).** Sesja serwerowa w ciasteczku (`HttpOnly; Secure; SameSite=Strict`), Spring Security, sesje in-memory na jednej maszynie Fly — nie JWT. Pełna decyzja + wyzwalacz rewizji: `context/foundation/auth-session-model.md`.
- **Risk:** Standardowy flow auth; ryzyko skupia się w wyborze modelu sesji, który dotyka same-origin Pattern B i przyszły tryb offline (FR-017). Ustanawia izolację per-użytkownik konsumowaną przez wszystkie slice'y danych.
- **Status:** done (2026-08-03) — backend (fazy 1-2, PR #5, #6): Spring Security, agregat `User`, migracja `V4`, rejestracja/logowanie/wylogowanie, usunięcie konta (FR-019), izolacja per-użytkownik, `/api/categories`. Frontend (fazy 3-4, PR #8): routing, klient API z echem `X-XSRF-TOKEN`, ekrany auth, `ProtectedRoute`, powłoka z 11 domenami — zweryfikowane pełną bramką i realnym przebiegiem na żywym backendzie. Otwarty rozjazd kontraktu wyniesiony do DEV-31 (`openapi.yaml` vs 403/401 przy usuwaniu konta oraz lista kodów domen).

### S-02: Cele długoterminowe i marzenia

- **Outcome:** Użytkownik może utworzyć, edytować i ukończyć cel długoterminowy (treść + horyzont: ten rok / kilka miesięcy) oraz marzenie „kiedyś" (treść, bez ram czasowych), z opcjonalną kategorią wybraną z listy 11 domen.
- **Change ID:** goals-and-dreams
- **PRD refs:** FR-004, FR-005, FR-007
- **Prerequisites:** S-01, F-01
- **Parallel with:** S-07, F-02
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Substrat dla gwiazdy przewodniej — proaktywna pętla działa na celach i marzeniach, więc to one (a nie zadania bieżące) muszą istnieć najpierw. Proste CRUD, niskie ryzyko; kategoria ręczna (auto-tag dochodzi w S-09).
- **Status:** done

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
- **Prerequisites:** F-02, S-02 — a nominalnie także S-03, które wypadło z okna. Zrealizowane bez niego: prerekwizytem był *mechanizm* pamięci (F-02), nie jej zawartość, a S-04b sam został jej pierwszym pisarzem.
- **Parallel with:** S-07, S-08, S-09
- **Blockers:** —
- **Unknowns:**
  - Jak bilansowanie kategorii (FR-012) waży wybór pozycji — np. heurystyka odległości czasowej + rotacja domen? — Owner: autor. Block: no (detal algorytmiczny dla `/10x-plan`).
  - Definicja "zaniedbanej" pozycji — heurystyka startowa (np. brak interakcji ≥14 dni dla celów, ≥30 dni dla marzeń)? — Owner: autor. Block: no (= PRD Open Question #6; do ustalenia w `/10x-plan`).
- **Risk:** Tu materializuje się **najbardziej ryzykowne założenie** produktu (to, którego nietrafność najbardziej zagraża sensowi projektu): czy AI trafnie wybierze zaniedbaną pozycję i sformułuje przekonującą, osadzoną w pamięci propozycję. Budowane przed schedulerem (S-05), by zwalidować jakość propozycji tanio — zgodnie z celem „feedback od użytkownika".
- **Status:** done — dwie połowy: **S-04a** (DEV-46, PR #25, `ec8762d`) deterministyczny wybór, **S-04b** (DEV-23) formułowanie przez Sonnet, cztery odpowiedzi, pierwszy krok i ekran. Odchylenia od opisu wyżej, świadome:
  - **S-03 wycięte z okna, S-04 weszło bez niego.** Pamięć AI zapełnia się wyłącznie z odpowiedzi na propozycje (`ai_memory_episode`); profil (`ai_memory_profile_fact`) zostaje pusty do S-03. Prompty renderują pusty blok pamięci zamiast się wywracać.
  - **Propozycja jest trwała, nie efemeryczna.** Zakres świadomie poszerzony: tabela `proposal` + częściowy unikalny indeks pilnujący FR-018 („co najwyżej jedna oczekująca"). Drugie kliknięcie przycisku zwraca tę samą propozycję i **nie** płaci za drugie wywołanie modelu. S-05 dziedziczy tabelę razem ze schedulerem.
  - **„Wycofane" to trzecia wartość filtra z S-08**, nie osobna trasa — wycofanie jest kolejną odpowiedzią na pytanie „które wpisy oglądam".
  - **Zapisany pierwszy krok nie ma powiązania z pozycją, z której wyrósł** — tak brzmi FR-014. Analiza i wyzwalacz powrotu: sekcja **Parked** na dole tego pliku.

### S-05: Automatyczny powrót w naturalnym rytmie  *(gwiazda przewodnia)*

- **Outcome:** AI sama, w naturalnym (losowym) rytmie — nie codziennie o tej samej porze, oczekiwane ~1 propozycja na 2-7 dni — wraca do użytkownika z propozycją zaniedbanego celu/marzenia, bilansując kategorie w czasie. Propozycja jest dostarczana e-mailem z linkiem do aplikacji oraz widoczna w aplikacji (FR-018); w danym momencie co najwyżej jedna oczekująca. Pełen cykl US-01 (propozycja → odpowiedź → pierwszy krok) działa bez udziału użytkownika.
- **Change ID:** natural-rhythm-return
- **PRD refs:** FR-011, FR-018, US-01
- **Prerequisites:** S-04 (done 2026-08-26), F-02
- **Parallel with:** S-06, S-09
- **Blockers:** —
- **Unknowns:**
  - Naturalny rytm — algorytm losowy z biasem, reguły heurystyczne czy ML? Dwie propozycje nie idą jedna po drugiej ani w sztywnym oknie. — Owner: autor (faza implementacji). Block: no (detal implementacyjny; nie blokuje planowania slice'a).
  - Dostawca e-maili transakcyjnych (Resend / Postmark / SES / inny) — pierwsza infrastruktura e-mail w projekcie (FR-018). — Owner: autor. Block: no.
  - Cisza nocna + strefa czasowa użytkownika — kiedy wolno wysłać e-mail z propozycją i skąd znamy strefę. — Owner: autor. Block: no.
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
- **Risk:** PRD sam zaznacza ryzyko zakresu (konkurencja z klasycznym todo na jego terenie). Nieróżnicujące — dlatego po pętli proaktywnej; możliwe równolegle przez osobny przebieg agenta. Świadomie zostawione (docelowo zastępuje klasyczne todo). **Fast-path 2026-07-07: odroczone do po walidacji gwiazdy (S-05).** Uwaga: FR-014 (zapis pierwszego kroku jako zadania bieżącego) tworzy zależność miękką S-04 → encja zadania — jeśli S-04 wyprzedzi S-07, zapis pierwszego kroku dochodzi wraz z S-07.
- **Status:** done (2026-08-24) — PR #23 (`012609c`): `GoalLayer.TASK` + nullable `due_date` na tabeli `goal`, bez osobnego agregatu; frontend S-02 użyty ponownie.

### S-08: Jednolity widok trzech warstw

- **Outcome:** Użytkownik widzi wszystkie trzy warstwy (bieżące / długoterminowe / marzenia) w jednym widoku i może filtrować po warstwie oraz po kategorii.
- **Change ID:** unified-three-layer-view
- **PRD refs:** FR-006, FR-007
- **Prerequisites:** S-02, S-07
- **Parallel with:** S-04, S-05, S-09, S-10
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Widok zbiorczy wymaga pozycji wszystkich warstw (S-02 + S-07). Poza ścieżką gwiazdy (propozycja przychodzi do użytkownika, nie przez listę) — możliwy równolegle. Jednolity widok pozwala zobaczyć całość życia; filtry obsługują skupienie. **Fast-path 2026-07-07: odroczone do po walidacji gwiazdy (S-05).**
- **Status:** done (2026-08-24) — PR #27 (`ed4ef2b`): jeden widok trzech warstw, oba filtry (warstwa, kategoria) trzymane w query stringu i stosowane w przeglądarce. `GET /goals` zostaje nieprzefiltrowane i niestronicowane — obiecany wcześniej kontrakt filtrów wycofany z `openapi.yaml`, stronicowanie ma wyzwalacz (~500 pozycji / ~250 kB), nie termin. Przy okazji adresy URL przeszły na angielskie, małe litery (`/cele` → `/goals`).

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
- **Risk:** Read-only offline, bez AI; PWA service worker + cache. Poza ścieżką gwiazdy — możliwy na końcu lub równolegle. Wybór modelu sesji z S-01 wpływa na cache'owanie tras offline. **Fast-path 2026-07-07: odroczone do po walidacji gwiazdy (S-05).**
- **Status:** proposed

## Backlog Handoff

| Roadmap ID | Change ID                  | Suggested issue title                                  | Ready for `/10x-plan` | Notes |
| ---------- | -------------------------- | ------------------------------------------------------ | --------------------- | ----- |
| F-01       | persistence-baseline       | Podłącz Postgres + JPA + Flyway, zasiej 11 kategorii   | done                  | **Done** — korzeń całej roadmapy; Postgres 18 + Flyway + 11 zasianych kategorii na `master` (DEV-16). |
| F-02       | ai-memory-integration      | Podłącz klient LLM (OpenRouter/Anthropic) + zrębowy profil pamięci + polityka prywatności | done              | **Done** — zmergowany do `master`, zarchiwizowany 2026-08-01 → `context/archive/2026-06-15-ai-memory-integration/`. Decyzje: `context/foundation/ai-provider.md`. |
| S-01       | account-and-auth           | Konto: rejestracja (email+hasło), logowanie, wylogowanie, usunięcie konta, bramkowanie tras, szkielet frontu | done                  | **Done** — wszystkie 4 fazy zmergowane do `master` (PR #5, #6, #8), 2026-08-03. Model sesji: `context/foundation/auth-session-model.md`. Pozostałość: DEV-31 (rozjazd `openapi.yaml`). |
| S-02       | goals-and-dreams           | CRUD celów długoterminowych i marzeń z kategorią       | done                  | **Done** — DEV-19, PR #20, 2026-08-23; `DELETE` domknięty przez DEV-44 (PR #21). Substrat gwiazdy; `goal` niesie wszystkie trzy warstwy. |
| S-03       | ai-memory-seed             | Pamięć AI: onboarding seed + wzbogacanie z ukończeń i wyników propozycji | yes     | Odblokowane 2026-08-23 (S-02 done), ale **wycięte z okna zgłoszenia**. S-04b zapełnia log epizodyczny; profil czeka na ten slice. |
| S-04       | proactive-proposal-engine  | Silnik propozycji + odpowiedzi + pierwszy krok (ręczny trigger) | done          | **Done** — S-04a (DEV-46, PR #25) + S-04b (DEV-23), 2026-08-26. Weszło bez S-03: pamięć zapełnia się z odpowiedzi na propozycje. Tu `LlmClient` dostał pierwsze wywołanie produkcyjne. |
| S-05       | natural-rhythm-return      | Automatyczny powrót w naturalnym rytmie (gwiazda)      | yes                   | Odblokowane 2026-08-26 (S-04 + F-02 done). Gwiazda przewodnia; pierwsza pozycja po zgłoszeniu. Dziedziczy tabelę `proposal` i regułę FR-018 — dokłada tylko rytm i e-mail. **Nie wolno odpytywać bazy częściej niż autosuspend Neona (~5 min)** — następny czas odpalenia liczony w pamięci (`lessons.md`). |
| S-06       | priority-categories        | Kategorie priorytetowe wpływające na bilansowanie      | yes                   | Odblokowane 2026-08-26 (S-04 done). Refinement — dokłada wagę do komparatora `ProposalSelector`. |
| S-07       | current-tasks              | CRUD zadań bieżących                                    | done                  | **Done** — DEV-27, PR #23, 2026-08-24. Trzecia warstwa `GoalLayer.TASK` + nullable `due_date` na tabeli `goal`, nie osobny agregat. |
| S-08       | unified-three-layer-view   | Jednolity widok 3 warstw z filtrami                    | done                  | **Done** — DEV-28, PR #27, 2026-08-25. Filtry warstwy i kategorii w query stringu, filtrowanie w przeglądarce; S-04b dołożył trzeci filtr („wycofane"). |
| S-09       | ai-category-autotag        | Auto-tag kategorii przez AI przy tworzeniu             | no                    | F-02 done; czeka na S-02. Równoległe do pętli. |
| S-10       | offline-read-only          | Offline read-only (PWA) dla 3 warstw                   | no                    | Czeka na S-02 + S-07. Fast-path: odroczone do po S-05. |
| —          | ci-pipeline                | Chore: CI komplementarne do CD — quality gate na PR, skany Trivy, Dependabot, agentic AI code review | done | Ops (2026-07-07): testy biegały tylko w workflow deploy na `master`. Zakres wyrósł ponad pierwotne framing „tylko testy na PR" — stąd zmiana change-id na `ci-pipeline`. Mały, niezależny od slice'ów. Linear: DEV-25. |

## Open Roadmap Questions

1. ~~**Dostawca AI + mechanizm pamięci AI**~~ — **ROZSTRZYGNIĘTE (2026-06-13).** OpenRouter + first-party Anthropic; modele `anthropic/claude-haiku-4.5` (auto-tag) + `anthropic/claude-sonnet-4.6` (propozycje); prywatność „no-training"; pamięć = profil strukturalny + log epizodyczny wstrzykiwany do kontekstu. Odblokowuje `F-02` (i pośrednio `S-03`, `S-04`, `S-05`, `S-09`). Pełna decyzja: `context/foundation/ai-provider.md`. (= PRD Otwarte pytanie 2.)
2. ~~**Ballpark skali** (`target_scale`: users / qps / data_volume)~~ — **ROZSTRZYGNIĘTE (2026-07-07).** MVP: 1-10 użytkowników, <1 qps, <1 GB — uzupełnione we frontmatter PRD; obecne wymiarowanie ops (Fly 512MB, Neon) bez zmian. (= PRD Otwarte pytanie 1.)

> Pytania per-slice (rytm proaktywny → S-05, priorytety×bilansowanie → S-04/S-06, onboarding statyczny/dynamiczny → S-03) zostają przy swoich slice'ach jako Unknowns. Model sesji (cookie vs JWT → S-01) rozstrzygnięty 2026-07-22 — `context/foundation/auth-session-model.md`.

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
- **Powiązanie zadań z „pierwszego kroku" (FR-014) z pozycją, z której wyrosły** — *kandydat na slice po zgłoszeniu, nie Non-Goal.* Dziś „zapisz jako zadanie" tworzy zwykłe zadanie bieżące — dokładnie to, o co prosi FR-014 — bez śladu, że pochodzi z konkretnego marzenia. Trzy poziomy, rosnący koszt: **(1) zapisać relację** — `goal.parent_id` (self-FK, nullable) + `parent_id` w `GoalCreation`, ~15 linii, ale rodzic musi być sprawdzony pod kątem własności, inaczej tworzenie pozycji staje się wyrocznią o cudzych id; **(2) pokazać ją** — otwiera na nowo to, co rozstrzygnął S-08 („trzy warstwy, jeden widok"): dziecko w „Zadaniach bieżących", pod rodzicem, czy w obu; **(3) nauczyć jej silnik** — po „zaczynam" marzenie milczy 30 dni (`DREAM_IDLE_DAYS`), więc w dniu 31 aplikacja zapyta „nie ruszyłeś gitary od miesiąca" kogoś, kto właśnie odhaczył pięć kroków. Bilansowanie łagodzi to już dziś (świeże zadania zerują ciszę swojej domeny, więc cała domena spada na koniec kolejki) — ale na poziomie domeny, nie pozycji, i nie działa dla pozycji bez kategorii. **Odłożone 2026-08-26** (odkryte przy manualnych testach S-04b fazy 4): do zgłoszenia 09-13 wolne wieczory należą się S-05, która sama wypadła z okna. Wracać po S-05.

## Done

(Pusta przy pierwszej generacji. `/10x-archive` dopisuje tu wpis — i przełącza `Status` pozycji na `done` — gdy archiwizowana zmiana ma pasujący `Change ID`. Format:)

- **<Slice ID>: <Outcome>** — Archived <YYYY-MM-DD> → `context/archive/<YYYY-MM-DD-change-id>/`. Lesson: <wskaźnik do lessons.md jeśli jest, lub `—`>.

- **F-01: (fundament) Postgres + Spring Data JPA/Hibernate + narzędzie migracji (Flyway) podłączone do istniejącego backendu; zasiana stała lista 11 kategorii jako dane referencyjne. Brak efektu widocznego dla użytkownika.** — Archived 2026-06-14 → `context/archive/2026-06-13-persistence-baseline/`. Lesson: —.

- **F-02: (fundament) wybrany dostawca AI + klient LLM podłączony; zrębowy mechanizm pamięci AI o użytkowniku; ustalona polityka prywatności (dane i pamięć nie trenują zewnętrznych modeli — twardy guardrail). Brak efektu widocznego dla użytkownika.** — Archived 2026-08-01 → `context/archive/2026-06-15-ai-memory-integration/`. Lesson: —.
