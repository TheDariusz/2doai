/**
 * Polish copy — the language the app shipped in, and the shape both catalogs are typed against
 * (`i18next.d.ts`), so a key added here and forgotten in `en.ts` is a `tsc` failure rather than a
 * silent fallback on screen.
 *
 * Deliberately not `as const`: the literal types that would produce make `en: typeof pl` demand the
 * *Polish* strings. Plain inference gives `string`, which is the parity check we actually want.
 *
 * Server-generated text is absent by design — `proposal.message`, `first_step[]` and a category's
 * `name` arrive already written in the language the request negotiated and are rendered verbatim.
 */
export const pl = {
  landing: {
    title: 'Codzienne zadania. Marzenia, które się spełniają.',
    intro: 'Zbierz zadania, cele długoterminowe i marzenia w jednym miejscu. 2do AI przypomina o odłożonych planach i podsuwa pierwszy krok.',
    seeHow: 'Zobacz, jak to działa',
    example: 'Przykład z fikcyjnymi wpisami',
    preview: {
      TASK: 'Pobiegać 20 minut po pracy',
      GOAL: 'Biegać trzy razy w tygodniu',
      DREAM: 'Przebiec maraton',
      suggestion: 'Propozycja AI',
      message: 'Maraton wciąż czeka. Może na początek wybierzesz bieg na 10 km?',
      choices: 'Ty decydujesz: zacząć, wrócić później albo wyłączyć przypomnienia o tym wpisie.',
      // The three answers the real proposal card offers, as chips in the example.
      answers: { start: 'Zaczynam', later: 'Wrócę później', never: 'Nie przypominaj' },
    },
    eyebrow: 'Przypomina w naturalnym rytmie',
    howTitle: 'Jak to działa',
    how: {
      capture: { title: 'Zapisz wszystko', body: 'Dodaj codzienne zadania, cele na najbliższe miesiące i marzenia bez terminu.' },
      revisit: { title: 'Wróć do zapomnianych planów', body: 'Propozycje w aplikacji i w emailu przypominają o wpisach, do których dawno nie zaglądasz.' },
      start: { title: 'Znajdź pierwszy krok', body: 'Gdy zdecydujesz się zacząć, AI zaproponuje kilka małych kroków, które zapiszesz jako zadania. Możesz też odłożyć propozycję lub wyłączyć przypomnienia o tym wpisie.' },
    },
    faqTitle: 'Zanim zaczniesz',
    faq: {
      account: { question: 'Jak zacząć?', answer: 'Załóż konto, podając email i hasło. Potwierdź adres kodem z wiadomości, a następnie zaloguj się i dodaj pierwszy wpis.' },
      suggestions: { question: 'Gdzie zobaczę propozycje?', answer: 'Automatyczne propozycje pojawiają się w aplikacji i przychodzą emailem. O propozycję możesz też poprosić w aplikacji w dowolnej chwili.' },
      control: { question: 'Co, jeśli propozycja mi nie pasuje?', answer: 'Możesz zacząć, odłożyć działanie, wybrać późniejsze przypomnienie lub wycofać wpis z przypomnień. Wycofane wpisy zostają w aplikacji.' },
      deletion: { question: 'Czy mogę usunąć konto?', answer: 'Tak. W menu konta potwierdź hasłem usunięcie konta oraz zapisanych wpisów, propozycji i pamięci AI. Tej operacji nie da się cofnąć.' },
    },
    closing: 'Zacznij od pierwszego wpisu.',
    // The author's handle, site and profile names are ASCII proper nouns and stay in the component;
    // the name carries a diacritic, so it lives here (identical in both catalogs) to keep the lint
    // rule's diacritic detector meaningful everywhere else.
    footer: { credit: 'Autor', name: 'Dariusz Szczepański', role: 'Software Engineer', links: 'Strona i profile autora' },
  },
  layout: {
    nav: 'Nawigacja',
    domainsFailed: 'Nie udało się wczytać domen — odśwież stronę.',
    domains: 'Obszary życia',
    domainComingSoon: 'Ta domena pojawi się w kolejnym wycinku.',
    language: 'Język',
    theme: 'Motyw',
    themeCurrent: 'Motyw: {{theme}}',
  },
  theme: { light: 'Jasny', dark: 'Ciemny', auto: 'Automatyczny' },
  auth: {
    // Each mode's heading doubles as the other mode's link label.
    login: {
      heading: 'Zaloguj się',
      prompt: 'Nie masz jeszcze konta?',
      hint: 'Podaj adres email, a wyślemy na niego jednorazowy kod do logowania.',
      submit: 'Wyślij mi kod',
    },
    register: { heading: 'Załóż konto', prompt: 'Masz już konto?', submit: 'Załóż konto' },
    email: 'Email',
    password: 'Hasło',
    errors: {
      emailTaken: 'Ten adres email jest już zarejestrowany.',
      // Offered on every 409: the server does not say whether the address was ever proved.
      emailTakenSignIn: 'Wyślij mi kod do logowania',
      // Only registration carries the 8-character minimum, so only its message may quote it.
      invalidRegistration: 'Sprawdź adres email i hasło (min. 8 znaków).',
      invalidEmail: 'Sprawdź adres email.',
      unavailable: 'Logowanie jest chwilowo niedostępne. Spróbuj za moment.',
      generic: 'Coś poszło nie tak. Spróbuj ponownie.',
    },
  },
  verify: {
    heading: 'Wpisz kod',
    // Conditional on purpose: the server mails nothing to an address without an account and will not
    // say so, so the screen may not claim a code is coming. The window is named because staleness is
    // what kills a code; the number is `EmailVerificationService.CODE_VALIDITY`, and both catalogs
    // are held against it by `VerificationApiTest.emitsTheVerificationUrnsTheContractAndTheSpaBothHardcode`.
    hint: 'Jeśli {{email}} ma konto, wysłaliśmy na ten adres ośmiocyfrowy kod. Jest ważny 15 minut.',
    // What to do when nothing arrives: a filtered mail is the likeliest cause (lessons.md), and the
    // resend is locked for the throttle's minute, so the wait is named before the button refuses.
    missing: 'Nic nie dotarło? Sprawdź folder spam. O nowy kod możesz poprosić po minucie.',
    code: 'Kod z wiadomości',
    submit: 'Zaloguj się',
    sendAgain: 'Wyślij kod jeszcze raz',
    resendIn: 'O kolejny kod możesz poprosić za {{time}}.',
    sent: 'Jeśli ten adres ma konto, nowy kod jest w drodze. Sprawdź skrzynkę i folder spam.',
    // Shown while the mail link signs in on its own — only in the browser that asked for the code.
    signingIn: 'Logujemy Cię…',
    errors: {
      // The link signed in on its own and was refused: most often an older mail, whose code a
      // newer one replaced, or a link a scanner spent first. Names the link, not a typed code.
      link: 'Ten link już nie działa — mógł zostać użyty albo przyszedł nowszy kod. Otwórz najnowszą wiadomość albo poproś o nowy kod.',
      // The server conflates wrong, expired, spent and unknown on purpose, so this is
      // the one sentence that fits all four - and it names the only remedy there is.
      code: 'Ten kod jest nieprawidłowy albo stracił ważność. Poproś o nowy.',
      // The countdown beside the button carries the real wait, from the server's Retry-After.
      tooMany: 'Za dużo próśb o kod. Spróbuj ponownie, gdy skończy się odliczanie.',
      unavailable: 'Nie udało się wysłać kodu. Spróbuj ponownie za chwilę.',
    },
  },
  account: {
    logout: 'Wyloguj',
    delete: 'Usuń konto',
    deleteWarning: 'Usunięcie konta kasuje wszystkie Twoje dane. Tej operacji nie da się cofnąć.',
    confirmPassword: 'Potwierdź hasłem',
    deleteForever: 'Usuń konto na zawsze',
    cancel: 'Anuluj',
    errors: {
      logout: 'Nie udało się wylogować. Spróbuj ponownie.',
      wrongPassword: 'Nieprawidłowe hasło.',
      delete: 'Nie udało się usunąć konta. Odśwież stronę i spróbuj ponownie.',
      language: 'Nie udało się zmienić języka. Spróbuj ponownie.',
    },
  },
  goals: {
    title: 'Zadania, cele i marzenia',
    // A heading names the group; a layer names one entry — deliberately different wordings.
    sections: { TASK: 'Zadania bieżące', GOAL: 'Cele długoterminowe', DREAM: 'Marzenia' },
    layers: { TASK: 'Zadanie', GOAL: 'Cel', DREAM: 'Marzenie' },
    horizons: { THIS_YEAR: 'W tym roku', FEW_MONTHS: 'Najbliższe miesiące' },
    createForm: 'Nowy wpis',
    create: 'Dodaj',
    editForm: 'Edytuj wpis',
    save: 'Zapisz',
    cancel: 'Anuluj',
    content: 'Treść',
    // The one field the quick-add bar shows without its own visible label — the placeholder names
    // all three layers, because it is the first control a new account meets.
    placeholder: 'Dodaj zadanie, cel albo marzenie…',
    layer: 'Rodzaj',
    horizon: 'Horyzont',
    dueDate: 'Termin',
    dueTime: 'Godzina',
    // The lead is read as the end of "remind me …", so each option completes that sentence.
    reminderLead: 'Przypomnij',
    reminderLeads: {
      AT_TIME: 'O tej godzinie',
      FIFTEEN_MINUTES: '15 minut wcześniej',
      ONE_HOUR: 'Godzinę wcześniej',
      MORNING_OF: 'Rano tego dnia',
    },
    category: 'Kategoria',
    noCategory: 'Bez kategorii',
    // `n` rather than `count`: this is a tally in brackets, not a sentence that inflects.
    completed: 'Ukończone ({{n}})',
    due: 'do {{date}}',
    dueAt: 'do {{date}}, godz. {{time}}',
    withdrawnTag: 'wycofane',
    complete: 'Ukończ',
    restore: 'Przywróć',
    edit: 'Edytuj',
    delete: 'Usuń',
    confirmDelete: 'Usunąć „{{content}}”? Tej operacji nie da się cofnąć.',
    noMatches: 'Żaden wpis nie pasuje do filtrów.',
    // The two axes that survive as controls: the layer tabs on the screen, and the rail's switch.
    // Both are phrased as "show …" rather than reusing the form's bare field names — two controls
    // answering to one name is what a screen reader reads out of its controls list.
    filters: {
      layer: 'Pokaż rodzaj',
      withdrawn: 'Pokaż wycofane',
      all: 'Wszystkie',
    },
    errors: {
      load: 'Nie udało się wczytać wpisów — odśwież stronę.',
      gone: 'Ten wpis już nie istnieje — lista została odświeżona.',
      rejected: 'Serwer odrzucił ten wpis — horyzont ma tylko cel, termin tylko zadanie.',
      refresh: 'Odśwież stronę i spróbuj ponownie.',
      save: 'Nie udało się zapisać zmiany. Spróbuj ponownie.',
    },
  },
  proposal: {
    label: 'Propozycja',
    ask: 'Daj mi coś teraz',
    searching: 'Szukam wpisu, do którego warto wrócić…',
    nothingWaiting: 'Nic teraz nie czeka — nic nie leży odłogiem.',
    answers: {
      STARTING: 'Zaczynam',
      NOT_NOW: 'Nie teraz',
      REMIND_LATER: 'Przypomnij później',
      NEVER: 'Nigdy',
    },
    // A plural key, not `Za {{days}} dni`: the presets happen to share one form today, and a
    // one-day preset would have made the old template wrong in Polish without warning.
    remindIn_one: 'Za {{count}} dzień',
    remindIn_few: 'Za {{count}} dni',
    remindIn_many: 'Za {{count}} dni',
    remindIn_other: 'Za {{count}} dnia',
    confirmation: {
      STARTING: 'Pierwszy krok:',
      NOT_NOW: 'Dobrze — wrócimy do tego za kilka dni.',
      REMIND_LATER: 'Przypomnimy w wybranym terminie.',
      // Withdrawal is reversible and the filter is the only way back, so this quotes the filter's
      // own label through i18next nesting — the two cannot drift apart into different words.
      NEVER: 'Wycofane — wpis znajdziesz pod filtrem „$t(goals.filters.withdrawn)”.',
    },
    noFirstStep: 'Odpowiedź zapisana, ale nie udało się przygotować pierwszego kroku.',
    saveStep: 'Zapisz jako zadanie',
    stepSaved: 'Zapisano',
    errors: {
      refresh: 'Odśwież stronę i spróbuj ponownie.',
      alreadyAnswered: 'Ta propozycja została już rozstrzygnięta — poproś o nową.',
      // Three whole sentences rather than one template plus three genitive fragments: that shape
      // is grammar by concatenation, and it only ever worked in the language it was written in.
      propose: 'Nie udało się pobrać propozycji. Spróbuj ponownie.',
      answer: 'Nie udało się zapisać odpowiedzi. Spróbuj ponownie.',
      saveStep: 'Nie udało się zapisać zadania. Spróbuj ponownie.',
    },
  },
}
