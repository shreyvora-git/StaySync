# StaySync — DESIGN.md (Java Swing Edition)

**Design north star:** *Calm, premium, and unmistakably useful.* A complaint tool should feel like relief, not paperwork.
**Reference (quality bar only):** https://wisprflow.ai/ — we study its craft; we do **not** copy its copy, logo, assets, or exact layout.
**Platform:** Java Swing desktop app. Everything here is achievable with **Swing + FlatLaf + Java2D** — no web technology.

---

## 0. Read this first

1. The teardown in §1 is based on the **page structure, copy, component hierarchy, and interaction sequence** of wisprflow.ai as published in its HTML content. I could **not** read its stylesheet, so exact hex values, fonts, and animation timings are **not** extracted; anything marked **(inferred)** is a design reading — verify in a browser's DevTools if you need exact numbers.
2. A website cannot be ported 1:1 to Swing. §2 explains what is translated, what is approximated, and what is dropped (e.g., CSS `backdrop-filter` blur has no cheap Swing equivalent).
3. **Tokens in §4 are StaySync's own** and are the source of truth. Library class names (FlatLaf etc.) are accurate to the best of my knowledge; **verify them against the exact version you pin.**

---

## 1. Cinematic Teardown of wisprflow.ai

Read it as a film: eleven scenes, one continuous scroll.

### Scene 0 — Cold open: announcement bar
A slim bar above everything carries one line of news and an inline link. (The text is repeated in the markup, suggesting a ticker on small screens.) *Purpose:* one line, zero weight, one action.

### Scene 1 — Title card: navigation
Wordmark left; a **segmented product switcher** ("Dictation | Notetaker") center, separated by a small divider glyph; text links and exactly **one filled pill CTA** right. A sticky mobile app banner appears with a close button. *Purpose:* orient, then get out of the way.

### Scene 2 — The hero: one sentence, one transformation
Tiny uppercase **eyebrow**; headline **"Don't type, *just speak.*"** — two beats, the second in an **italic emphasis style (inferred: editorial italic against clean sans)**; one subline; one CTA; one availability line. The centerpiece is a **before/after text transformation**: a rambling spoken paragraph beside its clean, corrected version. The value is proven on the first screen without a video.

### Scene 3 — Proof marquee: "Used by professionals at"
Monochrome logos duplicated in the markup — an **infinite horizontal marquee** (inferred: slow linear scroll, faded edges).

### Scene 4 — The duel: "4x faster than typing"
Two cards: **Keyboard — 45 wpm** vs **Flow — 220 wpm**, the same paragraph typing in both; the faster card finishes first. Cards sit on **soft gradient imagery**. A metric turned into a race.

### Scene 5 — How it works: tabbed demo
Heading with italic emphasis. Three tabs (**Speak naturally / Flow edits as you speak / Use it anywhere**), each on its own gradient backdrop with a scripted demo inside a mock app window: raw text → "Cleaning up…" → polished text, with annotation chips popping in (**"Filler identified," "Correction identified," "Repetition identified"**).

### Scene 6 — Personalization bento
Four cards, each with a **miniature live UI mock-up** (tone selector Formal/Casual/Very casual; "Add a new word" dialog; language chips; snippets), plus a bold title and two-line explanation.

### Scene 7 — Trust beat: "Your voice *stays yours.*"
Quiet statement, compliance badge row, "Learn more". Answers the objection before it forms.

### Scene 8 — Voices
Quote cards (avatar + name + role) mixed with **case-study cards with big stat callouts** ("90% faster message output").

### Scene 9 — Q&A: FAQ accordion
Rich answers (bold lead-ins, bullets, links) to real objections.

### Scene 10 — Closing shot: final CTA
Echoes the hero's rhythm — **"You have a way with words. *Now, two.*"** — a callback line, one button.

### Scene 11 — End credits: footer
Product cards with previews, four link columns by persona/resource, a **huge wordmark**, legal links.

### 1.1 The director's style (cross-cutting patterns)

