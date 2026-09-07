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
  layout: {
    nav: 'Nawigacja',
    domainsFailed: 'Nie udało się wczytać domen — odśwież stronę.',
    domains: 'Obszary życia',
    domainComingSoon: 'Ta domena pojawi się w kolejnym wycinku.',
    language: 'Język',
  },
  auth: {
    // Each mode's heading doubles as its submit label, and as the other mode's link label.
    login: { heading: 'Zaloguj się', prompt: 'Nie masz jeszcze konta?' },
    register: { heading: 'Załóż konto', prompt: 'Masz już konto?' },
    email: 'Email',
    password: 'Hasło',
    errors: {
      emailTaken: 'Ten adres email jest już zajęty — zaloguj się.',
      // Only registration carries the 8-character minimum, so only its message may quote it.
      invalidRegistration: 'Sprawdź adres email i hasło (min. 8 znaków).',
      invalidCredentials: 'Sprawdź adres email i hasło.',
      wrongCredentials: 'Nieprawidłowy email lub hasło.',
      unavailable: 'Logowanie jest chwilowo niedostępne. Spróbuj za moment.',
      generic: 'Coś poszło nie tak. Spróbuj ponownie.',
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
    category: 'Kategoria',
    noCategory: 'Bez kategorii',
    // `n` rather than `count`: this is a tally in brackets, not a sentence that inflects.
    completed: 'Ukończone ({{n}})',
    due: 'do {{date}}',
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
