# ArtShift — Photo Group-Snap Jigsaw Puzzle
## Complete A-to-Z Master Development Prompt for Claude Code / Codex

> Copy this entire document into the project root as `MASTER_PROMPT.md`. Then give the coding agent the start message included at the end of this document.

---

## 1. Role and Working Method

You are the senior product engineer, Flutter game developer, backend engineer, admin-panel developer, UI/UX designer, QA engineer, DevOps engineer, and release engineer for this project.

Build a complete, production-ready mobile photo group-snap jigsaw puzzle game for Android and iOS, plus its web admin panel and remote-content backend. Do not stop after creating a scaffold, mock screens, or TODO comments. Continue phase by phase until the game is playable, remotely manageable, tested, documented, and buildable.

Use the latest stable mutually compatible versions of all frameworks and packages available at implementation time. Do not hard-code secrets. If real cloud credentials are unavailable, implement fully working local/mock adapters, provide `.env.example`, seed data, and exact connection instructions so the real services can be connected later without changing game logic.

Before writing code:

1. Read this entire master prompt.
2. Inspect the existing repository, designs, and assets.
3. Preserve good existing work and do not overwrite unrelated files.
4. Create `docs/IMPLEMENTATION_PLAN.md` with phases, tasks, acceptance criteria, and status checkboxes.
5. Create `docs/DECISIONS.md` and record important architecture decisions.
6. Implement one complete vertical slice first: bootstrap → chapter → level → playable puzzle → completion → saved progress.
7. After every phase, run formatter, static analysis, relevant tests, and a build or smoke test. Fix errors before moving on.
8. Never claim a feature is finished until it works end-to-end.

---

## 2. Product Interpretation from the Reference Video

The reference demonstrates a portrait mobile photo jigsaw/tile-connect puzzle with these core behaviours:

- A full artwork is divided into equal rectangular photo pieces arranged by solved row and column.
- Every piece is present; there is no permanent empty cell and this is not a classic 8-puzzle/15-puzzle.
- Pieces begin scattered/permuted inside the play workspace and may overlap while being dragged.
- The player freely drags any individual piece or already-connected group in two dimensions.
- When two pieces that are true neighbours in the original image are brought close to the correct relative position, they magnetically snap together.
- Snapped pieces form one rigid connected group. Dragging any piece in that group moves the entire group together without breaking it apart.
- A dragged group can snap and merge with another compatible group. Wrong edges never connect.
- Correct groups gradually reconstruct the full artwork even if the growing image is not yet centred.
- Easier levels use fewer pieces; Level 8 in the reference uses a 3×3 arrangement and Level 9 increases to a 4×4 arrangement.
- The top area displays chapter and level information, with settings access.
- The game gives brief positive feedback during progress.
- Solving the puzzle triggers sparkles/confetti, a completed artwork view, a congratulations state, and a Next button.
- A chapter map displays numbered levels as a larger picture mosaic; completed levels reveal parts of the chapter artwork while future levels remain locked.
- Ads and an ad-removal purchase screen are present.

Use this only as behavioural inspiration. Create an original product, original name, original UI system, original icons, original transitions, and original assets. Do not copy the reference game's brand, artwork, labels, layouts pixel-for-pixel, pricing, sound, or proprietary content.

Working product name: **ArtShift: Connect the Picture**. Keep the visible name, package name, colours, and store identifiers configurable.

---

## 3. Recommended Technology

Build this as a monorepo:

```text
artshift/
  apps/
    mobile/              Flutter Android + iOS app
    admin/               Next.js TypeScript admin panel
  packages/
    contracts/           Shared API schemas / generated models
  supabase/
    migrations/          Database schema and RLS policies
    seed/                Development sample content
    functions/           Secure backend/Edge Functions when required
  workers/
    media-pipeline/       Image validation and variant-generation service
  docs/
  scripts/
  .github/workflows/
```

Use:

- Mobile: Flutter, Dart, Material 3 as a foundation with a fully custom visual theme.
- State management: Riverpod.
- Navigation: GoRouter.
- HTTP: Dio or an equally maintained client with interceptors, timeout, retry, and cancellation.
- Data models: immutable typed models with JSON serialization.
- Local progress/cache index: SQLite through a maintained Flutter package.
- Secrets/tokens: platform secure storage.
- Backend database/auth: Supabase Postgres + Auth + Row Level Security.
- Level artwork: Cloudflare R2 or another S3-compatible object store behind a CDN and custom asset domain.
- Admin: Next.js, TypeScript, accessible component primitives, server-side protected admin routes.
- Analytics/crash reporting: abstract interfaces with Firebase Analytics/Crashlytics adapters or equivalent.
- Ads: official Google Mobile Ads Flutter integration behind an `AdsService` abstraction.
- Purchases: official Flutter in-app purchase APIs behind a `PurchaseService` abstraction; validate purchases server-side before granting durable entitlements.

