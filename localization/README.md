# App copy

Supported languages: English, German, French, Spanish, Italian, Brazilian Portuguese, Dutch and Japanese.

The strings were extracted from the shipped UI and background messages and translated with the project's existing Workers AI model. These are machine-assisted translations. Native-speaker and large-font/layout review are still release checks, especially for emergency wording and billing terms.

`tools/translate_resources.py` validates placeholder agreement and caches completed public-string translations. It never sends pet records or credentials to the model. Untranslatable URLs/email remain in the base resource file. Target plural categories are included and all supported languages are bundled for offline selection.

`archive/` preserves the previous incomplete unsupported locales. `extraction/` stores the one-time rewriting scripts as text for provenance; they are not part of the application or a repeatable rewrite workflow.

The JSON caches contain public copy only. Do not store user data here.
