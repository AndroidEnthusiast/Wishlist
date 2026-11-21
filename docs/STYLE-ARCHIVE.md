# Style guide: earlier rules (archive)

Moved word for word from `docs/STYLE.md` on 21 November 2026, when the owner chose one dialog rule for every style
(stacked, full width; see STYLE.md "Dialogs"). Kept for the record; STYLE.md is what holds now.

- One main action per screen or dialog (`Button`); others are `OutlinedButton` or `TextButton`. A dialog's own
  Close / Cancel / Done are `TextButton`s in its `confirmButton` / `dismissButton` slots.
- Title, then `bodyMedium` text, then the content, then the actions at the bottom right. A long dialog scrolls in its
  text slot. Taps outside close it unless something would be lost (then `dismissOnClickOutside = false`).
- Dialogs: the confirm button is the main action (filled) only with a dismiss button; a lone button is tonal.
