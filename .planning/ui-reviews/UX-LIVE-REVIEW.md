# UX Live-Audit — S+R Assistent
**Datum:** 2026-04-06
**Methode:** Playwright Browser-Audit (localhost:8080)
**Viewport-Tests:** 1440px (Desktop), 768px (Tablet), 375px (Mobile)
**Screenshots:** `.planning/ui-reviews/screenshots/`

---

## Gesamtbewertung: 13/24

| Bereich | Score | Hauptproblem |
|---------|-------|--------------|
| Rechnungsservice (Arbeitsbereich) | 2/4 | Button-Farbchaos, leere Fläche |
| Rechnungsservice (Einstellungen) | 2/4 | Seite zu lang, API-Key im Klartext |
| E-Mail Designer | 3/4 | Split-Pane gut, Toolbar inkonsistent |
| Validierungen | 2/4 | Nur Ladebutton, kein Empty-State |
| Auswertungen | 2/4 | Datum-Layout bricht, leere Chart-Boxen |
| Benutzerverwaltung (Modal) | 2/4 | Kein Overlay, Passwort-Placeholder abgeschnitten |
| Responsiveness | 0/4 | Mobile komplett kaputt |

---

## Top 5 Kritische UX-Probleme

### 1. Mobile Layout komplett kaputt (Kritisch)
**Screenshot:** `08-mobile-375.png`

Bei 375px überlappt die Sidebar den Content vollständig. Header wird abgeschnitten ("S+R Assis..."), alle langen Button-Labels brechen auf 3–4 Zeilen ("Polling starten" → 2 Zeilen, Exportbutton → 4 Zeilen). Die App ist auf Smartphones unbenutzbar.

**Fix:** Sidebar bei `<768px` in ein Hamburger-Menü oder Drawer umwandeln. Sidebar-CSS: `@media (max-width: 767px) { .sidebar { display: none; } .hamburger { display: block; } }`

---

### 2. Kein konsistentes Button-System (Hoch)
**Screenshot:** `01-login.png`, `06-arbeitsbereich.png`

Im Rechnungsservice-Arbeitsbereich existieren gleichzeitig:
- **Dunkelgrün** gefüllt: "Polling starten"
- **Teal** gefüllt: "Neuesten Zahllauf hinzufügen", "Neuesten Zahllauf hinzufügen"
- **Lila** gefüllt: "Rechnungsdetails exportieren", "Arbeitsbereich" (aktiver Tab)
- **Outlined** weiß: "E-Mail Designer", "Einstellungen" (inaktive Tabs)

Keine visuelle Logik: Primär-/Sekundär-/Destruktiv-Hierarchie fehlt komplett.

**Fix:** 3 Button-Varianten definieren — `btn-primary` (lila, eine Hauptaktion pro Bereich), `btn-action` (teal, Datenoperationen), `btn-ghost` (outlined, Navigation/sekundär). Alle Buttons entsprechend umstellen.

---

### 3. ~50 Zahllauf-Pills ohne Pagination/Suche (Hoch)
**Screenshot:** `09-rechnungsservice-einstellungen.png`

Die Einstellungsseite zeigt alle ~50 Zahllauf-IDs als deletierbare Pills in einem 2-Spalten-Grid. Das erzeugt eine sehr lange Scrollseite und macht das Auffinden eines bestimmten Zahllaufs unmöglich ohne lineares Durchsuchen.

**Fix:** Max. 10 Pills anzeigen + "Alle anzeigen / einklappen" Toggle. Alternativ: Such-/Filterfeld über der Liste.

---

### 4. Einstellungen sind Vollseiten-Ersatz statt Overlay (Mittel)
**Screenshot:** `04-einstellungen-modal.png`

Der "Einstellungen"-Button im Header ersetzt die gesamte Seite durch die Benutzerverwaltung ohne Modal/Drawer-Pattern. Nutzer verlieren den Kontext — kein Hinweis, zu welchem Bereich sie danach zurückkehren.

**Fix:** Echtes Modal (`<dialog>`) oder Right-Drawer verwenden. Hintergrund blurren (CSS `backdrop-filter`). "Schließen"-Button wieder im sichtbaren Bereich lassen.

---

### 5. "Versand: an Beraterinnen-E-Mail" in roter Schrift wirkt wie Fehler (Mittel)
**Screenshot:** `06-arbeitsbereich.png`

Die zweite Radio-Option "Versand: an Beraterinnen-E-Mail" ist fett **rot** — das kommuniziert "Fehler" oder "Warnung", ist aber nur eine wählbare Option (die sogar sinnvoll für den normalen Betrieb ist).

**Fix:** Farbe auf normales Textgrau ändern. Wenn es ein Warnhinweis sein soll (weil echte E-Mails rausgehen), dann mit einem `⚠` Icon + Tooltip, nicht mit roter Pflichtfeld-Farbe.

---

## Weitere Findings nach Bereich

### Rechnungsservice — Arbeitsbereich
- `01-login.png` / `06-arbeitsbereich.png`: Drei Sub-Tab-Buttons (Arbeitsbereich, E-Mail Designer, Einstellungen) sehen wie beliebige Action-Buttons aus, nicht wie Tabs → fehlende visuelle Tab-Metapher (aktiver Tab sollte z.B. border-bottom oder andere Hintergrundfarbe nutzen)
- Statusanzeige "Noch nicht gestartet." ist kleines, graues Plaintext → kein Badge, kein Icon, leicht übersehen