Avoid a full game engine. This is a 2D grid-and-image game and should use Flutter widgets/custom painting. Do not add Flame unless a measured technical need appears.

---

## 4. The Essential Small-App Architecture

The shipped app must not contain the full level-art library.

Bundle only:

- UI vectors/icons;
- one compact app logo and splash treatment;
- one or two lightweight tutorial/fallback images;
- small sound effects, compressed appropriately;
- no production chapter or level artwork.

Remote content rules:

1. Admin uploads one master image for a level or chapter.
2. The media pipeline validates file type, dimensions, colour space, and file size.
3. It removes unsafe metadata, normalizes orientation, and creates optimized variants:
   - thumbnail: approximately 256–384 px on the longest required display edge;
   - puzzle image: approximately 1024–1536 px, based on quality testing;
   - optional store/marketing preview variant.
4. Prefer WebP for broad mobile compatibility. AVIF may be an optional secondary format only after verified support and performance.
5. Store variants with immutable versioned keys, for example:
   `levels/{levelId}/{contentHash}/puzzle.webp`.
6. Publish URLs only after all variants and database records are valid.
7. The mobile app downloads one puzzle image, decodes it once, and draws every tile from that same decoded image using source rectangles. Never create or download separate files for individual tiles.
8. Cache the downloaded original locally using `levelId + assetVersion + sha256` as the cache identity.
9. Prefetch only the next one or two unlocked levels, preferably on Wi-Fi and only when allowed by settings.
10. Enforce an LRU disk-cache budget, default 150 MB and configurable remotely. Never delete the currently active level from cache.
11. Provide a Downloads screen where the player can view cache use, clear downloaded art, and optionally download a chapter for offline play.
12. If offline, previously downloaded levels remain playable. Undownloaded levels show a friendly reconnect state.
13. Include exponential retry, request timeouts, integrity verification, corrupt-file deletion, and a manual Retry button.
14. Do not put object-storage secret keys in the mobile app. Public artwork may use immutable public CDN URLs. Admin uploads and protected operations must use secure server-side credentials or time-limited signed upload URLs.

The app should request a compact bootstrap/content manifest first. It must never list an entire bucket or guess file paths.

---

## 5. Core Group-Snap Jigsaw Engine

### 5.1 Piece and Group Model

Represent a puzzle as:

- `rows` and `columns`;
- `pieces`, one for every solved grid cell;
- `groups`, initially one group per piece;
- current drag/drop move count;
- elapsed active play time;
- level ID, content version, image dimensions, workspace rectangle, and completion-frame rectangle;
- status: loading, preview, scattering, playing, dragging, snapping, paused, solved, error.

Every `PuzzlePiece` must contain:

- stable piece ID and solved index;
- solved row and column;
- source crop rectangle in the full image;
- width and height in logical workspace units;
- current group ID;
- z-order and accessibility label.

Every `PieceGroup` must contain:

- stable group ID;
- a set of member piece IDs;
- one shared translation/transform from solved-space to workspace-space;
- group bounds;
- z-order;
- drag state.

All pieces inside one group must preserve exact solved relative offsets. Moving a connected group changes only the group's shared transform, not the offsets between its members. Use an efficient union-find/disjoint-set structure or an equally reliable group graph for merges, but keep render state explicit and testable.

### 5.2 Initial Scatter/Permutation

There is no mathematical solvability parity requirement because pieces move freely. Create a fair, playable initial layout:

1. Generate solved tile-size geometry from the image and configured rows/columns.
2. Create a list of initial slot centres covering the workspace; a grid permutation with small deterministic jitter is preferred for the launch version.
3. Randomly assign pieces to slots using a saved seed.
4. Reject layouts where too many correct neighbours are already within snap tolerance, where a large solved group is accidentally present, or where any piece is inaccessible/off-screen.
5. Allow controlled overlap for denser levels, but keep a minimum visible area for every independent piece.
6. Save the scatter seed and all current group transforms so the level can resume exactly.

