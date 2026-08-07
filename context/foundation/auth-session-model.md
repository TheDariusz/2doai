---
project: "2do AI"
type: decision-record
status: accepted
created: 2026-07-22
prd_refs: [FR-001, FR-002, FR-019]
resolves: ["CLAUDE.md otwarta decyzja: cookie vs JWT (`confirm before implementing auth flows`)"]
unblocks: S-01
---

# Decyzja: model sesji uwierzytelniania (cookie vs JWT)

> Rozstrzyga otwartą decyzję z `CLAUDE.md` (*„Auth (cookie vs JWT) is not yet decided — confirm before implementing auth flows"*).
> Odblokowuje slice **S-01 (`account-and-auth`)** — pierwszy slice ustanawiający izolację per-użytkownik, konsumowaną przez wszystkie kolejne slice'y danych.

## Decision

1. **Model sesji: ciasteczko sesyjne po stronie serwera** — `HttpOnly; Secure; SameSite=Strict`, **domyślne zarządzanie sesją Spring Security 6** (servletowy `HttpSession` / model `JSESSIONID`). **Nie JWT.**

2. **Przechowywanie sesji: w pamięci (in-memory)** pojedynczej, zawsze-włączonej maszyny Fly (`min_machines_running = 1`). **Sesje nie trafiają do Postgresa (Neon).**

3. **Uwierzytelnianie: e-mail + hasło** (już rozstrzygnięte). Magic-link jest **post-MVP**.

4. **CSRF:** `SameSite=Strict` (wzmocnione przez architekturę same-origin Pattern B) + wbudowany token CSRF Spring Security dla żądań mutujących.

5. **Odrzucone:** JWT w `localStorage`; oraz `Spring Session JDBC` z sesjami w Neon (patrz Rationale pkt. 4).

## Rationale

Decyzja wynika z **konkretnych ograniczeń tego projektu**, które unieważniają klasyczne argumenty za JWT:

1. **Same-origin (Pattern B) eliminuje główną słabość ciasteczek.** Cloudflare reverse-proxuje `/api/*` do backendu na Fly, więc front i API są **tym samym origin**. Największy powód ucieczki od ciasteczek — cross-origin SPA + osobna domena API, `SameSite=None`, blokowanie ciasteczek third-party — to dokładnie to, co ta architektura wyeliminowała. Dostajemy najbezpieczniejszą konfigurację ciasteczka (`HttpOnly; Secure; SameSite=Strict`) bez żadnego tarcia CORS.

2. **Jedna zawsze-włączona maszyna + 1–10 użytkowników unieważnia główną zaletę JWT.** Sztandarowa korzyść JWT to **bezstanowe skalowanie poziome** (wiele serwerów, brak współdzielonego magazynu sesji). Mamy **jedną** maszynę Fly i <1 qps — zero problemu skalowania, który JWT miałby rozwiązać.

3. **FR-019 (usunięcie konta) + wylogowanie wymagają natychmiastowej unieważnialności → sesje serwerowe.** Sesja po stronie serwera znika natychmiast (usunięcie wpisu). Ważny, niewygasły JWT pozostaje ważny do wygaśnięcia — realne unieważnienie wymaga doklejenia serwerowej listy blokad, czyli odbudowania stanu sesji, który JWT miał usunąć.

4. **Autosuspend Neon — spięcie z pracą nad kosztami ([[lessons.md]]).** Obowiązuje reguła: *zapytanie do bazy to zdarzenie, które musi pozostać rzadkie*. Rządzi ona także tą decyzją. Gdzie żyją sesje, decyduje czy Neon może spać:
   - **Sesje w Neon (Spring Session JDBC):** `SELECT` przy **każdym** żądaniu → budzi compute przy każdym wywołaniu → odbudowuje problem **~183 CU-h/mies.**, który dopiero co usunęliśmy. **Wrogie kosztom.**
   - **Sesje in-memory:** walidacja **nie dotyka DB** → Neon śpi swobodnie. Tak jak walidacja podpisu JWT nie dotyka DB.

   Wybór in-memory jest więc konsekwencją tej samej reguły co drenaż puli Hikari i sonda tylko-liveness na Fly.

5. **Solo dev → najmniej kodu, najmniej footgunów.** Ciasteczko sesyjne to **domyślna, sprawdzona** ścieżka Spring Security. JWT oznacza własne wydawanie tokenów, zarządzanie kluczami podpisu, wygasanie, rotację refresh-tokenów oraz listę blokad, jeśli wylogowanie ma być realne — więcej ruchomych części i więcej klas subtelnych błędów (`alg=none`, zarządzanie kluczami) do utrzymania po godzinach.

## Konsekwencje

- **Przyjęty kompromis:** restart/redeploy maszyny (OOM-kill na 512 MB, migracja hosta Fly) gubi sesje in-memory → użytkownicy logują się ponownie. Przy 1–10 użytkownikach i rzadkich deployach — pomijalne.
- **Kiedy rewidować:** **wyłącznie** gdy porzucimy pojedynczą maszynę (multi-region lub repliki poziome, tj. zejście z `min_machines_running = 1`). Wtedy migracja: sesje in-memory → **podpisane ciasteczko (stateless)** albo **cookie + zewnętrzny magazyn (np. Redis)**. **Nigdy cookie + sesje w Neon** — to samo naruszenie reguły z pkt. 4 Rationale. Bezstanowość JWT staje się warta swoich kosztów dopiero w tym momencie, nie wcześniej.
- **Front (PWA, offline read-only / FR-017):** tryb offline pokazuje dane z cache i nie wykonuje uwierzytelnionych wywołań, więc niedostępność ciasteczka dla JS (`HttpOnly`) jest neutralna; stan „czy zalogowany" offline opiera się na zcache'owanym profilu, nie na odczycie tokenu.
- **Spring Security 6:** ścieżka domyślna (`SecurityFilterChain`, konfiguracja ciasteczka, wbudowany CSRF); brak `oauth2-resource-server` (to droga JWT) w zakresie MVP.

## Decyzja uzupełniająca (2026-08-05, DEV-31): status nieudanej re-autentykacji przy `DELETE /api/users/me`

FR-019 wymaga potwierdzenia hasłem przed usunięciem konta. Pytanie: jakim statusem odpowiedzieć, gdy
hasło potwierdzające jest błędne — i jak klient ma odróżnić tę odmowę od drugiej odmowy możliwej na
tym samym endpoincie (odrzucenie CSRF przez `ProblemDetailsSecurityHandler`).

**Rozstrzygnięcie: zostaje 403, dochodzi `type` URI jako dyskryminator.** Problem JSON nieudanej
re-autentykacji niesie `type: urn:2doai:problem:re-auth-failed` oraz `title: Re-authentication
failed` (`ApiExceptionHandler.handleReAuthenticationFailed`). To jest kanoniczna, zadeklarowana
wartość — pinuje ją `AuthApiTest` (literałem, nie wspólną stałą, żeby przypadkowa zmiana nazwy URN-a
zerwała test) i dokumentuje `openapi.yaml`. Odrzucenie CSRF zostaje przy `about:blank`: jeden
dyskryminator wystarcza, bo klient ma dokładnie jedno pytanie — „czy to złe hasło, czy coś innego".

**Odrzucone: 401.** Na wywołaniu uwierzytelnionym 401 znaczy dla każdego SPA „sesja wygasła" — nasz
własny `client.ts` zamienia je na zdarzenie `session-expired` i zrzuca użytkownika na `/login`.
Zwracanie 401 za literówkę w haśle wylogowywałoby użytkownika za literówkę. Nie ma tu też przesłanki
z enumeracji kont (jak przy logowaniu): wołający jest już uwierzytelniony i już wie, że konto
istnieje.

**Odrzucone: 422.** Kuszące („to błąd walidacji ciała żądania"), ale kolidowałoby z 422, które ten
sam endpoint zwraca z Bean Validation dla hasła pustego lub przekraczającego limit bajtów bcrypta.
Klient i tak potrzebowałby dyskryminatora, żeby odróżnić „hasło się nie zgadza" od „hasło nie
przeszło walidacji" — 422 nie kupuje więc niczego, a kosztuje zmianę statusu w opublikowanym
kontrakcie.

**Ustalenie na marginesie: scenariusz „wygasła sesja → nieaktualny token CSRF → 403 bez wyrzucenia na
`/login`" jest w tej architekturze nieosiągalny.** Wymaga repozytorium tokenów CSRF związanego z
sesją; my używamy `CookieCsrfTokenRepository.withHttpOnlyFalse()` (`SecurityConfig`) — bezstanowego
double-submit, gdzie token żyje we własnym ciasteczku i jest walidowany względem ciasteczka tego
samego żądania. Wygaśnięcie sesji nie unieważnia `XSRF-TOKEN`, więc żądanie przechodzi `CsrfFilter`,
dociera nieuwierzytelnione do filtra autoryzacji i dostaje **401** z entry pointu — czyli dokładnie
to, co SPA zamienia na przekierowanie do logowania. Spisane, żeby nikt nie wyprowadzał tego wniosku
drugi raz.
