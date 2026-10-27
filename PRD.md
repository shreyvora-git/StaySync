# StaySync — Product Requirements Document (PRD)

**Formal title:** Hostel Mess Complaint Tracker
**Tagline:** *Your Hostel. In Sync.*
**Type:** DBSC (Database System Concepts) microproject
**Platform:** Desktop application — **Java Swing** GUI, Java application logic, **JDBC**, **MySQL** (developed in MySQL Workbench)
**Doc status:** v2.0 — Swing edition (supersedes the web-stack draft)
**Companion docs:** `DESIGN.md` (UI/UX), `ARCHITECTURE.md` (tech + database), `.cursorrules` (AI-agent rules)

> **Stack is fixed:** the entire frontend is Java Swing. No React, JavaScript, HTML/CSS, or web frameworks anywhere in the project. This also matches the original microproject guideline (Java Swing UI).

---

## 1. Summary

StaySync is a modern desktop application where hostel residents report issues (food, mess, room, washroom, water, safety) and hostel administrators triage, resolve, and communicate on them. Every complaint becomes a tracked record with a unique ID, status, priority, conversation thread, and audit trail.

**Core objective:** make hostel issue reporting easier, make progress visible, and make resolution accountable.

**Why it exists:** verbal complaints and scattered messages leave no record, no owner, and no deadline. StaySync replaces them with one structured, trackable process.

---

## 2. Goals and Non-Goals

### Goals
| # | Goal | How we know it is met |
|---|------|----------------------|
| G1 | Students can raise a complaint in under 60 seconds | Submit screen needs ≤ 6 inputs (category, title, description, optional location, optional urgency, optional photo) |
| G2 | Every complaint is traceable end to end | Unique ID, status timeline, history rows for every status/priority change |
| G3 | Admins can triage and resolve efficiently | Filter, search, update status/priority, add notes from one screen |
| G4 | Sensitive complaints stay private | Confidential complaints are invisible to unauthorized users in lists, counts, charts, exports, notifications, attachments |
| G5 | The database design is demonstrably sound | 7 normalized tables, PK/FK/unique/NOT NULL/CHECK, JDBC transactions, views, trigger, aggregate queries |
| G6 | The app feels premium for a Swing app | Follows `DESIGN.md`: FlatLaf-based theming, light + dark, refined typography, animated cards, no UI freezes |

### Non-Goals
- Emergency response or real-time safety dispatch (the app must tell users to contact hostel security or emergency services directly in urgent danger).
- Voice input, AI classification, or automated resolution (see "Could Have" for optional keyword-based category suggestion).
- Email/SMS/push delivery (in-app notifications only).
- Web or mobile versions, REST APIs, or any browser frontend.
- Multi-hostel or multi-tenant support.
- Payments, room allocation, attendance.
- Reproducing Wispr Flow's branding, copy, or assets. It is a **visual quality reference only**.

---

## 3. Users and Roles

### 3.1 Student
A hostel resident with a self-registered account. Can only see **their own** complaints.

### 3.2 Admin
Hostel staff (warden, mess manager, maintenance coordinator). Admin accounts are created **by seed script or by an Authorized Admin inside the app — never through the registration screen**.

### 3.3 Authorized Admin (Confidential Access)
An Admin with `can_view_confidential = TRUE` (e.g., warden, welfare officer). The only role that can see, comment on, update, or export confidential complaints and their attachments.

### 3.4 Permission Matrix