Do not put pieces into a blank-cell sliding permutation. All `rows × columns` pieces must exist and remain freely draggable.

### 5.3 Free Dragging and Z-Order

- The user may touch any visible piece.
- Resolve the touched piece's current group and raise the entire group above other groups.
- Preserve the finger-to-group grab offset so the group does not jump under the finger.
- Move the whole group continuously in X and Y with the pointer.
- Support drag cancellation and multi-touch safety; only one group may be actively dragged in the launch version.
- Keep enough of a group visible that it cannot be lost outside the workspace. If released mostly off-screen, animate it gently back into a recoverable position.
- Count one move on a completed drop that materially changes the group's position. Do not count tiny taps.
- Default launch gameplay has no piece rotation. Keep `allowRotation` as a future data-driven option, off by default.

Dragging must feel direct at 60 fps. Do not rebuild or decode images during pointer movement.

### 5.4 Exact Neighbour and Snap Logic

Two pieces may connect only if their solved coordinates share one true edge:

- left/right neighbours: same solved row and columns differ by exactly one;
- top/bottom neighbours: same solved column and rows differ by exactly one.

For each open boundary edge of the dragged group:

1. Find the piece that belongs on the other side of that edge in solved topology.
2. If that piece is already in the same group, ignore it.
3. Compute the target translation that would place both pieces at their exact solved relative offset.
4. Compare the dragged group's current translation with that target using distance and axis error.
5. Snap only when the error is within the level's configured tolerance and optional release velocity limit.
6. Choose the best candidate with the smallest normalized error when several candidates qualify.
7. Animate the entire dragged group into the exact target transform in approximately 90–160 ms.
8. Merge both member sets and preserve the stationary target group's transform as the new canonical transform.
9. Play a soft click, tiny haptic, border glow, and particle sparkle if enabled.
10. Run a cascade check so the newly merged group can connect to another already-aligned compatible group in the same drop.

Wrong pieces may overlap visually but must never snap. Corner-only contact must not connect. Floating-point error must not accumulate: after every merge, member positions are derived from solved offsets plus one canonical group transform.

Connected groups remain connected and move as one rigid unit. Do not automatically split them during normal play. If a future debug/assist ungroup feature is added, keep it behind an off-by-default feature flag and never trigger it from an ordinary drag.

### 5.5 Tile Rendering

Decode the puzzle image once. Draw every piece from that same decoded bitmap through a performant custom-rendering layer or efficiently positioned clipped widgets. For each piece:

- derive its solved row and column from `solvedIndex`;
- compute the matching source rectangle inside the decoded image;
- apply the current group's transform to its solved-space destination rectangle;
- use identical crop math for the solved preview, individual pieces, connected groups, and final image;
- add a subtle divider/shadow to unconnected outer edges only;
- remove or soften the seam between correctly connected members of the same group;
- clip and hit-test using the actual rendered rectangle;
- keep geometry accurate across image aspect ratios, safe areas, and device pixel ratios.

Do not generate or download separate bitmap files for pieces. Do not allocate a new bitmap every frame or every drag. Precompute source rectangles and solved offsets when grid/image size changes. Dispose decoded image resources safely when leaving a level.

### 5.6 Completion Detection and Finish Sequence

The puzzle is complete when all pieces belong to one valid group and all required neighbour relationships are satisfied. Completion must not depend on the final group already being centred.

When complete:

1. Stop timer and ignore new input.
2. Animate the full connected group into the centred completion-frame rectangle.
3. Fade out all remaining internal seams and shadows.
4. Show a soft glow around the full artwork.
5. Play positive feedback such as “Great!” selected from a localized pool.
6. Trigger tasteful sparkles/confetti and optional success sound/haptic.
7. Save completion locally immediately.
8. Queue cloud progress synchronization without blocking the UI.
9. Open the completion sheet with full artwork, moves, time, best result, earned stars, Next Level, Replay, Share, and Chapter Map actions.

The success animation must remain smooth on mid-range Android devices. Respect Reduce Motion and sound/haptic settings.

---

## 6. Difficulty and Level Progression

Every level is data-driven. Do not hard-code difficulty by screen number.

Recommended first chapter progression, which matches the reference's visible change from Level 8 to Level 9:

| Levels | Piece grid | Default snap tolerance | Notes |
|---|---:|---:|---|
| 1–3 | 3×3 | 22% of smaller piece edge | guided tutorial/easy |
| 4–8 | 3×3 | 17% | easy to medium |
| 9–12 | 4×4 | 16% | medium; exact reference-style increase at Level 9 |
| 13–16 | 4×4 | 13% | medium to hard |
| 17–21 | 5×5 | 12% | hard |
| 22–25 | 5×5 | 9–11% | expert variations |

Admin may override grid size, scatter layout/seed policy, snap tolerance, preview duration, move/time targets, hint availability, rotation, workspace size, and special rules for any level.

Use three-star scoring based on level-specific par moves and par time, but never block progress because a player earned fewer stars. Completing a level unlocks the next level. Completing the required final level unlocks the next chapter.

At first launch, Level 1 is unlocked. Guest progress is local. If the player later signs in, merge local and cloud progress conservatively: completion is never lost, best moves use the minimum, best time uses the minimum non-zero value, and entitlements use server authority.

---

## 7. Chapter Mosaic System

Each chapter contains 25 levels and uses a 5×5 chapter mosaic.

- The chapter has one cover image.
- Each map cell corresponds to one level position.
- A completed level reveals the matching crop from the cover image.
- An unlocked but incomplete level shows its number with the chapter theme.
- A locked level shows a distinct original locked style and number.
- The current level has a clear accessible focus state and a large `Play Level N` button.
- The map animation reveals a newly completed cell after returning from gameplay.
- Chapter progress is shown as `completed / total`.

The mosaic must be generated from one chapter-cover image at runtime using the same crop technique as the puzzle board. Do not store 25 chapter-map tile files.

Support chapters with counts other than 25 in the data model, but make 25 the launch default.

---

## 8. Mobile Screens and Flows

Build and connect all of these screens:

1. Native splash and Flutter launch/loading screen.
2. First-run consent and privacy choices where legally required.
3. Optional three-page onboarding/tutorial.
4. Home screen with Continue, Chapters, Daily Puzzle, Gallery, and Settings.
5. Chapter carousel/list with progress and download state.
6. Chapter detail/mosaic map.
7. Level loading screen with download progress and retry.
8. Puzzle screen.
9. Pause sheet.
10. Hint/preview sheet.
11. Level-complete sheet.
12. Gallery of completed artwork.
13. Daily puzzle screen and history.
14. Achievements screen.
15. Download/cache manager.
16. Remove Ads / Premium screen.
17. Sign-in/profile screen, with guest play as the default.
18. Settings: music, sound, haptics, reduce motion, snap effects, optional edge outlines, language, prefetch over Wi-Fi, privacy, restore purchases, support, terms, and app version.
19. Friendly maintenance, forced-update, offline, empty, and server-error states.

### Puzzle Screen Layout

- Portrait-first, responsive from small phones to tablets.
- Safe-area-aware header with back/pause, chapter name, level number, and settings.
- Optional moves/time row.
- Centred puzzle board with preserved image ratio and maximum usable size.
- Hint/preview and reset controls below or beside the board depending on width.
- Banner-ad safe area only when the player is not entitled to remove ads.
- Never let ads overlap controls, board cells, system navigation, or completion actions.

---

## 9. Visual and Audio Direction

Create an original premium “calm art gallery” identity:

- deep emerald/teal foundation;
- warm cream surfaces;
- restrained gold accent;
- high-contrast readable text;
- softly rounded cards and buttons;
- subtle paper, gallery, or botanical motifs created originally;
- smooth but brief transitions;
- no copied ribbon, wood-tile, or ad-removal artwork from the reference.

Define proper design tokens for colour, typography, spacing, radius, elevation, duration, and easing. Support light/dark theme only if it can remain cohesive; otherwise launch with one polished accessible theme.

All tap targets must be at least 48 logical pixels. Support dynamic text without clipping. Provide semantic labels and keyboard/focus support where relevant. Avoid communicating lock/completion state with colour alone.

If a required UI asset is missing and an image/design generation capability is available, create a new original asset and save it in the correct asset folder. Otherwise create an original SVG, procedural shape, or clearly labelled temporary placeholder. Never use copyrighted game art, scraped images, other games' logos, or unlicensed stock content. Level artwork will be supplied and licensed through the admin panel.

Audio:

- small original or properly licensed move, completion, button, and sparkle sounds;
- independent music and sound toggles;
- audio pauses correctly on lifecycle interruption;
- no sound required to understand game state.

---

## 10. Hints and Player-Friendly Features

Implement:

