import type { pl } from './pl'

/**
 * English copy, typed against the Polish catalog: a key missing here is a `tsc` error, and a key
 * that exists here and nowhere else is an excess-property error. That is the leak guard — without
 * it a forgotten key falls back to Polish on an English screen and nothing goes red.
 *
 * Written as English, not as a gloss of the Polish: the sentences are shorter and the failure
 * messages name what failed rather than composing one from fragments.
 */
export const en: typeof pl = {
  layout: {
    nav: 'Navigation',
    domainsFailed: 'Could not load your life domains — refresh the page.',
    domains: 'Life domains',
    domainComingSoon: 'This domain arrives in a later slice.',
    language: 'Language',
  },
  auth: {
    login: { heading: 'Sign in', prompt: 'No account yet?' },
    register: { heading: 'Create account', prompt: 'Already have an account?' },
    email: 'Email',
    password: 'Password',
    errors: {
      emailTaken: 'That email is already registered — sign in instead.',
      invalidRegistration: 'Check the email and the password (at least 8 characters).',
      invalidCredentials: 'Check the email and the password.',
      wrongCredentials: 'Incorrect email or password.',
      unavailable: 'Signing in is unavailable right now. Try again in a moment.',
      generic: 'Something went wrong. Try again.',
    },
  },
  verify: {
    heading: 'Confirm your address',
    hint: 'We sent a six-digit code to {{email}}. It is valid for 15 minutes.',
    code: 'Code from the email',
    submit: 'Confirm address',
    sendAgain: 'Send the code again',
    sent: 'A new code is on its way — check your inbox.',
    confirmed: 'Address confirmed — you can sign in now.',
    errors: {
      code: 'That code is wrong or no longer valid. Ask for a new one.',
      notVerified: 'Confirm your address first — enter the code we emailed you.',
      tooMany: 'Too many code requests. Wait a minute and try again.',
      unavailable: 'The code could not be sent. Try again in a moment.',
    },
  },
  account: {
    logout: 'Log out',
    delete: 'Delete account',
    deleteWarning: 'Deleting your account erases all your data. This cannot be undone.',
    confirmPassword: 'Confirm with your password',
    deleteForever: 'Delete my account for good',
    cancel: 'Cancel',
    errors: {
      logout: 'Could not log you out. Try again.',
      wrongPassword: 'Incorrect password.',
      delete: 'Could not delete the account. Refresh the page and try again.',
      language: 'Could not change the language. Try again.',
    },
  },
  goals: {
    title: 'Tasks, goals and dreams',
    sections: { TASK: 'Current tasks', GOAL: 'Long-term goals', DREAM: 'Dreams' },
    layers: { TASK: 'Task', GOAL: 'Goal', DREAM: 'Dream' },
    horizons: { THIS_YEAR: 'This year', FEW_MONTHS: 'Next few months' },
    createForm: 'New entry',
    create: 'Add',
    editForm: 'Edit entry',
    save: 'Save',
    cancel: 'Cancel',
    content: 'Content',
    placeholder: 'Add a task, a goal or a dream…',
    layer: 'Kind',
    horizon: 'Horizon',
    dueDate: 'Due date',
    category: 'Category',
    noCategory: 'No category',
    completed: 'Completed ({{n}})',
    due: 'due {{date}}',
    withdrawnTag: 'withdrawn',
    complete: 'Complete',
    restore: 'Restore',
    edit: 'Edit',
    delete: 'Delete',
    confirmDelete: 'Delete “{{content}}”? This cannot be undone.',
    noMatches: 'No entry matches the filters.',
    filters: {
      layer: 'Show kind',
      withdrawn: 'Show withdrawn',
      all: 'All',
    },
    errors: {
      load: 'Could not load your entries — refresh the page.',
      gone: 'This entry no longer exists — the list has been refreshed.',
      rejected: 'The server rejected this entry — only a goal has a horizon, only a task a due date.',
      refresh: 'Refresh the page and try again.',
      save: 'Could not save the change. Try again.',
    },
  },
  proposal: {
    label: 'Proposal',
    ask: 'Give me something now',
    searching: 'Looking for an entry worth coming back to…',
    nothingWaiting: 'Nothing is waiting — nothing is gathering dust.',
    answers: {
      STARTING: "I'm starting",
      NOT_NOW: 'Not now',
      REMIND_LATER: 'Remind me later',
      NEVER: 'Never',
    },
    // English selects `one` and `other` only; `few` and `many` exist because both catalogs are one
    // shape (see `en: typeof pl`) and Polish needs them. They can never be reached from here.
    remindIn_one: 'In {{count}} day',
    remindIn_few: 'In {{count}} days',
    remindIn_many: 'In {{count}} days',
    remindIn_other: 'In {{count}} days',
    confirmation: {
      STARTING: 'First step:',
      NOT_NOW: 'Fine — we will come back to this in a few days.',
      REMIND_LATER: 'We will remind you on the date you picked.',
      NEVER: 'Withdrawn — you will find the entry under the “$t(goals.filters.withdrawn)” filter.',
    },
    noFirstStep: 'Your answer is saved, but a first step could not be prepared.',
    saveStep: 'Save as a task',
    stepSaved: 'Saved',
    errors: {
      refresh: 'Refresh the page and try again.',
      alreadyAnswered: 'This proposal has already been answered — ask for a new one.',
      propose: 'Could not fetch a proposal. Try again.',
      answer: 'Could not save your answer. Try again.',
      saveStep: 'Could not save the task. Try again.',
    },
  },
}