| Pattern | What it is | StaySync (Swing) use |
|---|---|---|
| Italic emphasis in headlines | Last 1–3 words in a contrasting italic | Welcome headline, success/empty-state titles (never body UI) |
| Show, don't describe | Real-looking UI in every section | Real components (ticket card, timeline) used as the hero illustration |
| Before → After | The transformation is the story | Messy complaint text → structured ticket card |
| Annotation chips | Pills that "explain" what just happened | "Location noted", "Category: Water", "Needs triage" |
| One CTA per scene | Never competing buttons | One primary button per screen |
| Soft gradient stages | Pastel gradient behind demo cards | Java2D "aurora" gradients behind hero/empty states |
| Generous whitespace | Sections breathe | 24–32 px card padding, 24 px gutters |
| Scroll-triggered sequencing | Demos start when visible | Welcome sequence starts on window show; screen-level animations start when the screen is shown; play once |
| Audience personalization | Persona pages | **Students \| Admins** switch |
| Quiet trust language | Plain statements | Confidentiality notes, no hype |
| Inline error pattern | Hidden "Something went wrong… Try again." banner | `ErrorBanner` with retry |

---

## 2. Web → Swing Translation

| Wispr Flow (web) | StaySync (Swing) | Fidelity |
|---|---|---|
| Landing page scroll story | **Welcome screen**: split window — left "stage" (aurora, headline, animated hero demo, category marquee), right sign-in card | Approximated |
| Dictation / Notetaker switcher | **Students \| Admins** segmented control above the form | Faithful |
| Messy speech → clean text | Messy complaint (typewriter) → structured ticket card, then ticket cycles Pending → In Progress → Resolved | Faithful |
| Logo marquee | Category chip marquee at the bottom of the stage | Faithful |
| Keyboard-vs-Flow duel | Replaced by the ticket lifecycle loop (no invented numbers) | Adapted |
| Tabs with demos | 3-step dot stepper under the hero demo (Report · Track · Resolved) | Adapted |
| Bento cards | Dashboard card grid (stat cards, quick actions) | Faithful in spirit |
| Compliance badges | Confidentiality commitments (what we actually enforce) | Honest |
| Case-study stat callouts | KPI stat cards (count-up) | Faithful |
| `backdrop-filter` blur | Not available → solid translucent surfaces | Dropped |
| CSS transforms / 3D | `JLayer` + `AlphaComposite` offsets; no 3D | Approximated |
| Scroll-driven animation | Not applicable (screens aren't long pages) | Dropped |
| Responsive breakpoints | Window-width breakpoints (sidebar collapse, column stacking) | Adapted |
| Web fonts | FlatLaf bundled Inter / JetBrains Mono; Instrument Serif TTF bundled | Faithful |

---

## 3. Brand Identity

**Name:** StaySync — *stay* (hostel life) + *sync* (everyone aligned). **Tagline:** *Your Hostel. In Sync.*
**Personality:** calm, clear, fair. Never alarmist, never bureaucratic.

**Voice rules:** short sentences, active verbs, plain words ("fix", "update", "report"). Reassure on sensitive screens; never joke on safety flows. Errors say what happened and what to do next.

**Logo:** wordmark + a **double-arc "sync" glyph** (two interlocking arcs = student and admin in agreement). Ship as SVG (`FlatSVGIcon`) with themable color; app icon = glyph on an accent rounded square, exported as 16/32/64/128/256/512 px PNGs for `Window.setIconImages`.

**Copy bank**

| Place | Copy |
|---|---|
| Welcome headline | Don't just complain. *Get it fixed.* |
| Welcome sub | One place to report mess, room, water, and safety issues — and watch each one get resolved. |
| Primary action | Sign in · Create account |
| Empty: My Complaints | Nothing reported yet. When something's not right, we're one click away. |
| Empty: Notifications | You're all caught up. |
| Success: submitted | Complaint #SS-0042 is in. We'll notify you the moment it moves. |
| Safety notice | If you or someone else is in immediate danger, contact hostel security or emergency services right now. |
| Confidential reassurance | Only authorized staff can see this. Other students never can. |
| Closing line (Welcome footer) | You've got a voice. *Now, a record.* |

---

## 4. Design Tokens

Tokens live in **FlatLaf properties files** (themed) and are read in code through one class, `Tokens` (`UIManager.getColor("StaySync.xxx")`). **No hard-coded colors, fonts, or radii in components.**

### 4.1 Wiring (how Swing consumes the tokens)

```
src/main/resources/com/staysync/ui/theme/
  FlatLaf.properties          # shared (radii, sizes, fonts)
  FlatLightLaf.properties     # light overrides + StaySync.* colors
  FlatDarkLaf.properties      # dark overrides + StaySync.* colors
```
```java
FlatInterFont.install();                         // flatlaf-fonts-inter
FlatJetBrainsMonoFont.install();                 // flatlaf-fonts-jetbrains-mono
FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
FlatLaf.registerCustomDefaultsSource("com.staysync.ui.theme");
FlatLightLaf.setup();                            // or FlatDarkLaf.setup()
```
Theme change at runtime (animated cross-fade, `flatlaf-extras`):
```java
FlatAnimatedLafChange.showSnapshot();
FlatDarkLaf.setup();           // or FlatLightLaf.setup()
FlatLaf.updateUI();
FlatAnimatedLafChange.hideSnapshotWithAnimation();
```
Persist the choice with `java.util.prefs.Preferences` (`light | dark | system`). For `system`, detect via `FlatLaf.isLafDark()`/OS detection (e.g., `com.formdev.flatlaf.util.SystemInfo` + `FlatSystemProperties`/`DarkModeDetector` if available in your version) or default to Light and expose the explicit toggle.

### 4.2 Color tokens

**Light** (`FlatLightLaf.properties`)
```properties
@accentColor = #4F46E5
@background = #FAF8F5
@foreground = #17150F

Panel.background = #FAF8F5
StaySync.bg.subtle = #F3F0EB
StaySync.surface = #FFFFFF
StaySync.surface2 = #FBFAF8
StaySync.scrim = #17150F73

Component.borderColor = #E8E3DB
StaySync.border = #E8E3DB
StaySync.border.strong = #D6CFC4

StaySync.text = #17150F
StaySync.text.muted = #5E5A52
StaySync.text.faint = #8A857B

StaySync.accent = #4F46E5
StaySync.accent.hover = #4338CA
StaySync.accent.soft = #EEEDFF
StaySync.accent.on = #FFFFFF

StaySync.status.pending.fg = #8A5200
StaySync.status.pending.bg = #FFF1D6
StaySync.status.progress.fg = #1D4ED8
StaySync.status.progress.bg = #E3EDFF
StaySync.status.resolved.fg = #11693A
StaySync.status.resolved.bg = #DDF5E6

StaySync.prio.low.fg = #475569
StaySync.prio.low.bg = #ECEFF3
StaySync.prio.med.fg = #9A3412
StaySync.prio.med.bg = #FFE8D9
StaySync.prio.high.fg = #B42318
StaySync.prio.high.bg = #FFE3E0
StaySync.prio.none.fg = #6B6860
StaySync.prio.none.bg = #F1EEE8

StaySync.conf.fg = #6B2D7B
StaySync.conf.bg = #F5E6FA

StaySync.danger = #C0362C
StaySync.success = #15803D
StaySync.warning = #B45309
StaySync.info = #1D4ED8

# Aurora blobs (painted as radial gradients)
StaySync.aurora.1 = #FFD9C2
StaySync.aurora.2 = #D9D6FF
StaySync.aurora.3 = #CFE9FF

# Chart palette (Okabe–Ito, color-blind safe)
StaySync.chart.1 = #0072B2
StaySync.chart.2 = #E69F00
StaySync.chart.3 = #009E73
StaySync.chart.4 = #CC79A7
StaySync.chart.5 = #56B4E9
```

**Dark** (`FlatDarkLaf.properties`)
```properties
@accentColor = #8E94FF
@background = #0F0E0C
@foreground = #F4F1EA

Panel.background = #0F0E0C
StaySync.bg.subtle = #151411
StaySync.surface = #1B1A17
StaySync.surface2 = #211F1B
StaySync.scrim = #00000099

Component.borderColor = #2C2A25
StaySync.border = #2C2A25
StaySync.border.strong = #3B3830

StaySync.text = #F4F1EA
StaySync.text.muted = #B3AEA3
StaySync.text.faint = #7F7A70

StaySync.accent = #8E94FF
StaySync.accent.hover = #A5AAFF
StaySync.accent.soft = #23244A
StaySync.accent.on = #0F0E0C

StaySync.status.pending.fg = #F5C26B
StaySync.status.pending.bg = #2E2410
StaySync.status.progress.fg = #8FB3FF
StaySync.status.progress.bg = #14213D
StaySync.status.resolved.fg = #6EDC9A
StaySync.status.resolved.bg = #10291C

StaySync.prio.low.fg = #A7B2C2
StaySync.prio.low.bg = #232A33
StaySync.prio.med.fg = #FFB48A
StaySync.prio.med.bg = #35201A
StaySync.prio.high.fg = #FF9A91
StaySync.prio.high.bg = #3A1B19
StaySync.prio.none.fg = #A39E93
StaySync.prio.none.bg = #26241F

StaySync.conf.fg = #E0A8F0
StaySync.conf.bg = #2D1B36

StaySync.danger = #FF8A80
StaySync.success = #5FD38A
StaySync.warning = #F5B25C
StaySync.info = #8FB3FF

StaySync.aurora.1 = #3A2418
StaySync.aurora.2 = #25234F
StaySync.aurora.3 = #12304A

StaySync.chart.1 = #56B4E9
StaySync.chart.2 = #F0B429
StaySync.chart.3 = #2FBF9B
StaySync.chart.4 = #E39BC4
StaySync.chart.5 = #7FC8F8
```

**Color rules**
- Status/priority are **never color-only**: status chips carry a dot, priority chips carry bar icons, confidential carries a shield — each with a text label.
- Body text uses `text` or `text.muted`; `text.faint` is decorative or ≥ 18 px only.
- One accent. Gradients only on stages (Welcome, empty states), never on buttons or data.
- Light contrast: `text` on `bg` ≈ 16.9:1, `text.muted` ≈ 6.4:1, `accent.on` on `accent` ≈ 6.3:1. Re-check any new pairing.

### 4.3 Shape, size, and spacing tokens (`FlatLaf.properties`)

```properties
Component.arc = 12
Button.arc = 12
TextComponent.arc = 12
CheckBox.arc = 6
ProgressBar.arc = 999
ScrollBar.thumbArc = 999
ScrollBar.width = 10
Component.focusWidth = 2
Component.innerFocusWidth = 0
Table.rowHeight = 56
Table.showHorizontalLines = true
Table.showVerticalLines = false
Table.intercellSpacing = 0,1
TableHeader.height = 40
Button.minimumWidth = 80
StaySync.radius.sm = 8
StaySync.radius.md = 12
StaySync.radius.lg = 20
StaySync.radius.xl = 28
```
- **Spacing:** base unit 4 px → 4, 8, 12, 16, 20, 24, 32, 40, 48, 64. Use `Tokens.space(n)`; sizes auto-scale with HiDPI (use `UIScale.scale(int)` only for custom painting).
- **Elevation:** light theme → soft cached drop-shadow border (`ShadowBorder`, blur 16–24, alpha 0.07–0.14); dark theme → 1 px border + faint shadow (shadows read poorly on dark). Interactive cards raise one level on hover.
- **Window:** default 1280 × 800, **minimum 1000 × 680**. Sidebar 248 px (collapses to 72 px icon rail below 1180 px window width); top bar 64 px; content max width 1120 px, centered.

### 4.4 Typography

| Role | Font | Notes |
|---|---|---|
| UI / body | **Inter** (FlatLaf font pack) | Default `defaultFont` |
| Headings | Inter, SemiBold, tight tracking via slightly larger sizes | `Fonts.heading(size)` |
| Emphasis (italic words) | **Instrument Serif Italic** (bundled TTF, OFL) | Fallback `Georgia` italic; used only in Welcome headline, success/empty titles |
| IDs, numbers in tables | **JetBrains Mono** (FlatLaf font pack) | e.g., `#SS-0042`, KPI numbers (tabular) |

| Token | Size (px @100%) | Weight | Use |
|---|---|---|---|
| `hero` | 56 | 600 | Welcome headline |
| `h1` | 32 | 600 | Screen titles on big screens |
| `h2` | 24 | 600 | Screen titles, KPI numbers |
| `h3` | 18 | 600 | Card titles |
| `body` | 14 | 400 | Default |
| `sm` | 13 | 400/500 | Table cells, helper text |
| `xs` | 12 | 600 | Chips, eyebrows (uppercase, extra letter-spacing via `TextAttribute.TRACKING`) |

Mixed-font headings are drawn by one component, `EmphasisHeading`: it lays out runs (regular + serif-italic) with `TextLayout`/`LineBreakMeasurer`, wraps to width, and reports a correct preferred size and baseline. Do **not** fake it with two side-by-side `JLabel`s (breaks on wrapping).

### 4.5 Motion tokens

| Token | Value | Use |
|---|---|---|
| `instant` | 90 ms | Press feedback |
| `fast` | 160 ms | Hover, focus, chip changes |
| `base` | 260 ms | Menus, toasts, screen cross-fade |
| `slow` | 480 ms | Count-ups, chart draw-in, first reveal |
| `stage` | 900 ms | Welcome sequence beats |
| `easeOut` | `cubic-bezier(0.22, 1, 0.36, 1)` | Entrances |
| `easeInOut` | `cubic-bezier(0.65, 0, 0.35, 1)` | State changes |
| `spring` | `cubic-bezier(0.34, 1.56, 0.64, 1)` | Badge pop (sparingly) |

---

## 5. Motion System in Swing ("the camera moves")

**Engine:** one small `Motion` utility wrapping FlatLaf's `com.formdev.flatlaf.util.Animator` (+ `CubicBezierEasing`), 60 fps, always started/updated on the EDT. If your FlatLaf version lacks these, a 16 ms `javax.swing.Timer` tween is an acceptable drop-in.

**Principles:** purposeful, short, interruptible, never blocking. Animate **paint-level properties** (alpha, offset, progress) of one component and `repaint()` only its bounds. **Never animate layout** (bounds of components inside managed layouts) except the single-panel collapse/expand helper.

| Technique | Swing implementation |
|---|---|
| Fade / slide a whole panel | Wrap in `JLayer` with a custom `LayerUI<JComponent>` applying `AlphaComposite` and a translate in `paint()` |
| Screen transition | `ScreenHost` takes a snapshot image of the old screen, swaps in the new one, cross-fades snapshot → live over `base` (260 ms) |
| Staggered card entrance | Each `Card` has `revealProgress` (0→1); delay = index × 40 ms (max 8) |
| Count-up numbers | `StatCard` animates displayed value 0 → N over `slow` with `easeOut` |
| Chart draw-in | Charts paint with a `progress` factor (bars grow, line clips left→right) once on first display |
| Hover / press | Component-level color tween (`fast`); lift = shadow alpha tween |
| Skeleton shimmer | `SkeletonPanel` paints a moving gradient, 1.4 s loop, one shared 30 fps timer, stops when not showing |
| Toasts | `ToastManager` on the root pane's layered pane (POPUP layer): slide + fade in `base`, auto-dismiss 4 s, pause on hover |
| Badge pop | Scale 0.8 → 1.1 → 1 with `spring` when unread count increases |
| Status chip change | Cross-fade colors over `fast` + one pulse ring |
| Theme switch | `FlatAnimatedLafChange` snapshot cross-fade |
| Marquee | Painted strip, 40 px/s, pause on hover |

**Reduce motion:** Settings switch (and `Motion.enabled()` guard everywhere). When on: no translate/scale, 100 ms fades only, no marquee/pulse/float/shimmer loops, instant count-ups. Default **off**; first run may be seeded from a system property `-Dstaysync.reduceMotion=true`.

**Hard limits:** nothing in the authenticated app > 480 ms; Welcome sequence ≤ ~6 s, plays once, never delays the sign-in form.

### 5.1 Welcome screen choreography (≈ 5.5 s, plays once; "Replay" icon after)
1. 0.0 s — Left card "Overheard in the corridor" types the messy text (~45 chars/s).
2. 2.4 s — Thin accent progress line sweeps under it; caption **"Sorting it out…"**.
3. 3.0 s — Annotation chips pop in (80 ms stagger): `Location noted`, `Category: Water & Common Facilities`, `Needs triage`.
4. 3.6 s — Structured **ticket card** assembles: title writes in, category chip, status chip **Pending**, ID `#SS-0042` in mono.
5. 5.0 s onward — Ticket idles (±3 px float, 6 s loop) and its status chip cycles **Pending → In Progress → Resolved** every 2.5 s, with the 3-dot stepper (*Report · Track · Resolved*) following.

> This is a **scripted illustration**, not a live AI feature. Do not claim automatic categorization anywhere in the product.

---

## 6. Components (`ui.components`)

Each component supports default, hover, focus (visible ring), pressed, disabled, loading, error (where relevant) in both themes. Click targets ≥ 36 px tall (default 40).

| Component | Spec |
|---|---|
| **AppButton** (Primary / Secondary / Ghost / Danger) | Primary: accent fill, `accent.on` text, arc 12 (pill on Welcome). Secondary: surface + strong border. Ghost: text only, subtle hover. Danger: `danger` fill. Heights 32 / 40 / 48. Loading state swaps label for a spinner, same width, `aria-busy` equivalent via accessible description |
| **ChipLabel** (StatusChip, PriorityChip, CategoryChip, ConfidentialChip) | Height 24, pill, `xs` bold. Status: dot (In Progress dot pulses slowly; Resolved shows ✓). Priority: 1–3 bar icon; `Unassigned` uses dashed border. Confidential: shield icon + "Private" |
| **Card** | Surface fill, 1 px border, radius `lg` (20), padding 20–24, soft shadow; `interactive` variant lifts on hover |
| **StatCard** | Eyebrow label (uppercase `xs`), big mono number (`h2`/`h1`), sub-label, optional sparkline; count-up on first show |
| **FormField** | Label **above** the field (no floating labels), 40 px input, arc 12, focus ring = accent 2 px; error = danger border + icon + message below, announced via accessible description |
| **TextArea with counter** | `142 / 2000` bottom-right, turns `warning` at 90 % |
| **DropZone** | Dashed rounded border, icon + "Drop a photo or browse", `TransferHandler` for drag-and-drop, thumbnails (`ImageIO` scaled off-EDT) with ✕ remove, per-file error text |
| **SegmentedControl** | Used for Students\|Admins, Light\|Dark\|System, 30\|90 days, status tabs; animated pill indicator slides between segments (`fast`) |
| **DataTable** | `JTable` + custom renderers: sticky header (inside `JScrollPane`), 56 px rows, hairline dividers, hover row highlight, chips painted in cells, ID in mono, truncated titles with tooltip, sortable headers (server-side) |
| **PaginationBar** | "Showing 11–20 of 134", Prev/Next, page-size combo; keyboard accessible |
| **FilterBar** | Debounced (300 ms) search field, status/category/priority combos, date range, "Clear filters"; active filters shown as removable chips |
| **Timeline** | Vertical line, node per event with type icon; "Moved to In Progress", actor chip, note, relative time (absolute in tooltip); staggered reveal (60 ms) |
| **ConversationView** | Bubbles with name + role badge (never color-only); internal notes: dashed amber-tinted block with lock icon and "Internal — students can't see this"; composer with `Ctrl+Enter` to send; disabled for students on Resolved |
| **NotificationBell** | Custom-painted bell + unread badge ("9+" max); click opens a popover (`JPopupMenu`-based panel) with last 6 items, "Mark all read", "View all"; confidential items show a shield and generic text |
| **ModalLayer** | In-window modal: root-pane **glass pane** with scrim + centered card, `Esc` closes, focus cycle root restricts Tab to the dialog, focus restored to the trigger. Native `JOptionPane` is avoided except for fatal errors |
| **Toast** | Success / error / info; top-right; ≤ 3 stacked; error toasts stay until dismissed |
| **EmptyState** | Soft aurora tile, line icon, one sentence, one action |
| **SkeletonPanel** | Mirrors final layout (rows, cards, chart frames); replaces spinners for screen loads |
| **ErrorBanner** | Inline: icon + message + **Try again** |
| **SafetyNotice** | Accent-soft banner with shield icon; always visible when the Safety category is selected |
| **Charts** (`BarChart`, `LineChart`, `DonutChart`) | Custom Java2D (see §9) |
| **ThemeToggle** | Icon button in top bar and Welcome screen; cycles Light → Dark → System; tooltip shows mode |
| **AvatarMenu** | Initials circle; popup with Profile, Log out |
| **Accordion** | Used in a "Help" panel (FAQ) in Settings; height animates via a `JLayer`-free collapse helper |

Icons: **Lucide** SVGs (ISC license) rendered via `FlatSVGIcon` with theme-aware color filters (`setColorFilter`), sizes 16/20/24.

---

## 7. Welcome & Sign-in Screens (screens 1–2)

One window, split layout, minimum 1000 × 680.

**Left "stage" (≈ 55 %)** — paints the aurora (three `RadialGradientPaint` blobs on `bg`, cached to a `BufferedImage` and re-rendered on resize/theme change)
- Top-left: logo + wordmark.
- Eyebrow `STAYSYNC · HOSTEL COMPLAINTS`.
- `EmphasisHeading`: "Don't just complain. *Get it fixed.*" (hero size; scales to `h1` below 1180 px width).
- Subline (body-lg 16 px, muted).
- **Hero demo** (§5.1): two cards connected by a faint arrow; chips; ticket card; 3-dot stepper.
- Bottom: **category marquee** (five category chips looping) labelled "Everything hostel life throws at you".
- Footer line (shield icon): "Safety reports stay private. Only authorized staff can see them." — and, in small print, the immediate-danger notice.

**Right card (≈ 45 %)** on `surface`, vertically centered, max width 400
- Top: **SegmentedControl "Students | Admins"** — switching changes the heading ("Welcome back" / "Staff sign-in") and hides "Create account" for Admins.
- Sign-in form: email, password (show/hide eye), "Sign in" primary, inline error banner (generic "Invalid email or password"), lockout message with countdown.
- Register view (Students only): full name, email, password + strength meter, confirm password; "Create account". Transition between sign-in/register: horizontal slide + fade (`base`).
- Bottom: theme toggle, "Admin accounts are created by hostel staff."
- Enter key submits; focus starts in the email field; first invalid field gets focus.

---

## 8. Application Shell and Screens (3–12)

### Shell
- **Sidebar** (`surface`, right border): logo; nav items with icon + label; active item = `accent.soft` pill with accent icon; collapses to a 72 px icon rail (tooltips) below 1180 px window width; collapse animates width over `base` (the one allowed layout animation).
  - Student: Dashboard · New complaint · My complaints · Notifications · Settings
  - Admin: Dashboard · Complaints · Analytics · Categories · Notifications · Settings
- **Top bar** (64 px): screen title (left), global search (admin), notification bell, theme toggle, avatar menu.
- **Content host:** `ScreenHost` with cross-fade transitions; content scrolls in a `JScrollPane` with thin custom scrollbars; max content width 1120 px.
- Keyboard: `Ctrl+N` new complaint (student), `Ctrl+F` focus search, `Ctrl+Shift+D` toggle theme, `Alt+1…6` sidebar items, `F5` refresh.

### 3 · Student Dashboard
Greeting ("Good evening, Aarav."), **3 StatCards** (Open · In progress · Resolved), a **"Report something"** primary card (aurora background), **Recent complaints** (5 rows), **Latest updates** (3 notifications). Loading → skeleton; no data → EmptyState.

### 4 · Submit Complaint
Single column, max 640. Order: **Category card-select grid** (icons; Safety card shows a shield), Title, Description (counter), Location, Urgency (SegmentedControl Low/Medium/High; helper: "Helps staff understand how pressing this feels. Staff set the final priority."), Photos DropZone, Submit. Safety selection reveals `SafetyNotice` and the "Private" reassurance with a short height-expand animation. Success view: animated check draw-in, ID in mono, **Track it** / **Report another**.

### 5 · My Complaints
Status tabs (All / Pending / In Progress / Resolved with counts) → FilterBar → DataTable → PaginationBar. Row double-click or Enter opens details. Empty state when no match ("Clear filters").

### 6 · Complaint Details & Conversation
Two columns (≥ 1100 px; stacks below). Left: header (mono ID, title, chips, created, location), description, attachment thumbnails (click → in-window lightbox), **ConversationView** + composer. Right (340 px): **Status card** (status, priority, assigned, resolution notes) and **Timeline**. **Admin variant** adds an **Action panel**: status combo, priority combo, assignee combo, change note, **Update** (primary), and a composer toggle *Reply to student / Internal note*. Confidential banner at the top where applicable. Hidden/unauthorized/missing → the same "We can't find that complaint." state.

### 7 · Notifications
Grouped Today / Earlier; unread accent dot; All / Unread segmented filter; "Mark all as read"; row click opens the complaint.

### 8 · Admin Dashboard
Row 1: **4 KPI StatCards** (Total · Pending · In progress · Resolved). Row 2: **Complaints by category** (bar) + **Trend** (line, 30/90 toggle). Row 3: **Needs triage** table (Pending, no priority, oldest first) + **Recent activity**. Authorized Admins also see a visually separated **Private reports** count card.

### 9 · Complaint Management
FilterBar (search, status, category, priority, dates), DataTable with multi-select (bulk status = Could), right-hand **quick-view side panel** (slides in over `base`) with "Open full screen". **Export CSV** secondary button; the confidential export is a separate menu item that opens a confirm modal (Authorized Admins only). Export progress shown in a toast with a progress bar.

### 10 · Analytics & Reports
Range SegmentedControl (30/90 days) → KPI row → charts grid: by category (bar), trend (line), status distribution (donut), resolution time by priority (bar), average resolution time (big number). Footnote "Excludes private complaints." Authorized Admins: separate "Private overview" panel (counts only). Each chart has a "View data" toggle that swaps to a small table (a11y alternative).

### 11 · Category Management
Table: name, description, usage count, confidential chip, active switch. **Add / Edit** modal: name, description, confidential switch with warning "Complaints in this category will be hidden from general admins." Deactivate → confirm modal.

### 12 · Profile & Settings
Cards: **Profile** (name), **Security** (change password), **Appearance** (Light/Dark/System + Reduce motion switch), **Account** (email, role read-only), **Users & Access** (Authorized Admins: table of users with Suspend/Activate, "Grant confidential access", "Create admin" modal), **Help** (FAQ accordion).

---

## 9. Charts (custom Java2D)

- Components extend `JComponent`, paint with `Graphics2D` (`KEY_ANTIALIASING` on, `KEY_STROKE_CONTROL` pure), colors from `StaySync.chart.*` and status tokens, fonts from `Fonts`.
- Style: horizontal gridlines only (1 px `border`), rounded bar tops (6 px), 2.5 px lines with a dot on hover, tooltip card (surface + shadow) following the mouse, legend as chips.
- Draw-in animation once on first display (`slow`); instant under Reduce motion.
- Each chart: title, one-line takeaway, accessible description (`AccessibleContext`), "View data" table alternative; empty data → EmptyState tile.
- JFreeChart is acceptable **only** if custom charts slip the schedule; if used, override all colors/fonts from tokens.

---

## 10. States, Errors, Microcopy

| State | Pattern |
|---|---|
| Screen loading | SkeletonPanel shaped like content |
| Action loading | Button spinner + disabled; optimistic UI only for mark-as-read |
| Empty | Icon + sentence + action |
| Validation | Inline, specific ("Title needs at least 5 characters") |
| Service error | ErrorBanner with Try again; never stack traces or SQL text |
| Not found / no access | "We can't find that complaint." (same for hidden confidential records) |
| Idle lock | Returns to sign-in with toast "Locked after 30 minutes of inactivity." |
| DB unreachable | Full-screen state: "Can't reach the database. Check your connection settings." + Retry + "Open settings file location" |

---

## 11. Accessibility Checklist

- Contrast ≥ 4.5:1 text, ≥ 3:1 for UI boundaries and chart marks.
- Everything reachable by keyboard; logical focus order (`FocusTraversalPolicy` where needed); **visible focus ring**.
- `setAccessibleName`/`AccessibleDescription` on icon buttons, charts, custom-painted components (bell, chips, stat cards).
- Mnemonics on dialog buttons; `Enter`/`Esc` mapped to default/cancel actions.
- Custom components expose `AccessibleRole` and state (e.g., `SegmentedControl` as radio group).
- Don't rely on color alone (dots, icons, text).
- Marquee, shimmer, and floating elements stop under Reduce motion and when the window is unfocused.
- Respect OS text scaling/HiDPI: no fixed pixel math in custom painting without `UIScale`.

---

## 12. Swing Craft Rules (what makes it look premium)

1. **FlatLaf everywhere**; never mix with Metal/Nimbus. Don't call `setBackground` with literals — use tokens.
2. **No `null` layouts.** Use MigLayout (or `GridBagLayout`/`BorderLayout`) so HiDPI and resize behave.
3. **Antialias everything you paint**; paint into cached `BufferedImage`s for expensive effects (aurora, shadows).
4. **Repaint narrowly:** `repaint(x, y, w, h)` or component-level; never `frame.repaint()` in animation loops.
5. **EDT discipline:** UI mutations only on the EDT; slow work in `SwingWorker`/executor, results posted back with `SwingUtilities.invokeLater`.
6. **One timer per concern**, stopped when the component is hidden (`HierarchyListener`).
7. **Dispose** animators and listeners in `removeNotify()`.
8. Test at 100/125/150/200 % scaling and in both themes before calling a screen done.

---

## 13. Do / Don't

**Do:** lead with the product in action; one primary action per screen; whitespace over borders; test both themes and three window sizes.

**Don't:** copy Wispr Flow text, logos, imagery, or layout; fabricate testimonials, logos, certifications, statistics; use pure `#000/#FFF` backgrounds, heavy shadows, or neon gradients; put sensitive complaint text in toasts, window titles, tooltips, or logs; animate layout; block the EDT.

---

## 14. Design QA (per screen)

- [ ] Tokens only (no raw colors/fonts/sizes in component code)
- [ ] Light and dark verified
- [ ] 1000×680, 1280×800, 1920×1080 verified; HiDPI 100/125/150 %
- [ ] Loading, empty, error, success states exist
- [ ] Keyboard-only walkthrough passes
- [ ] Reduce motion verified
- [ ] Contrast checked
- [ ] No UI freeze with a 3 s artificial DB delay
- [ ] Confidential content never appears in toasts, notifications, tooltips, charts, window titles
