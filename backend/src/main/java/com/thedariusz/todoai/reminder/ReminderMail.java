package com.thedariusz.todoai.reminder;

/** One reminder, rendered and addressed, waiting to be sent after the fire transaction commits. */
record ReminderMail(String to, String subject, String text) {
}