| Capability | Student | Admin | Authorized Admin |
|---|:--:|:--:|:--:|
| Register / log in | ✅ (register) | ✅ (login only) | ✅ (login only) |
| Create complaint | ✅ | ❌ | ❌ |
| View own complaints | ✅ | n/a | n/a |
| View all non-confidential complaints | ❌ | ✅ | ✅ |
| View confidential complaints | Own only | ❌ | ✅ |
| Change status / priority | ❌ | ✅ (non-conf.) | ✅ (all) |
| Public comment | Own only | ✅ (non-conf.) | ✅ (all) |
| Internal note | ❌ (cannot see) | ✅ (non-conf.) | ✅ (all) |
| Analytics (general) | ❌ | ✅ | ✅ |
| Analytics incl. confidential | ❌ | ❌ | ✅ (separate panel, counts only) |
| CSV export (general) | ❌ | ✅ | ✅ |
| CSV export (confidential) | ❌ | ❌ | ✅ (separate explicit action) |
| Manage categories | ❌ | ✅ | ✅ |
| Create admins / grant confidential access / suspend users | ❌ | ❌ | ✅ |

> The brief names two roles (Student, Admin). The third tier is a flag on Admin users (`can_view_confidential`), not a new role value. See §12.

---

## 4. Complaint Categories

Seeded at install; admins manage them from the Category Management screen — no code changes needed for new types.

| Category | Typical issues | Confidential |
|---|---|:--:|
| Food & Mess | Poor quality or hygiene; food not served on time; other mess issues | No |
| Electrical & Room Maintenance | Lights or fans not working; room maintenance; daily room cleaning not done | No |
| Washroom & Hygiene | Washroom cleanliness; flush not working; water cooler cleaning | No |
| Water & Common Facilities | No hot water in the morning; water cooler problems; shared facility upkeep | No |
| Safety & Student Welfare | Room safety; ragging or harassment; other personal safety concerns | **Yes** |

Rules:
- Deactivated categories disappear from the submit screen but remain on historical complaints.
- A category cannot be deleted if any complaint references it (FK `RESTRICT`); deactivate instead.
- Changing a category's confidentiality does **not** retroactively change existing complaints (each complaint snapshots `is_confidential` at creation).

---

## 5. Complaint Lifecycle

```
Student submits → [Pending] → Admin triages (sets priority) → [In Progress] → Admin records resolution → [Resolved]
```

| Rule | Detail |
|---|---|
| Statuses | `Pending`, `In Progress`, `Resolved` |
| Priorities | `Low`, `Medium`, `High`; **NULL until an admin triages** (UI shows "Unassigned") |
| Student urgency | Optional hint (`Low/Medium/High`) at submission; shown to admins; **never** sets priority |
| Allowed transitions | Pending → In Progress → Resolved. *Assumption:* an Admin may **reopen** Resolved → In Progress with a mandatory note. Enforced in the service layer **and** by a MySQL trigger |
| Resolving | Requires `resolution_notes`; sets `resolved_at` |
| Reopening | Clears `resolved_at`; history records the reopen |
| Every change | One `complaint_history` row + a notification, written in **one JDBC transaction** |
| Deletion | Complaints are never hard-deleted by the app (audit integrity) |

---

## 6. Functional Requirements

Priority: **M** = Must, **S** = Should, **C** = Could.

### 6.1 Authentication & Accounts (AUTH)
| ID | Requirement | P |
|---|---|:--:|
| AUTH-1 | Students register with full name, email, password (min 8 chars, strength meter) | M |
| AUTH-2 | Email unique (case-insensitive); duplicate shows a clear inline error | M |
| AUTH-3 | Login for all roles; role-based routing (student → Student Dashboard, admin → Admin Dashboard) | M |
| AUTH-4 | Passwords stored only as BCrypt hashes | M |
| AUTH-5 | Logout clears the in-memory session and returns to the Welcome screen | M |
| AUTH-6 | Admin accounts cannot be created from the registration screen | M |
| AUTH-7 | Suspended accounts cannot log in (`account_status`) | S |
| AUTH-8 | Login throttling: 5 failed attempts per email → 15-minute lockout (in-memory) | S |
| AUTH-9 | Change password from Profile & Settings | S |
| AUTH-10 | Auto-lock after 30 minutes of inactivity (returns to login) | S |