- Preview: temporarily overlays the full solved image for a few seconds.
- Pair Hint: selects one not-yet-connected true neighbour pair, highlights both pieces/groups, and shows a brief ghost edge/arrow demonstrating how they align without moving them automatically.
- Assist Snap: optional hint action that moves one selected compatible pair close enough for the player to complete the connection; keep it configurable.
- Reset: restores a fresh fair scatter using a new saved seed after confirmation.
- Undo: restore the previous drop snapshot, including group transforms, z-order, and any merge created by that drop. Keep a bounded history.
- Resume: if the app closes mid-level, restore every group membership/transform, z-order, move count, elapsed time, level asset version, and scatter seed.

Hints must always identify pieces that are true solved neighbours. Never auto-connect wrong edges. Keep expensive group/search work off the frame-rendering path.

---

## 11. Remote Content Data Model

Create migrations, typed models, indexes, constraints, seed data, and RLS policies for at least these tables:

### `chapters`

- `id` UUID
- `slug` unique
- `title_key`
- `description_key`
- `sort_order`
- `cover_asset_id`
- `level_count`
- `unlock_rule` JSONB
- `status`: draft, scheduled, published, archived
- `publish_at`
- `min_app_version`
- timestamps

### `levels`

- `id` UUID
- `chapter_id`
- `level_number`
- `title_key`
- `grid_rows`
- `grid_columns`
- `initial_layout_mode`
- `scatter_passes`
- `snap_tolerance_ratio`
- `allow_rotation` default false
- `preview_seconds`
- `par_moves_3_star`, `par_moves_2_star`
- `par_time_3_star_seconds`, `par_time_2_star_seconds`
- `asset_id`
- `special_rules` JSONB
- `is_daily_eligible`
- `status`, `publish_at`, `min_app_version`
- timestamps
- unique `(chapter_id, level_number)`

### `assets`

- `id` UUID
- `kind`
- `original_name`
- `storage_key_original` private/admin only
- `storage_key_puzzle`
- `storage_key_thumbnail`
- `cdn_url_puzzle`
- `cdn_url_thumbnail`
- width, height, bytes, MIME type
- `sha256`
- `version`
- attribution/licence fields
- moderation/validation status
- timestamps

### `profiles`

- user ID
- display name
- locale
- created/updated timestamps

### `level_progress`

- user ID
- level ID
- completed
- stars
- best moves
- best time
- attempts
- last board state only when cloud resume is enabled
- completed/updated timestamps
- unique `(user_id, level_id)`

### `entitlements`

- user ID
- product ID
- platform
- entitlement type
- status
- original transaction reference/token hash
- expiry where applicable
- last validated timestamp

### `remote_config`

- typed key
- JSON value
- environment
- active version
- timestamps

### `daily_puzzles`, `achievements`, `achievement_progress`, `content_localizations`, and `admin_audit_log`

Public/anonymous clients may read only currently published content compatible with their app version. Users may read/write only their own progress through strict RLS. Admin mutations must require an authenticated admin role. Never expose the service-role key to the browser or mobile app.

---

## 12. API and Bootstrap Contract

Provide a versioned API or secure database RPCs with typed contracts. Minimum operations:

- `GET /v1/bootstrap?platform=&appVersion=&locale=`
- `GET /v1/chapters`
- `GET /v1/chapters/{id}/levels`
- `GET /v1/levels/{id}`
- `POST /v1/progress/sync`
- `GET /v1/daily`
- `GET /v1/config`
- `POST /v1/purchases/validate`
- protected admin endpoints for uploads, publishing, ordering, and config.

The bootstrap response should contain only what launch needs: minimum/suggested app version, maintenance state, active content revision, ad switches, cache policy, available chapters summary, current daily puzzle, and signed-in entitlement summary.

Use ETag or content revision support. Client must keep the last valid manifest and use it during transient outages. Validate every response. Reject malformed grid sizes, missing hashes, duplicate level numbers, and incompatible assets safely.

Example level manifest shape:

```json
{
  "id": "uuid",
  "chapterId": "uuid",
  "levelNumber": 9,
  "grid": { "rows": 4, "columns": 4 },
  "initialLayoutMode": "permuted_grid_with_jitter",
  "scatterPasses": 3,
  "snapToleranceRatio": 0.16,
  "allowRotation": false,
  "previewSeconds": 2,
  "asset": {
    "version": 3,
    "puzzleUrl": "https://assets.example.com/levels/.../puzzle.webp",
    "thumbnailUrl": "https://assets.example.com/levels/.../thumb.webp",
    "sha256": "hex-value",
    "bytes": 245678,
    "width": 1200,
    "height": 1200
  },
  "rules": {},
  "publishedAt": "ISO-8601"
}
```