### Auswertungen
- `03-auswertungen.png`: Datum-Inputs wrappen auf 2 Zeilen (Zeitraum-Label + "von:" + Datepicker in Zeile 1, dann "bis:" + Datepicker in Zeile 2) — inkonsistentes Layout
- Chart-Platzhalter (gestrichelte Box) ohne "Keine Daten — bitte Zeitraum wählen"-Text
- Abschnitt-Überschriften ("Management-KPIs", "Zusammenfassung" etc.) als fette Paragraphen, nicht als `<h3>` → Hierarchie-Problem

### E-Mail Designer
- `05-email-designer.png`: Toolbar-Buttons inkonsistent — B/I/U als kleine Quadrate, andere als Pills
- Color-Picker-Box (2×2rem schwarzes Quadrat) ist zu klein als Click-Target — WCAG 2.5.5 empfiehlt min. 44×44px
- Split-Pane-Layout (Editor links, Vorschau rechts) ist das stärkste UX-Pattern der App ✓

### Validierungen
- `02-validierungen.png`: Nur ein "Stammdaten laden / neu laden"-Button sichtbar — Empty-State-Text "Noch keine Daten geladen." zu unauffällig. Nutzer unklar, was das Laden bewirkt.
- Tab-Wechsel zwischen "Validierung der Stammdaten" und "Baum der Beraterinnen" nutzt wieder inkonsistente Button-Farben

### Benutzerverwaltung / Einstellungen-Modal
- `04-einstellungen-modal.png`: Passwort-Placeholder "(unverändert lassen zum Bei..." wird abgeschnitten — Feld zu schmal
- "Speichern" und "Schließen" im Kopf-Bereich, weit vom Formular-Inhalt entfernt — ungewöhnlich, vor allem bei langem Scroll
- API-Key im ERPNext-Abschnitt im Klartext sichtbar → sollte als `type="password"` mit Toggle-Icon sein

---

## Console-Fehler

```
[ERROR] Failed to load resource: 404 (Not Found) @ http://localhost:8080/favicon.ico
[VERBOSE] Password field is not contained in a form (3×) — Browser kann kein Passwort speichern
```

**favicon.ico**: Einfach eine `favicon.ico` oder `<link rel="icon">` im HTML-Head ergänzen.

**Passwort-Felder außerhalb `<form>`**: Die Login-Eingaben sind nicht in einem `<form>`-Element — verhindert Browser-Passwort-Manager-Integration. Login-Block in `<form onsubmit="return false;">` wrappen.

---

## Responsiveness-Zusammenfassung

| Viewport | Status | Hauptproblem |
|----------|--------|--------------|
| 1440px | ✓ Funktioniert | Kleiner Weißraum-Überschuss |
| 768px | ✓ Weitgehend OK | Header-Text wraps leicht |
| 375px | ✗ Kaputt | Sidebar overlappt Content |

---

## Priorisierte Verbesserungs-Roadmap

| Priorität | Aufwand | Maßnahme |
|-----------|---------|----------|
| 🔴 Kritisch | M | Responsive: Sidebar-Collapse + Hamburger-Menü bei <768px |
| 🔴 Kritisch | S | Button-System: 3 semantische Klassen definieren + durchsetzen |
| 🟠 Hoch | S | "Beraterinnen-E-Mail" Rotfarbe → neutral |
| 🟠 Hoch | M | Zahllauf-Pills: Max 10 + Toggle / Suchfeld |
| 🟡 Mittel | L | Einstellungen: Echter Modal/Drawer statt Seitenersatz |
| 🟡 Mittel | S | favicon.ico hinzufügen |
| 🟡 Mittel | S | API-Key/Passwort-Felder als `type="password"` mit Toggle |
| 🟡 Mittel | S | Login-Felder in `<form>` wrappen (Passwort-Manager) |
| 🟢 Klein | S | Auswertungen: Datum-Layout in eine Zeile bringen |
| 🟢 Klein | S | Chart-Leer-States: Beschreibender Text statt leere Box |
| 🟢 Klein | S | Color-Picker-Target-Größe: min. 44×44px |

**Aufwand:** S = <1h, M = 1–4h, L = 1 Tag

---

## Dateien

- `src/main/resources/ui/dashboard.html` — alle Fixes hier
- `screenshots/01-login.png` — Dashboard/Hauptansicht
- `screenshots/02-validierungen.png` — Validierungen
- `screenshots/03-auswertungen.png` — Auswertungen
- `screenshots/04-einstellungen-modal.png` — Benutzerverwaltung
- `screenshots/05-email-designer.png` — E-Mail Designer
- `screenshots/06-arbeitsbereich.png` — Rechnungsservice Arbeitsbereich
- `screenshots/07-tablet-768.png` — Tablet-Responsiveness
- `screenshots/08-mobile-375.png` — Mobile (kaputt)
- `screenshots/09-rechnungsservice-einstellungen.png` — Einstellungsseite (full-page)