### 6.2 Complaint Submission (SUB)
| ID | Requirement | P |
|---|---|:--:|
| SUB-1 | Fields: category (required), title (5–150 chars), description (20–2000 chars), location note (optional ≤ 100), urgency hint (optional), photos (optional) | M |
| SUB-2 | Record created with status `Pending`, unique ID, `is_confidential` copied from the category | M |
| SUB-3 | Attachments via file chooser or drag-and-drop: JPEG/PNG/WebP*, max 5 MB each, max 3 per complaint; validated by decoding content (not extension) | M |
| SUB-4 | Complaint + attachment rows + history row + notifications saved atomically; copied files removed on rollback | M |
| SUB-5 | Choosing the Safety category shows: *"If you or someone else is in immediate danger, contact hostel security or emergency services now."* | M |
| SUB-6 | Inline validation, live character counters, success screen with the new complaint ID | M |
| SUB-7 | Keyword-based category suggestion while typing the title (no AI service) | C |

\* WebP requires an ImageIO plugin; if not bundled, accept JPEG/PNG only and say so in the UI.

### 6.3 Student Tracking (TRK)
| ID | Requirement | P |
|---|---|:--:|
| TRK-1 | "My Complaints" table: ID, title, category, status, priority (if assigned), created date | M |
| TRK-2 | Search by ID or title; filter by status, category, date range; server-side pagination | M |
| TRK-3 | Detail screen: description, attachments, status, resolution notes, timeline, conversation | M |
| TRK-4 | Student can comment while status ≠ Resolved; on Resolved the composer explains "Ask an admin to reopen" | M |
| TRK-5 | Students never see internal notes | M |
| TRK-6 | History view = all past complaints (Resolved filter on the same table) | M |

### 6.4 Admin Management (ADM)
| ID | Requirement | P |
|---|---|:--:|
| ADM-1 | Complaint queue across the hostel; filters: category, status, priority, date range; search by ID/title | M |
| ADM-2 | Update status and priority with an optional change note | M |
| ADM-3 | Assign a complaint to an admin (`assigned_admin_id`) | S |
| ADM-4 | Public replies (student-visible) and internal notes (admin-only) | M |
| ADM-5 | Resolution notes mandatory when resolving | M |
| ADM-6 | Full history timeline for any accessible complaint | M |
| ADM-7 | Category management: create, edit, activate/deactivate, mark confidential | M |
| ADM-8 | Bulk status update for selected rows | C |
| ADM-9 | Admins without confidential access never see confidential complaints, counts, or search hits | M |
| ADM-10 | Authorized Admins can create admin accounts, suspend users, and grant/revoke confidential access (Settings → Users & Access) | S |

### 6.5 Comments (CMT)
| ID | Requirement | P |
|---|---|:--:|
| CMT-1 | Thread per complaint, chronological, with author, role badge, timestamp | M |
| CMT-2 | Max 1000 chars; plain text only | M |
| CMT-3 | New comment notifies the other party | M |
| CMT-4 | Internal notes visually distinct ("Internal — students can't see this") | M |

### 6.6 Notifications (NTF)
| ID | Requirement | P |
|---|---|:--:|
| NTF-1 | Bell with unread badge in the top bar; popover with latest items; full Notifications screen | M |
| NTF-2 | Events: complaint submitted (student + eligible admins), status changed, priority changed, new comment, resolved | M |
| NTF-3 | Mark one / mark all read | M |
| NTF-4 | **Confidential complaints use generic wording** (no title, description, category name) | M |
| NTF-5 | Clicking a notification opens the related complaint | M |
| NTF-6 | Background poll every 30 s (single scheduled worker; no UI blocking) | S |
| NTF-7 | In-app toast when a new notification arrives while the app is open | S |

### 6.7 Search, Filter, Pagination (SRCH)
| ID | Requirement | P |
|---|---|:--:|
| SRCH-1 | Numeric input → exact ID; text → title `LIKE` (escaped) | M |
| SRCH-2 | Filters: status, category, priority (admin), from/to date | M |
| SRCH-3 | Server-side pagination: page size 10/25/50; default 10 | M |
| SRCH-4 | Sort by created, updated, priority, status (whitelisted columns) by clicking table headers | S |
| SRCH-5 | Filters/page/sort persist while navigating between screens in the same session | S |

