---
project: "2do AI"
type: decision-record
status: accepted
created: 2026-06-13
prd_refs: [FR-008, FR-009, FR-010, FR-011, FR-012, FR-013, FR-014]
resolves: ["PRD Open Question #2", "Roadmap Open Question #1"]
unblocks: F-02
---

# Decyzja: dostawca AI, modele i mechanizm pamięci

> Rozstrzyga **Open Question #2** z `prd.md` oraz **Open Roadmap Question #1** z `roadmap.md`.
> Odblokowuje fundament **F-02 (`ai-memory-integration`)** — a przez niego całą ścieżkę gwiazdy przewodniej (S-03, S-04, S-05) oraz S-09.

## Decision

1. **Dostawca / brama:** **OpenRouter** przed **pierwszorzędnym (first-party) Anthropic** Claude. Powód: zarządzanie budżetem (przedpłacone kredyty = twardy limit; limity per-klucz) oraz elastyczność wyboru modelu. OpenRouter przekazuje cenniki Anthropic **bez marży per-token** (przychód OpenRouter to opłata przy doładowaniu kredytów, nie marża na tokenach).

2. **Modele — podział wg obciążenia (split):**
   | Zastosowanie | Model (slug OpenRouter) | Cena (in/out / 1M) | Dlaczego |
   | --- | --- | --- | --- |
   | Auto-tag (FR-008 / S-09) | `anthropic/claude-haiku-4.5` | $1 / $5 | Szybki + tani; wysoka częstotliwość, wrażliwy na latencję (NFR feedback <500 ms); klasyfikacja do 11 domen przez `json_schema` z `enum` |
   | Propozycje proaktywne + pierwszy krok (FR-011–014 / S-04, S-05) | `anthropic/claude-sonnet-4.6` | $3 / $15 | Jakość polskiego dominuje (komunikat „troskliwego znajomego" = gwiazda przewodnia); niska częstotliwość (~1 / 2–7 dni) |

   Jeden klient (OpenAI-compatible); slug modelu różni się per miejsce wywołania. **Uwaga:** slugi OpenRouter używają **kropek** (`claude-haiku-4.5`), nie myślnikowych ID first-party (`claude-haiku-4-5`).

3. **Prywatność — konfiguracja „no-training":** first-party Anthropic przez OpenRouter z **wyłączonymi** przełącznikami logowania promptów / treningu. Warunki komercyjne Anthropic i tak zabraniają treningu na danych z API — to **dokładnie** spełnia twardy guardrail PRD (*dane i pamięć nie trenują zewnętrznych modeli*), zachowując prompt caching i pełen dostęp do modeli. **ZDR / brak retencji nie jest wymagany** przez guardrail i świadomie nie został przyjęty (kosztowałby prompt caching i wprowadzał zmienność ceny/latencji przez routing Bedrock/Vertex).

4. **Mechanizm pamięci AI — profil strukturalny + wstrzykiwanie do kontekstu:** pamięć trwała w Postgres (F-01) w dwóch warstwach:
   - **Profil semantyczny** — trwałe fakty o użytkowniku (zawód, wartości, osiągnięcia, priorytety); zasiewany onboardingiem (FR-009), wzbogacany przy każdym ukończeniu (FR-010).
   - **Log epizodyczny** — ograniczony strumień ukończeń i wyników propozycji (zaczynam / nie teraz / przypomnij za / nigdy).

   Oba renderowane i wstrzykiwane do kontekstu Sonnet przy każdej propozycji. **Bez bazy wektorowej / embeddingów w MVP.** Czysty agregat DDD; pamięć jest **wglądalna przez użytkownika i eksportowalna do markdown** (służy guardrailowi intymności + kryterium Secondary „eksport"). RAG nad ogonem epizodycznym to **addytywne** rozszerzenie post-MVP (trwałe wiersze epizodyczne są szwem) — nie przepisanie.

## Rationale

- **Budżet:** ceny przekazywane po koszcie + przedpłacone kredyty = naturalny twardy limit wydatków; klucz MVP z niskim limitem per-klucz jako drugi bezpiecznik. Przy skali MVP (~1 użytkownik) koszt modelu jest pomijalny niezależnie od wyboru — dlatego wybór steruje **jakość i latencja**, nie cena.
- **Split modeli:** auto-tag jest wysokoczęstotliwy i na ścieżce latencji UI → najtańszy/najszybszy Haiku ze strukturalnym `enum`. Propozycje proaktywne są rzadkie, ale to **różnicująca** funkcja produktu → Sonnet dla jakości polskiego i tonu. Dodatkowa złożoność = jedna wartość konfiguracyjna per zastosowanie (ten sam klient).
- **Prywatność:** guardrail mówi o **treningu**, nie o **retencji** — konfiguracja no-training spełnia go wprost, bez utraty prompt caching.
- **Pamięć — profil strukturalny zamiast RAG:** przy skali MVP obie warstwy pamięci mieszczą się swobodnie w oknie kontekstu Sonnet, więc infrastruktura wyszukiwania nie zarabia jeszcze na swój koszt. Profil strukturalny daje czysty model domenowy (preferencja DDD z CLAUDE.md), determinizm, wglądalność/eksport oraz kontrolowany koszt tokenów — a szew pod RAG zostaje.

## Integration implications

Dla przyszłego `/10x-plan ai-memory-integration` — żeby nie wyprowadzać tego ponownie:

- **Kształt API:** OpenRouter udostępnia Anthropic **wyłącznie** przez endpoint **OpenAI-compatible Chat Completions** (`POST https://openrouter.ai/api/v1/chat/completions`, auth bearer). Backend używa **Spring AI 2.0** (klient OpenAI ze startera `spring-ai-starter-model-openai`, skonfigurowany `spring.ai.openai.base-url` → OpenRouter) — **nie** ręcznie pisanego `RestClient` ani Anthropic Java SDK (`com.anthropic:anthropic-java`). Za portem/adapterem (interfejs `LlmClient`, adapter `SpringAiLlmClient`) zgodnie z preferencją clean-architecture, żeby brama była wymienialna. Transport, timeouty i retry (429/5xx, fail-fast na innych 4xx) są wbudowane w klienta OpenAI (`spring.ai.openai.timeout` / `spring.ai.openai.max-retries`) — adapter kształtuje tylko żądanie. _Uwaga: Spring AI 2.0 wymaga Spring Boot 4.0 (GA 2026-06-12)._
- **Wyjście strukturalne (auto-tag):** Spring AI `OpenAiChatModel.ResponseFormat` typu `JSON_SCHEMA` (Spring AI ustawia `strict: true` i nazwę `json_schema` na drucie) ze schematem `enum` 11 domen → gwarancja jednej poprawnej kategorii.
- **No-training w kodzie:** blok `provider: { data_collection: "deny" }` jest dokładany do **każdego** żądania przez `OpenAiChatOptions.extraBody(...)` (Spring AI przekazuje go jako zagnieżdżone OpenAI `additionalBodyProperties`) — to kodowa połowa twardego guardraila prywatności (dashboardowa połowa niżej).
- **Sekrety:** `OPENROUTER_API_KEY` jako **sekret Fly** (`fly secrets set …`), nigdy w repo ani w commitowanym `.env`. Klucz MVP z niskim limitem (cap kredytów per-klucz) jako drugi bezpiecznik budżetu.
- **Konfiguracja prywatności (jednorazowo, dashboard OpenRouter):** wyłączyć logowanie promptów; w Privacy ustawić przełączniki treningu na OFF (OpenRouter nie będzie routował do dostawców trenujących). Udokumentować w runbooku.

## Do zweryfikowania przy implementacji

> **Status po F-02 (2026-06-18):** (a) i (d) rozstrzygnięte w kodzie/runbooku; (b) potwierdzone
> **na Sonnet** żywym round-tripem (`OpenRouterLiveTest`); **(b) dla Haiku** + **(c)** świadomie
> **odłożone do S-09** (tam ląduje auto-tag). Szczegóły ops: `deployment-runbook.md` → Faza 7.

- **(a)** Stawka opłaty OpenRouter przy doładowaniu kredytów — **mechanizm potwierdzony**: opłata
  naliczana przy *doładowaniu kredytów* (nie marża per-token), ceny Anthropic pass-through; dokładny
  % do wpisania z ekranu kredytów (placeholder w runbooku Faza 7.1) — nie blokuje F-02.
- **(b)** Wsparcie `json_schema` `strict`: **potwierdzone na Sonnet 4.6** (strukturalny round-trip w
  `OpenRouterLiveTest` przechodzi). Dla **Haiku 4.5** nadal niezweryfikowane — **odłożone do S-09**
  (fallback jeśli brak: tool-calling, prompted JSON, albo skierowanie auto-tagu na Sonnet).
- **(c)** Tania **A/B jakości polskiego** Haiku vs Sonnet na ~30 pozycjach tagowania — **odłożone do
  S-09** (przed zablokowaniem warstwy auto-tagu).
- **(d)** No-training/logowanie: **rozstrzygnięte w kodzie** — `provider: { data_collection: "deny" }`
  na każdym żądaniu (zweryfikowane żywym round-tripem), wsparte domyślnym routingiem OpenRouter (nie
  kieruje do dostawców trenujących) i warunkami komercyjnymi Anthropic. Dashboard domyślnie bezpieczny
  (patrz runbook Faza 7.2).

## Related

- `prd.md` → Open Question #2 (rozstrzygnięte tą decyzją), FR-008, FR-009/010, FR-011–014, NFR (prywatność, feedback <500 ms).
- `roadmap.md` → F-02 (`ai-memory-integration`), Open Roadmap Question #1.
- Pozostaje otwarte: PRD Open Question #1 (`target_scale`) — nieblokujące; przy skali MVP koszt modelu pomijalny.