---

## 13. Admin Panel

Create a complete responsive admin panel with:

- secure admin login;
- dashboard metrics: chapters, published levels, drafts, active players if analytics is connected, downloads/errors;
- chapter list/create/edit/duplicate/archive;
- drag-and-drop chapter ordering;
- chapter cover upload and crop preview;
- level list/grid view;
- create/edit/duplicate/archive levels;
- bulk upload multiple level images;
- drag-and-drop level ordering;
- grid-size and difficulty configuration;
- automatic image validation and processing status;
- preview exactly how crops will appear on 3×3, 4×4, and 5×5 boards;
- playable free-drag/group-snap preview in the browser using the same neighbour and tolerance rules as mobile;
- draft, schedule, publish, unpublish, archive workflow;
- version history and safe rollback of metadata;
- localization editor;
- daily-puzzle scheduling;
- remote-config editor with typed validation;
- minimum-version/maintenance controls with confirmation;
- ads and premium product-ID configuration without exposing secrets;
- audit log containing actor, action, target, previous value, new value, and timestamp;
- role separation: owner, editor, viewer;
- clear destructive-action confirmation.

The admin browser must never directly receive permanent R2 credentials. Use signed uploads or a trusted server route. Validate and authorize again on the server.

---

## 14. Monetization, Consent, and Fairness

Implement monetization behind feature flags so development works with ads disabled.

### Ads

- Adaptive banner on chapter/home areas and, if UX testing approves, below the puzzle controls.
- Interstitial only after a completed level, never during puzzle movement or before first meaningful play.
- Default frequency: every three completed levels with at least a two-minute cooldown; remotely configurable.
- Rewarded ad may grant one optional preview/hint or another non-essential convenience.
- No forced rewarded ad to complete a normal level.
- Use test ad IDs in debug/staging.
- Handle failed/no-fill ads without blocking gameplay.
- Integrate legally required consent and privacy controls.

### Purchases

Support products through configuration:

- one-time `remove_ads_forever`;
- optional monthly/yearly `artshift_premium` subscription.

Premium may include no ads, unlimited previews/undo, bonus chapters, and offline chapter download. Core sequential gameplay must remain usable for free.

Implement purchase, pending state, cancellation, restore, expiry, refund/revocation handling, and server validation. The server entitlement is authoritative. Never grant permanent entitlement only from an unverified client callback.

If the final store audience includes children, explicitly reconfigure ads, analytics, consent, data collection, and store declarations to meet applicable child-directed policies before release. Do not silently label the product as child-directed.

---

## 15. Analytics and Diagnostics

Define a provider-independent analytics interface and document every event. Include:

- app_open, session_start;
- chapter_view, level_start, level_resume;
- group_drag_start, group_drop, group_snap, invalid_drop sampled rather than spammed;
- hint_used, preview_used, reset_used;
- level_complete with level ID, grid size, moves, time, attempts, stars;
- level_abandon;
- asset_download_start/success/failure and cache_hit;
- ad_request/impression/failure;
- paywall_view, purchase_start/success/failure/restore;
- sign_in and progress_sync result.

Never send the full artwork URL, purchase receipt, token, exact board state, email, or other unnecessary personal data as analytics parameters. Add crash reporting with release/environment tags and scrub secrets.

---

## 16. Security and Reliability

- Use HTTPS only.
- Store environment values outside source control.
- Public mobile configuration may include public project URL and publishable/anonymous key only; RLS is mandatory.
- Store sensitive tokens in platform secure storage.
- Rate-limit sensitive APIs.
- Validate MIME type from bytes, not extension alone.
- Enforce upload size and dimension limits.
- Sanitize filenames and never execute uploaded content.
- Use allowlists for accepted image formats.
- Add database constraints in addition to UI validation.
- Protect admin routes server-side, not only with hidden buttons.
- Log admin changes but never log secrets, raw receipts, or tokens.
- Implement timeout, retry with jitter, cancellation, and circuit-friendly error behaviour.
- Make sync idempotent.
- Provide safe database backup/migration and rollback documentation.

---

## 17. Performance and App-Size Requirements

Targets:

- Smooth 60 fps tile movement on representative mid-range Android hardware.
- No image decode or file I/O inside per-frame paint logic.
- Puzzle starts quickly from cache and shows truthful progress on first download.
- Memory remains stable after opening and closing many levels.
- No production level art in the binary.
- Use system font or a minimal subsetted font family.
- Prefer vector icons and procedural decoration.
- Remove unused packages and assets.
- Build Android as an App Bundle for release and inspect actual download/install size.
- Use release/profile builds for performance and size measurement, never debug build numbers.
- Add a documented size budget and record results in `docs/PERFORMANCE.md`.

Use isolates for CPU-heavy hashing/image metadata work where profiling shows UI impact. Predecode only the active level and a tightly bounded preview. Do not retain decoded full images for every cached level.

---

## 18. Local Persistence and Sync

Persist locally:

- installation/player ID for guest mode;
- settings and consent state;
- unlocked/completed levels and best results;
- active puzzle resume state;
- cache index and asset versions;
- pending analytics/sync operations with a safe bounded queue;
- entitlement snapshot with last validation time, while respecting server authority.

Use schema versions and migrations. Handle app update, database migration, corrupted cache, deleted remote content, content-version change, and logout without losing valid completed progress.

When an active level's asset version changes, allow an already-started puzzle to finish with its cached version if safe, then migrate to the new version on replay. Never mix tile crops from two asset versions.

---

## 19. Testing Requirements

Create real automated tests, not just a testing checklist.

### Unit tests

- scatter contains all pieces, keeps them recoverable, and does not start substantially solved;
- solved-neighbour topology for horizontal and vertical edges;
- correct pair/group snaps inside tolerance;
- wrong edge, diagonal contact, and outside-tolerance drops never snap;
- connected group retains exact internal offsets while dragging;
- two groups merge without floating-point drift;
- cascade merge behaviour;
- connected groups do not split during normal play;
- completion detection when all pieces form one valid group;
- crop/source-rectangle math across image and board aspect ratios;
- scoring and unlock rules;
- local/cloud progress merge;
- cache eviction and integrity mismatch;
- manifest decoding and validation;
- purchase entitlement state transitions.

### Widget/UI tests

- loading, offline, error, playing, paused, and solved states;
- correct board cell count;
- free 2D dragging, group z-order, group movement, and magnetic snap animation;
- dynamic text and small-screen overflow;
- ads hidden for entitlement;
- locked/unlocked/revealed chapter mosaic states;
- accessibility labels.

### Integration tests

- new guest completes Level 1 and unlocks Level 2;
- app is killed mid-level and resumes correctly;
- level downloads once and then opens offline;
- corrupt cache is detected and recovered;
- sign-in merges progress;
- ad failure does not block completion;
- purchase restore removes ads;
- admin uploads, publishes, and mobile receives a new level.

Also test Android and iOS lifecycle, phone/tablet layouts, airplane mode, slow connection, CDN 404, API 500, clock changes, app upgrade, and low-storage conditions.

---

## 20. Environments, CI/CD, and Release

Create dev, staging, and production environments with separate IDs, URLs, database projects, buckets, ad IDs, purchase IDs, and analytics destinations. Use Flutter flavors and corresponding admin environment configuration.

CI must run:

- formatting check;
- static analysis/lint;
- unit and widget tests;
- admin TypeScript/lint/tests/build;
- database migration validation;
- secret scan and dependency audit where feasible;
- Android build on pull requests or release branches;
- iOS build/signing workflow documentation, with automated build when credentials are configured.

Provide:

- `README.md` with quick start;
- `.env.example` files;
- local development and seed commands;
- Supabase setup and migration instructions;
- R2 bucket, CORS, custom-domain and cache-header setup;
- AdMob and store purchase setup;
- Android signing and Play Console AAB instructions;
- iOS bundle ID, certificates, App Store Connect and TestFlight instructions;
- privacy-policy data inventory;
- production launch checklist;
- rollback and incident notes.

Do not commit keys, service accounts, signing files, receipts, or real user data.

---

## 21. Seed Content

Create development seed data for:

- one chapter with 25 levels;
- difficulty progression from Section 6;
- a second locked sample chapter;
- remote-config defaults;
- daily puzzle sample;
- achievements;
- one mock premium entitlement test account.

Use clearly original generated, procedural, or placeholder artwork. Do not embed the reference video's artwork. Make it easy to replace seed assets through admin upload.

---

## 22. Required Delivery Artifacts

The final repository must contain:

1. Complete Flutter source for Android and iOS.
2. Complete Next.js admin panel.
3. Database migrations, RLS policies, indexes, seeds, and typed schema.
4. Media upload/processing pipeline.
5. Remote manifest/bootstrap implementation.
6. Working puzzle engine and renderer.
7. Local persistence and cloud sync.
8. Ads, consent, purchases, and restore flows behind configuration.
9. Automated tests.
10. CI configuration.
11. Original UI assets and design tokens.
12. Documentation and environment templates.
13. Android release AAB or exact reproducible build command if signing credentials are not available.
14. iOS build verification or exact reproducible Xcode/TestFlight steps when signing credentials are unavailable.
15. Admin and mobile demo credentials/data for local development only.

---

## 23. Definition of Done

The project is done only when all of the following are true:

- A clean checkout can be configured from the README.
- Mobile app launches without a cloud account using seed/mock mode.
- The player can open a chapter, download a remote level image, freely drag pieces, connect correct neighbouring edges, move connected groups together, solve the puzzle, see the success flow, and unlock the next level.
- Level 8 can use 3×3 and Level 9 can switch to 4×4 through data only.
- There is no blank cell or classic sliding-puzzle movement anywhere in the core game.
- Correct neighbouring pieces snap magnetically; wrong edges never snap.
- Once connected, every member moves together as one group and does not break apart during normal play.
- The same downloaded source image renders every tile correctly; individual tile images are not downloaded.
- Completed chapter cells reveal the correct portions of one chapter image.
- Progress survives restart and can sync after sign-in.
- Offline cached play works.
- Admin can upload, validate, preview, publish, reorder, and archive levels.
- A newly published level becomes available after content refresh without shipping a new app.
- Ads never interrupt active moves and premium entitlement removes them.
- Purchase restore and server validation work in sandbox/test environments.
- Static analysis and automated tests pass.
- Android release build succeeds; iOS project is buildable with documented signing steps.
- No secrets or unlicensed reference assets are committed.
- There are no critical TODOs, fake buttons, dead routes, or placeholder business logic in the release path.

---

## 24. Implementation Phases

Execute in this order:

### Phase 0 — Discovery and plan
Inspect repo, confirm toolchains, create architecture/implementation docs, establish environments and monorepo.

### Phase 1 — Vertical slice
Mock manifest, one remote/local-development image, chapter screen, playable 3×3 free-drag/group-snap engine, group movement, success flow, local progress, tests.

### Phase 2 — Production puzzle system
4×4/5×5, efficient single-image renderer, exact neighbour topology, magnetic group merge, fair scatter, resume, pair hint, scoring, polish, accessibility.

### Phase 3 — Backend and content delivery
Supabase schema/RLS, bootstrap/API, R2/CDN adapter, asset versions, sync, offline cache.

### Phase 4 — Admin panel
Auth/roles, chapter/level CRUD, upload pipeline, crop/play preview, publish workflow, localization, audit.

### Phase 5 — Full product screens
Home, gallery, daily, achievements, profile, downloads, settings, errors, tutorial.

### Phase 6 — Monetization and compliance
Consent, ads, purchases, server validation, restore, privacy/data inventory.

### Phase 7 — QA, performance, and release
Automated/integration testing, profiling, app-size analysis, build flavours, CI, Android/iOS release documentation.

At the end of every phase, update `docs/IMPLEMENTATION_PLAN.md` with completed work, verification performed, known limitations, and the next exact task.

---

## 25. Start Message to Send the Coding Agent

Paste this after adding the master prompt to the project:

```text
Read MASTER_PROMPT.md completely before editing anything. Important: this is a free-drag group-snap jigsaw game, NOT a blank-cell sliding puzzle. Correct neighbouring photo pieces must magnetically connect; every connected group must move together as one rigid unit and must not break during normal play. Inspect the existing repository, designs, and assets, then create docs/IMPLEMENTATION_PLAN.md and docs/DECISIONS.md. Begin Phase 0 and continue autonomously into Phase 1. Build a complete working vertical slice—not mock-only screens. Reuse good existing work, create original missing SVG/procedural assets, never copy reference-game assets, and keep all production level artwork remotely delivered. Run formatting, analysis, tests, and a smoke build after each phase. If cloud credentials are missing, use clean interfaces plus a working mock/local adapter and .env.example; do not stop. Report changed files, commands run, test/build results, and the next phase when Phase 1 is fully working.
```