### 6.8 Analytics (ANL)
| ID | Requirement | P |
|---|---|:--:|
| ANL-1 | KPI cards: total, pending, in progress, resolved | M |
| ANL-2 | Complaints by category (bar chart) | M |
| ANL-3 | Trend over time (line chart, 30/90-day toggle) | M |
| ANL-4 | Resolution performance: average hours to resolve, overall and by priority | M |
| ANL-5 | General charts **exclude** confidential complaints | M |
| ANL-6 | Separate "Private overview" panel visible only to Authorized Admins (counts only) | S |

### 6.9 CSV Export (EXP)
| ID | Requirement | P |
|---|---|:--:|
| EXP-1 | Export current filters to CSV via a Save dialog (admin) | M |
| EXP-2 | Columns: ID, title, category, status, priority, student name, created, updated, resolved, resolution notes | M |
| EXP-3 | Confidential rows only via a separate explicit action for Authorized Admins (confirmation dialog) | M |
| EXP-4 | Neutralize formula injection (prefix cells beginning `= + - @` with `'`); RFC 4180 quoting; UTF-8 with BOM for Excel | M |
| EXP-5 | Row cap (10,000) with a visible message if truncated; export runs in background with progress | S |

### 6.10 History & Audit (HIS)
| ID | Requirement | P |
|---|---|:--:|
| HIS-1 | Row at creation (`old_status` NULL → `Pending`) | M |
| HIS-2 | Row on every status and priority change with actor and timestamp | M |
| HIS-3 | Timeline UI: event, actor, before → after, note, relative + absolute time | M |
| HIS-4 | Append-only: no update/delete code path | M |

### 6.11 Profile & Settings (PRF)
| ID | Requirement | P |
|---|---|:--:|
| PRF-1 | View/edit name; view email and role | M |
| PRF-2 | Change password | S |
| PRF-3 | Appearance: Light / Dark / System; **Reduce motion** switch; persisted per OS user | M |
| PRF-4 | Users & Access panel (Authorized Admins only) | S |

### 6.12 Desktop Experience (UX)
| ID | Requirement | P |
|---|---|:--:|
| UX-1 | Resizable window, min 1000 × 680; layouts adapt (sidebar collapses to icon rail < 1180 px wide; tables flex) | M |
| UX-2 | Correct on HiDPI/scaled displays (100/125/150/200%) | M |
| UX-3 | Light and dark themes; instant, animated switch; no restart | M |
| UX-4 | Loading skeletons, empty states, inline form validation, toasts, friendly error banners on every screen | M |
| UX-5 | **UI never freezes:** all database/file work runs off the Event Dispatch Thread | M |
| UX-6 | Subtle animation (fades, slides, count-ups, chart draw-in); never delays an action; honors Reduce motion | M |
| UX-7 | Keyboard-navigable: logical tab order, mnemonics/shortcuts, visible focus; accessible names on all controls | M |
| UX-8 | Custom title-bar color matching the theme where the OS supports it | C |

---

## 7. Screens (12)

| # | Screen | Audience |
|---|---|---|
| 1 | Welcome (hero stage + audience switch) | Public |
| 2 | Login & Registration | Public |
| 3 | Student Dashboard | Student |
| 4 | Submit Complaint | Student |
| 5 | My Complaints | Student |
| 6 | Complaint Details & Conversation | Student / Admin (role-specific actions) |
| 7 | Notifications | Both |
| 8 | Admin Dashboard | Admin |
| 9 | Complaint Management | Admin |
| 10 | Analytics & Reports | Admin |
| 11 | Category Management | Admin |
| 12 | Profile & Settings | Both |

