# Fuexam visible branding replacement design

Date: 2026-06-22

## Goal

Replace all user-visible branding text derived from the old FuOverflow/Fuo brand with `Fuexam` across this repository, while avoiding changes to internal technical identifiers that are not rendered to users.

## Approved scope

### In scope

All text that is actually visible to a user, including:

- User-facing frontend UI in the main app.
- User-facing frontend UI in the admin app when rendered in browser screens.
- Email subjects, email bodies, and email templates.
- Metadata that users or external platforms can visibly surface, such as browser tab titles, manifest app names, OpenGraph/site names, and similar rendered branding.
- Toasts, dialogs, button labels, empty states, banners, footers, navigation labels, page headings, descriptions, and sample/demo text rendered in the app.
- Backend-generated messages only when those messages are intended to be displayed to users.
- Visible domain/email text shown as branding copy in the UI.

### Out of scope

Do not rename non-visible internal identifiers, including:

- Package names
- Java class names
- Imports
- Database names, columns, enums, migrations, internal constants, and internal IDs
- Maven coordinates, module names, and other technical identifiers that are not rendered to users
- Hidden operational config values unless they are display-only text

## Replacement rules

### Brand text

Replace visible variants of the old brand with `Fuexam`, including:

- `FuOverflow` -> `Fuexam`
- Visible `Fuo...` branding variants -> `Fuexam`

### Point/currency naming

Replace visible point branding:

- `FUO Point` -> `Fuexam Point`

### Visible domain/email text

If a domain or email address is shown as branding copy to users, update the displayed brand text.

Do not automatically change the actual working destination, endpoint, or configured sender value if that value is functional infrastructure rather than display text.

## Recommended implementation approach

Use a targeted visible-text sweep.

### Why this approach

This approach best matches the approved scope:

- It minimizes the chance of breaking code by avoiding blind global replacement.
- It keeps changes aligned with what users can actually see.
- It allows ambiguous hits to be reviewed before editing.

### Rejected alternatives

#### Surface-first runtime-only pass

Useful for catching runtime text, but too dependent on manual browsing and can miss less-traveled surfaces.

#### Broad replace then rollback

Fast initially, but too likely to affect internal identifiers and create cleanup work.

## Discovery and inventory strategy

Before editing, inventory all likely visible surfaces and classify each hit.

### Surface groups

1. **Frontend app**
   - Scan the main frontend application for visible text in pages, layouts, components, and metadata.

2. **Admin frontend**
   - Scan the admin frontend for browser-rendered text, labels, headings, navigation, and metadata.

3. **Backend-rendered user-visible content**
   - Scan email templates, notification copy, generated email subjects/bodies, and backend user messages.

4. **Metadata and public-facing assets**
   - Scan manifest names, page titles, descriptions, OpenGraph/site metadata, and similar visible branding strings.

5. **Public/demo rendered text**
   - Scan mock/demo/sample content that renders in the UI.

### Classification model

Each matched file should be assigned a surface type such as:

- `ui`
- `admin-ui`
- `mail`
- `metadata`
- `user-message`

Matches that are only internal implementation details should be excluded.

### Ambiguity handling

If a match mixes visible branding text and internal technical content in one file, only the literal user-visible segment should be changed.

## Edit strategy

Apply changes by surface group rather than all at once:

1. Main user UI
2. Admin UI
3. Mail/templates/notifications
4. Metadata/manifest/browser title
5. User-facing backend messages

### Editing rules

- If the text renders visibly to users, change it.
- If it is internal-only, keep it.
- If it is dynamic or composed, preserve the logic and only replace the visible brand literal.
- Avoid changing functional URLs or email sender configuration unless the occurrence is clearly display-only text.

## Verification strategy

### 1. Search verification

Run repository searches for branding variants after edits, including patterns like:

- `FuOverflow`
- `Fuo`
- `FUO Point`

Then review remaining hits and allow only intentional internal-only leftovers.

### 2. Build verification

Run the smallest useful validation for affected areas:

- Frontend build/type-check for impacted frontend apps if scripts are available.
- Smallest Maven validation command if backend-visible messages/templates were changed.

### 3. Spot-check verification

Manually inspect representative high-visibility surfaces, including:

- App shell/layout/title
- Common/auth pages
- Admin shell
- Email/template strings
- Metadata/manifest visible names

### 4. Final reporting

Report:

- Files changed
- Surface categories covered
- Remaining intentional old-brand occurrences and why they were kept

## Edge-case rules

### API messages

Only rename API text if it is intended to be shown to users.

### Docs and README content

Only treat docs/README content as in scope when that content is rendered publicly in the app/site/demo surface. Repo-only/internal docs are not a priority under this scope.

### URLs and email addresses shown in UI

Change displayed branding text, but do not change actual destinations/configured values unless they are clearly just display copy.

### Images and screenshots

Text baked into images is not part of this text-sweep. If discovered, it should be reported separately.

### Generated or third-party files

Avoid editing generated or third-party files unless the file itself directly renders user-visible branding and there is no better source-of-truth file to change.

## Expected deliverable

A repository update where all user-visible old-brand text is replaced with `Fuexam`, while internal technical identifiers remain unchanged unless they are directly rendered to users.

## Constraints

- Keep the change focused on visible branding only.
- Do not silently change architecture or internal naming conventions.
- Prefer the smallest useful verification commands after implementation.
- Preserve existing behavior except for the approved visible branding updates.
