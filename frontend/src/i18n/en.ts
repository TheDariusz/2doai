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
  landing: {
    title: 'Your everyday tasks. Your someday dreams.',
    intro: 'Keep tasks, long-term goals and dreams in one place. 2do AI reminds you of plans you have set aside and suggests a first step.',
    seeHow: 'See how it works',
    example: 'Example with fictional entries',
    preview: {
      TASK: 'Go for a 20-minute run after work',
      GOAL: 'Run three times a week',
      DREAM: 'Run a marathon',
      suggestion: 'An AI suggestion',
      message: 'That marathon is still waiting. How about picking a 10 km race to start with?',
      choices: 'You decide: start, come back later, or stop reminders about this entry.',
    },
    howTitle: 'How it works',
    how: {
      capture: { title: 'Write it all down', body: "Add today's tasks, goals for the coming months and dreams with no deadline." },
      revisit: { title: 'Return to forgotten plans', body: 'Suggestions in the app and by email remind you of entries you have left alone for a while.' },
      start: { title: 'Find a first step', body: 'When you decide to start, AI suggests a few small steps you can save as tasks. You can also put a suggestion off or stop reminders about that entry.' },
    },
    faqTitle: 'Before you begin',
    faq: {
      account: { question: 'How do I get started?', answer: 'Create an account with your email and a password. Confirm your address with the code sent by email, then sign in to add your first entry.' },
      suggestions: { question: 'Where will I see suggestions?', answer: 'Scheduled suggestions appear in the app and arrive by email. You can also ask for one in the app at any time.' },
      control: { question: 'What if a suggestion does not suit me?', answer: 'You can start, put it off, choose a later reminder, or withdraw the entry from reminders. Withdrawn entries stay in the app.' },
      deletion: { question: 'Can I delete my account?', answer: 'Yes. In the account menu, confirm with your password to delete your account and its stored entries, proposals and AI memory. This cannot be undone.' },
    },
    closing: 'Start with your first entry.',
  },
  layout: {
    nav: 'Navigation',
    domainsFailed: 'Could not load your life domains — refresh the page.',
    domains: 'Life domains',
    domainComingSoon: 'This domain arrives in a later slice.',
    language: 'Language',
    theme: 'Theme',
    themeCurrent: 'Theme: {{theme}}',
  },
  theme: { light: 'Light', dark: 'Dark', auto: 'Auto' },
  auth: {
    login: { heading: 'Sign in', prompt: 'No account yet?' },
    register: { heading: 'Create account', prompt: 'Already have an account?' },
    email: 'Email',
    password: 'Password',
    errors: {
      emailTaken: 'That email is already registered — sign in instead.',
      emailTakenUnconfirmed: 'Signed up but never got the code? Confirm your address.',
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
    // Names the one outcome the screen cannot see: a resend for an address that is already
    // confirmed is filtered server-side and mails nothing, so the wait would never end.
    sent: 'A new code is on its way — check your inbox. If the address is already confirmed, sign in instead.',
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