Screens 1–2 share one window (split layout). Screens 3–12 live in the main application shell (sidebar + top bar + content area). Visual and interaction specs: `DESIGN.md`.

---

## 8. Confidentiality Requirements (critical)

1. A complaint is confidential if its category is confidential **at creation**; the flag is stored on the complaint.
2. Confidential complaints are excluded, **in SQL**, from admin lists, search, counts, charts, CSV, notification content, and attachment access — unless the requester is an Authorized Admin (or the complaint's own student).
3. Exclusion is enforced in the DAO/service layer through one shared predicate, not by hiding UI elements.
4. A hidden or unauthorized record behaves as **"not found"** (same message as a missing ID) so existence is not leaked.
5. Notification text for confidential complaints is generic: *"There's an update on your private request."*
6. Attachments of confidential complaints are accessible only through `AttachmentService` after an authorization check; stored under UUID names (and encrypted at rest — see ARCHITECTURE §7).
7. The Safety category UI always shows the immediate-danger notice.
8. Logs never contain complaint descriptions or comment text.

---

## 9. Non-Functional Requirements

| Area | Requirement |
|---|---|
| Performance | App cold start < 4 s; list screens render < 500 ms with 10k complaints on a laptop; animation ≥ 50 fps on a mid-range machine |
| Responsiveness | No DB or file I/O on the EDT; every long task shows progress or a skeleton |
| Security | BCrypt, `PreparedStatement` only, role checks in services, upload validation, least-privilege DB user, no hard-coded credentials |
| Privacy | Synthetic data for demos; minimum personal data collected |
| Reliability | Multi-row writes transactional; FK integrity; consistent error handling (typed exceptions → friendly messages) |
| Accessibility | Keyboard-complete; contrast WCAG AA; color never the only signal; accessible names |
| Compatibility | Java 21 on Windows 10/11, macOS, and Linux desktop |
| Maintainability | Layered code (UI → controller → service → DAO), design tokens in one place, documented SQL |
| Observability | Rolling file log (no PII) with error stack traces |

---

## 10. Success Criteria → Acceptance Tests

| # | Criterion | Test (pass condition) |
|---|---|---|
| S1 | Students submit and track own complaints | Student A submits; sees it in My Complaints; Student B cannot open it (service returns "not found") |
| S2 | Admins manage and update status | Admin moves Pending → In Progress → Resolved; student sees each state |
| S3 | History recorded | Each change yields exactly one history row with correct actor and values |
| S4 | Comments and notifications | Admin comment creates an unread notification for the student; mark-read works |
| S5 | Search / filter / pagination / CSV | Status+category filter returns correct rows; CSV matches the filter; page 2 works |
| S6 | Confidential restrictions | Non-authorized admin: confidential row absent from table, search, counts, charts, CSV, notifications, attachments |
| S7 | Referential integrity | Deleting a user/category that has complaints is rejected by the DB |
| S8 | Window + themes | Verified at 1000×680, 1280×800, 1920×1080, scaling 100/125/150%, both themes |
| S9 | SQL evidence | Report includes DDL, DML, SELECT/JOIN/aggregate outputs with screenshots |
| S10 | Security basics | Injection strings in search box (`' OR 1=1 --`) return no extra rows; renamed `.exe` as `.png` rejected |
| S11 | Responsiveness | With the DB artificially delayed 3 s, the window still repaints and shows a skeleton |
| S12 | Invalid transitions | Direct SQL `UPDATE` Pending → Resolved is rejected by the trigger |

---

## 11. Academic Deliverables Mapping

| Rubric item | Where it is satisfied |
|---|---|
| Java Swing GUI | The whole application (`ui` package) |
| JDBC connectivity | DAO layer with `PreparedStatement`, HikariCP pool, JDBC transactions |
| DDL | `database/01_schema.sql` |
| 5–10 rows per table | `database/02_seed.sql` (synthetic) |
| INSERT / UPDATE / DELETE | `database/03_dml_demo.sql` + live app actions |
| PK / FK relationships | ER diagram in `ARCHITECTURE.md` |
| SELECT with WHERE / ORDER BY / joins | `database/04_queries.sql` |
| Aggregates (COUNT, GROUP BY, AVG) | Analytics queries |
| Views / trigger | `database/05_views_triggers.sql` |
| Status update + history | Transactional update demo |
| Student/admin restrictions | Role checks + confidentiality predicate/view |
| Screenshots | MySQL Workbench result grids + Swing UI captures (both themes) |
| Report + 6–8 slide PPT | Built from this PRD, `ARCHITECTURE.md`, and screenshots |

---

## 12. Proposed Schema Additions (need your approval)

The brief fixes seven tables with "suggested fields". These small additions close real gaps and keep the count at seven:

| Table | Addition | Reason |
|---|---|---|
| `users` | `can_view_confidential BOOLEAN` | Makes "only authorized staff" concrete without a third role |
| `complaints` | `is_confidential BOOLEAN` | Snapshot so later category edits don't expose old complaints |
| `complaints` | `student_urgency ENUM` (nullable) | Students indicate urgency without controlling priority |
| `complaints` | `location VARCHAR(100)` (nullable) | Staff need to know where (e.g., "B Wing, Floor 3") |
| `complaint_comments` | `is_internal BOOLEAN` | Admin internal notes vs. student-visible replies |

Also: `complaints.priority` is **nullable** ("Unassigned" until triaged); `complaints.updated_at` uses millisecond precision for optimistic-concurrency checks.

---

## 13. Risks and Open Questions

| # | Risk / Question | Mitigation / Default |
|---|---|---|
| R1 | Swing apps can look dated | FlatLaf + design tokens + custom-painted cards/charts (see `DESIGN.md`) |
| R2 | Two-tier design: every client holds DB credentials | Least-privilege DB user, credentials in an untracked config file, service-layer authorization, DB trigger/views as backstop; documented as a prototype limitation and "future scope: server tier" |
| R3 | UI freezes from DB calls on the EDT | Mandatory `SwingWorker`/executor pattern, reviewed in `.cursorrules` |
| R4 | Confidential leakage via a forgotten code path | One shared predicate + view + automated tests (S6) |
| R5 | File upload abuse / local file exposure | Decode-validate images, size limits, UUID names, strip EXIF by re-encoding, encrypt confidential files |
| R6 | Scope creep from visual ambition | Build in milestone order; polish last |
| Q1 | Who creates the first Authorized Admin? | Seed script + dev password runner |
| Q2 | Can students reopen Resolved complaints? | Default **no**; they comment, admin reopens |
| Q3 | Hostel blocks/rooms as structured data? | Out of scope; free-text `location` |

---

## 14. Milestones

| M | Deliverable | Contents |
|---|---|---|
| M0 | Foundations | Maven project, DB schema + seed, config loading, HikariCP, FlatLaf theme + tokens, theme toggle, window shell |
| M1 | Auth | Welcome/Login/Register, session, role routing, profile basics |
| M2 | Complaint core | Submit (with photos), My Complaints, details, history timeline |
| M3 | Admin core | Queue, filters, transactional status/priority update, comments + internal notes |
| M4 | Notifications | Bell, list, mark read, polling, toasts, generic wording |
| M5 | Analytics + export | KPIs, custom charts, CSV, category management, users & access |
| M6 | Polish | Animations, skeletons, empty/error states, HiDPI + a11y pass, jar packaging |
| M7 | Report | SQL screenshots, UI screenshots (both themes), report, PPT |

---

## 15. Glossary

- **EDT** — Swing's Event Dispatch Thread; all UI work must happen on it, all slow work must not.
- **Authorized Admin** — Admin with `can_view_confidential = TRUE`.
- **Triage** — Admin's first review where priority is set.
- **Confidential complaint** — A complaint whose category is flagged confidential at creation.
- **Internal note** — A comment visible only to admins.
