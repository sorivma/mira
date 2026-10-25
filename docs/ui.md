# Workspace UI

The Jira-like workspace uses the Minecraft font and native text editing, a small
fluent layout module and an embedded CommonMark parser. Its authoritative domain
is in core, with world-local persistence and real-time server synchronization.
Workspaces contain multiple projects and configurable Owner/Editor/Viewer roles.
Saved task fields and description blocks merge independently; typing stays local.
Settings provide membership, projects, sprints, custom issue types, workflow graph
and scheme editing, board mappings, archives and activity. See [backend.md](backend.md)
for the command/storage contracts and multiplayer acceptance procedure.

## Native visual design

The workspace is an inventory-style window over the dimmed world, with compact
creative-inventory tabs for Board, Backlog, and Issues. It is capped at 920 by
520 scaled pixels, preserving Minecraft's configured GUI scale. With NEI it
aligns left and reserves 132 scaled pixels at the right and space below for
NEI controls. Without NEI it is centered. Its empty client-only container
supports NEI's normal GUI integration without changing server inventory slots.
No Minecraft textures are copied into Mira's jar. `MinecraftSkin` binds these
resources directly from the active resource pack:

| Minecraft resource | Use |
| --- | --- |
| `textures/gui/demo_background.png` | Outer frame and issue surfaces |
| `textures/gui/widgets.png` | Normal, selected and hovered buttons |
| `textures/gui/container/creative_inventory/tabs.png` | Navigation tabs and scrollbar thumbs |
| `textures/gui/container/inventory.png` | Recessed wells and item slots |
| Minecraft item rendering | Tab icons and work-specific sample issue icons |

Nine-slice drawing preserves the texture corners while resizing a panel.
The vanilla palette is inventory silver `#C6C6C6`, recessed gray `#8B8B8B`,
body ink `#404040`, secondary ink `#505050`, white edges and the vanilla yellow
button highlight `#FFFFA0`. Secondary ink was darkened from the initial design
plan for readability. Status hues accompany explicit status labels; they do not
replace the labels. All text uses the active Minecraft font renderer.

The distinctive element is the work-related item icon: a furnace for steam,
a bucket for the water intake, redstone for cable routes. Icons are sample
presentation mappings in `IssueWidgets`, not real attached inventory items.
Lists use flat rows; board tasks have raised inventory panels. Cards reserve
three title lines with ellipsis, keep item/key/type/priority together, and place
assignee and completion counts above a small progress strip. Details and forms
share the outer frame. Widgets and text form a continuous description with one
left edge. Default widget headings and individual editor frames/actions are
absent. A gutter handle appears on hover/selection, insertion gaps reveal + on
hover, and one shared toolbar controls the selected block. The selected text
editor grows with its content and uses the document background and ink.

Project, filters and issue properties use anchored choice menus with direct
selection. Vanilla tooltips expose full issue summaries, clipped text and
control labels. Buttons use the normal Minecraft click sound. Scroll thumbs
support dragging and track clicks as well as the wheel. Back/Edit and
the full issue form's Cancel/Save actions stay outside their scroll areas.

Assignees are consistently shown as a small skin face directly beside their
nickname, including issue details, board cards, rows, selectors and selector
options. Unassigned/Everyone use a neutral silhouette. `PlayerFaces` reuses the
local/loaded player skin and otherwise uses the standard Steve fallback; it
does not request remote profiles while drawing. UVs account for legacy and
modern skin dimensions, with the hat overlay. The picker includes the local
nickname, players from the game's online roster, sample assignees, and the
current assignee. Demo records using `You` are resolved to the local nickname
when the workspace session starts.

Task details use a reading/discussion column with a separate properties column
at 520 scaled pixels of available detail width. Narrow details place status,
priority, and the inline assignee field near the title; additional properties
and activity are collapsed. Comments show newest first, with the composer before
the history. The empty description has a direct Edit entry point.

Creation starts with a focused title, a Markdown description, and an assignee
field with a Me action. More fields reveals type, priority and sprint. Edit starts
with those fields expanded. Form actions and validation stay outside the scrolling
body, and title changes clear the empty-title error. Create + next retains
planning choices while resetting text and focusing the new title.

## Workspace settings

Settings keep the resource-pack inventory frame and Minecraft font. Lapis marks
the selected section; pale stone highlights selectable records, moss signals a
ready publication review, and redstone marks errors. Text labels always carry
the meaning alongside color. At 510 scaled pixels of settings width, a persistent
rail groups Workspace, Planning, Workflow and History. Smaller windows use a
section picker and stack form controls. The rail and page scroll independently.
Forms cap their reading width and keep name/column save actions below the scroll.

New and Edit are separate states. New focuses the name; Cancel returns to the
previous item. Project keys are only editable during creation. Save name and Save
column indicate precisely what is being saved; workflow graph edits, mappings
and publication have their own actions. Empty selectors are inactive controls.
Owner-only pages explain access, while project and board settings remain readable
to Viewers. Archived items must be restored before editing. Saving keeps the page
visible and suspends its form interactions until the server replies. Settings
conflicts expose Keep my edits and Reload saved without dropping the local form.

People & access pairs player faces with roles, explains Viewer/Editor capabilities,
and provides an inline ownership-transfer review. Sprints show completed/total
issues and an explicit destination for unfinished work. Board columns show each
status's current column before reassignment. Workflows have a graph at wider sizes,
status/transition lists at every size, an anchored inspector, required-field
toggles, and From/To controls that also work without dragging. Schemes present a
default workflow plus issue-type exceptions. Publication lists replacement statuses
for affected issues before applying the change. Archives have type/search filters;
activity groups changes by date with search and author filtering.

Escape cancels an ownership review or New form before leaving settings. Ctrl+F
focuses search in Archives/Activity. Issue-creation and document shortcuts stay
within the issue views. The normal field Tab traversal and choice-menu arrow keys
remain available. UI animation is limited to existing native interaction feedback.

## Ownership

All paths below are relative to
`platform-forge-1.7.10/src/main/java/mira/platform/forge1710/client/`.

| Module | Responsibility |
| --- | --- |
| `MiraClient` | Key registration and one workspace instance per client run |
| `workspace/WorkspaceScreen` | Minecraft lifecycle and input forwarding |
| `workspace/WorkspaceShell` | Header, navigation, filters, responsive composition |
| `workspace/BoardView` | Status columns, compact column tabs, column scroll positions |
| `workspace/BacklogView` | Issues grouped by sprint/backlog |
| `workspace/IssueListView` | Flat filtered issue list |
| `workspace/IssueDetailsView` | Issue presentation, inline text edits, comments, activity |
| `workspace/IssueEditorView` | Form controls and editor scroll/focus |
| `workspace/IssueWidgets` | Shared issue cards, rows, wrapped paragraphs |
| `workspace/WorkspaceSession` | Navigation, filtering, selection and detached draft lifecycle |
| `workspace/RemoteWorkspace` | Stable server projections, versioned mutations and conflict handling |
| `workspace/JiraClient` | Client-thread snapshot delivery and acknowledgment/retry handling |
| `workspace/WorkspaceData` | Detached presentation records; no authority or persistence |
| `workspace/WorkspaceSettings` | Local administration forms and command construction |
| `workspace/WorkspaceSettingsView` | Membership/project/sprint/workflow/scheme/board/archive views |
| `workspace/WorkflowSettingsView` | Graph inspector, workflow assignments and publication review |
| `workspace/SettingsLayout` | Shared settings sections, copy and responsive control composition |
| `workspace/IssueDraft` | Detached create/edit values and validation message |
| `workspace/DocumentDraft` | Detached ordered document, selection, insertion, paragraph splitting and reordering |
| `workspace/DocumentView` | Continuous block composition, insertion gaps, drag handles and shared toolbar |
| `workspace/TaskTemplates` | Append common checklists/materials to a draft |
| `workspace/PinnedTaskHud` | Pinned task title/status/progress while playing |
| `widgets/WidgetCatalog` | Typed widget registration, creation and rendering |
| `widgets/WidgetContext` | Inputs, observations, and undo-aware widget mutations |
| `InventoryClient` | Periodic inventory requests and current/fallback observations |
| `NeiBridge` | Optional NEI ghost item drops and input coordination |
| `workspace/WorkspacePlayers` | Game roster options and inline assignee/Me controls |
| `workspace/DemoWorkspace` (test sources) | Disposable UI interaction fixtures |
| `ui/Ui` | Layout, clipping, painting, hit testing, scroll and focus dispatch |
| `ui/MinecraftSkin` | Minecraft texture slices, item rendering and vanilla tooltips |
| `ui/ChoiceMenu` | Anchored selection menu, scrolling and arrow-key selection |
| `ui/DragAndDrop` | Click/drag threshold, accepting targets, previews and edge scroll state |
| `ui/TextInput` | Native caret/selection/clipboard behavior bound to state |
| `ui/TextArea` | Wrapped multiline editing, selection and clipboard |
| `ui/Markdown` | CommonMark/GFM syntax translated to native fluent nodes |
| `ui/MarkdownEditor` | Shared source/preview editor and selection-aware formatting tools |
| `ui/RichText` | Styled word wrapping, hard breaks, code whitespace and alignment |
| `ui/MarkdownImages` | Bounded background image reads and render-thread texture lifecycle |
| `ui/MarkdownLinks` | Native external-link confirmation and local-destination callback |
| `ui/PlayerFaces` | Loaded player skin lookup and neutral/default-face fallback |

The session and draft classes use ordinary Java values and can be exercised
without starting Minecraft. Feature views do not calculate mouse hitboxes or
call OpenGL. The UI module knows nothing about projects, issues, or workflows.
The domain, body model and matching/allocation rules live in core. Inventory
collection/packets remain in the platform `inventory/` package; Jira transport and
world-file storage are in the common `jira/` package. The client projection never
owns task, permission or workflow rules. The shared `Ui.graph` primitive owns graph
layout, arrows, clipping, selection and drag targets; feature views supply values
and actions.

## Fluent composition

Views return a tree; the screen renders it. Rows and columns divide remaining
space between children without explicit dimensions. Fixed sizes reserve space
on their corresponding axis; padding and gaps belong to the parent.

`Ui.stack` and `Node.fitHeight()` size composed form groups from their fixed-height
children. Call `fitHeight()` after adding children. `Ui.labelled` keeps labels above
controls; `navigation` and `choiceRow` distinguish destinations and records from
command buttons. `actionButton` provides an inactive state without pointer targets.
`Node.interactive(false)` suspends a subtree's form actions while retaining scrolling.

```java
return column(7).add(
    heading("Planning"),
    row(6).height(33).add(
        IssueWidgets.property("Type",
            select("Type", issue.type, session.types(), session::changeType)),
        IssueWidgets.property("Priority",
            select("Priority", issue.priority, WorkspaceData.PRIORITIES, session::changePriority))));
```

Keep scroll state on its owning view, not on the transient tree:

```java
private final Ui.Scroll scroll = new Ui.Scroll();

// All children of a scrolling column have an explicit height.
Node list = column(5).scroll(scroll);
list.add(IssueWidgets.issueRow(issue, () -> session.select(issue)));
```

The same clipped rectangles drive painting and pointer actions. Nested scroll
areas take wheel input under the pointer. Hidden text inputs lose focus;
Tab/Shift+Tab traverse the inputs currently visible in the viewport. Pointer
targets are discarded after clicks because an action may replace the view.

Board gestures use the same clipped bounds. A card declares its payload, and
the column declares which payload type it accepts; feature views never handle
mouse coordinates or draw their own drag preview:

```java
IssueWidgets.card(issue, () -> session.select(issue))
    .draggable(issue)
    .dropBefore(Issue.class, moved -> session.moveIssueBefore(moved, issue));

column(4).inset()
    .dropTarget(Issue.class, moved -> session.moveIssue(moved, status));
```

`DragAndDrop` delays the card's click action until release. At five scaled
pixels of movement the gesture becomes a drag. The source dims, the card
follows the pointer, accepting columns/tabs get a green outline, and another
card gets an insertion line. Nested card targets win over column targets.
The preview does not register hitboxes or targets. The session changes only
after releasing onto a target; a release elsewhere or Escape cancels it.

To add another workspace feature, add its view beside the existing feature
views and compose it in `WorkspaceShell`. Add navigation/actions to the session
only when they represent actual interactions. Extend `ui/` only for reusable
rendering/input behavior needed by the feature.

## Behavior and controls

- **J** opens the workspace from a world; configure the binding in Controls.
- **Ctrl+F** focuses search. Search matches issue key, title, and description.
- **C** opens creation when no text input is focused. **Ctrl+N** also opens it
  from search. An already-open draft is never replaced by these shortcuts.
- **+** in a board column creates a task in its applicable workflow's initial
  status. A fresh draft also takes concrete
  assignee/sprint filters as defaults. Resuming a draft preserves its values.
- Project, assignee, sprint, and issue fields open choice menus. Use the mouse
  or Up/Down then Enter. Escape or clicking outside dismisses the menu.
- **Reset** clears search and both filters without changing the project.
- **Drag cards** between columns to change status. Drop before another card
  at its insertion line to change order, or onto the column background/header
  to append to the column. In the compact layout, drop onto the desired status
  tab; that status opens after the move. No horizontal-board replacement is used.
- Hold a dragged card near a column's upper/lower edge to scroll it. A simple
  click still opens details. Escape cancels a drag before navigating away.
- **Enter** saves from the issue title or posts a focused comment. Inside the
  document it splits a text paragraph at the caret, replacing any selection,
  or adds a paragraph after a selected widget. **Shift+Enter** inserts a literal
  line break within Markdown source (including lists, tables and code fences).
- **Ctrl+Enter** or Create + next creates and opens the next blank draft, keeping
  status, assignee, type, priority and sprint. In Edit, Ctrl+Enter saves the edit.
- **Me** assigns the issue/draft to the local nickname. Head and name are one
  field, not a separate decorative portrait.
- **Escape** leaves the editor, then issue details, then the workspace.
- Closing/reopening keeps navigation, filters, drafts, and scroll state for the
  running client. Each project has its own unfinished create draft.
- Cancelling Edit discards that edit. Cancelling Create retains its draft for
  the next Create action in that project.
- Navigation uses compact top tabs at every width. Narrow boards show one
  configurable column with tabs/counts; all columns appear when enough width
  is available. At a window width of 860 scaled pixels,
  selecting an issue opens a side inspector with an Open action for expansion.
  Filters are hidden in the full issue view to free space for content.
- **Ctrl+Enter** saves the document in issue details. In the full issue form it
  retains the form's Save / Create + next behavior.
- Click the title to open the detached issue editor, or any document text/widget to select and
  edit that block. Assignee pickers and
  long choice lists have search; arrows and Enter still select an option.
- **+** gaps insert Text, Checklist, Materials, Location or Blocked by anywhere,
  including before the opening paragraph. The shared **Insert block** control
  inserts after the selection. **/** in an empty text block opens a searchable
  block picker and replaces that empty paragraph; **/** outside text input opens
  the same picker. Escape closes the picker before cancelling the document.
- Drag a **::** gutter handle to an insertion line, or use **^ / v** in the shared
  toolbar. Short handle clicks select instead of dragging. Escape/releasing
  outside an insertion target cancels a drag; edge scrolling remains available.
- Switching selected blocks keeps all draft edits. The shared toolbar provides
  Markdown formatting/preview for text and a title field for widgets. Only the
  selected block reveals its editor; ordinary widget titles are hidden while
  reading. Custom widget headings remain document content.
- **Save / Ctrl+Enter** in details commits the entire description as one undo
  action. **Cancel / Escape** discards text, settings, additions, removals and
  order changes, keeping the issue open. Navigation or opening the full issue
  editor also discards the unsaved document. The full form uses its existing
  Save/Cancel controls for the entire issue. Checkbox actions outside editing
  remain immediate/undoable; during editing they belong to the document draft.
- Templates append sections and preserve existing body content. Choosing Blank
  does not delete sections. Create + next starts a fresh body.
- **Undo / Ctrl+Z** reverses your latest eligible saved task edit, body action,
  comment or board move if its fields have not subsequently changed. It is
  disabled while a draft is open and does not replace text-field Undo. The
  server retains the original action and records the inverse in activity.
- **Pin** shows a compact task HUD when no screen is open. J opens its details.
  Pins and the workspace reset when disconnecting.

## Extensible task bodies and carried materials

Text and widgets share one ordered document. Legacy `Issue.description` text is
adapted into the first Note block on opening the editor, so even that original
paragraph can move between widgets. Saving the document stores every block in
the body and clears the legacy string; Undo restores both. Note models appear
as untitled **Text** blocks. Any type may occur more than once. Insert, rename,
move and remove use the shared toolbar and the same model in details/full forms.
Checklist steps have independent manual checks; edit the checklist to add a step
with Enter or +. Counts on cards and the pinned HUD reflect checkable rows.

Materials use an item/name line, a count/source line and a progress strip.
Editing reveals labelled quantity, completion, matching and inventory controls.
Location edits expose signed integer dimension/X/Y/Z fields and Use target;
invalid numeric text keeps the previous numeric value. During document editing,
all widget actions and item drops change the detached document. A whole-document
item drop starts editing and inserts Materials after the selection (or adds to
the selected Materials block). Existing Materials/alternative drop targets keep
their own actions. Save commits the complete document, Cancel discards it.

### Markdown description and text blocks

Every Text block uses the same source/preview editor and shared formatting tools,
with a 16,000-character limit per text block. Bold/italic/link tools wrap the
selection. Format offers headings, lists, task lists, quotes, fenced code,
tables, images, strikethrough and horizontal rules. Enter creates a new paragraph;
Shift+Enter keeps a newline in the current source. Source whitespace is preserved on every save, including
indented code and trailing spaces for hard line breaks.

Rendering supports CommonMark headings, paragraphs, nested ordered/unordered
lists, block quotes, emphasis/strong emphasis, inline/fenced/indented code,
soft/hard breaks, thematic breaks, escaped punctuation, entities, inline and
reference links/images, plus GFM tables, strikethrough, task-list markers and
automatic URL/email links. Markdown task lists are document text; the Checklist
widget provides interactive task progress. Code has a Copy action. Tables retain
column alignment at wide widths and stack labelled cells at narrow widths.

Links to issue keys such as `[intake](BASE-1)` use workspace navigation; external
HTTP/HTTPS/mail links use Minecraft's confirmation dialog. `#heading-slug` links
scroll to headings, including those outside the current viewport. Images load
asynchronously from HTTP(S), or use `namespace:path` resource-pack textures.
Remote images are limited to 4 MiB/8 million decoded pixels, scaled to at most
1024 pixels per side, displayed at up to 160 scaled pixels tall, and cached in
32 texture slots. Unavailable images retain their alt text and destination.
Raw HTML appears as literal source; this native renderer does not execute HTML
or scripts. Footnotes, math, diagrams and browser HTML/CSS are not extensions of
this Markdown implementation.

### Material inventory and item drops

Drop a NEI item onto the body to create/add to Materials, onto a Materials
section to add a requirement, or onto an existing requirement to add an accepted
alternative. These are sample/ghost drops and never give or consume items.
The Held item button also adds a requirement using the held stack's quantity.

Material settings in Edit include quantity, automatic/manual completion,
matching (exact tag data, same item/variant, any variant, or selected ore group),
and main inventory versus all carried storage. Exact matching uses a canonical
NBT fingerprint. Alternatives share the same matching policy. Repeating the same
single-item requirement increments its quantity; alternatives can be reset.
Manual checks remain set independently of inventory. Automatic checks follow
the current observation and can become incomplete after moving items away.
The task itself never changes status automatically.

The observed inventory belongs to the viewer, not the assignee. A shared
maximum-flow allocation across all automatic material rows prevents counting
one item multiple times and can reroute broad matches to satisfy narrower ones.
Allocation is independent between tasks. Main inventory includes armor and the
cursor stack; bag contents and optional equipped storage are counted only with
All carried. Snapshots refresh while the UI or pinned tracker is active.

Explicit adapters are implemented for Forestry backpacks, Adventure Backpack,
and the GTNH Backpack mod (including server-stored, personal, and ender-backed
inventories), plus equipped Baubles/Adventure storage. These integrations have
not been run in a pack. Server-only contents require Mira on the server. Failed
reads or scan limits show an availability warning; unsupported storage mods are
not assumed readable. Stored recipes and item filters are excluded.

To implement another body type:

1. Add a JDK-only `TaskWidget` subclass in core. Implement a detached `copy()`
   preserving the ID; optionally override `progress()` for checkable content.
2. Implement `WidgetRenderer<YourWidget>` in the client and register it with
   `WidgetCatalog.register(...)` during client initialization. Its factory adds
   the type to Add section. Use `WidgetContext` and the fluent UI to render it.
3. Route mutations through `context.change(...)`; keep game objects out of the
   model. No screen/editor changes are needed for a regular new section type.

For another storage mod, register a `CarriedInventory.Reader` with an item
predicate, optional shared-storage identity, and read-only content enumeration.
Register readers during initialization. The same adapter works for every
Materials section; do not introduce a separate widget per backpack brand.
Depth, entry, and visitation limits bound collection; shared storage identities
prevent counting the same bag contents twice. Storage readers run on the server
tick thread for authoritative snapshots and may reject unavailable client reads.

## User-run verification

Per `AGENTS.md`, the assistant does not execute verification. The refactored
sources and added tests have not been compiled or run. Earlier foundation
evidence in `verification.md` does not validate this UI revision.

With the JDK environment configured as in the README:

```powershell
.\gradlew.bat :platform-forge-1.7.10:test
.\gradlew.bat check build
.\gradlew.bat :platform-forge-1.7.10:runClient
```

`WorkspaceSessionTest` covers combined filters/project isolation, rejected empty
titles, project-specific drafts, cancelled edits, saved edits, navigation,
comments, status changes, editor navigation guards, direct field selection,
idempotent activity logging, filter reset, board moves, ordering, local nickname
resolution, contextual creation and repeated creation without duplicate edits.
`DragAndDropTest` covers short clicks, accepted/rejected drops, nested targets,
cancellation, external payloads and clipped source bounds without rendering.
`TaskBodyTest` covers matching policies, alternatives, scopes, long counts,
cross-section allocation, manual overrides, detached copies, ordering and custom
types. `TaskBodySessionTest` covers body drafts, undo, templates, monotonic keys
and inline text/link navigation, detached document edits, movable legacy text,
cancel/new-block cleanup, stale-document rejection, selection retention,
single-action document undo and lossless Markdown saves. `DocumentDraftTest`
covers insertion gaps, drag index adjustment, paragraph splitting, slash
replacement and selection after removal. `MarkdownTest` covers parser constructs and
styled wrapping/alignment without starting a game. JUnit is test-only
and is not bundled in the mod. These tests have not been executed by the assistant.

For manual acceptance:

1. Enter a disposable world; press J and try Board, Backlog, and Issues.
2. Search by title and key, choose both filters, switch projects, and use Reset.
3. Create an issue, attempt an empty title, then save a populated form.
4. Edit an issue, cancel, reopen, and confirm the original values remain.
5. Change status/assignee, post a comment, and inspect the other views.
6. Cancel a create draft, switch projects, then return and reopen Create.
7. Scroll lists/details using the wheel, drag the thumb, and click the track.
   Click near clipping edges and hover truncated labels to see their full text.
8. Change window size/GUI scale, use Tab and Ctrl+F, exercise Up/Down/Enter and
   Escape in a choice menu, then close/reopen Mira.
9. Separately test the installable jar on a dedicated server and in the target
   GTNH pack; no claim of runtime or pack compatibility is made for this revision.
10. Apply a resource pack that changes vanilla GUI textures and item icons.
    Confirm Mira uses them, with readable text and intact panel corners.
11. Drag between columns, reorder cards at the insertion line, and drop into
    an empty column. Check status/counts and confirm a move does not open details.
12. On a narrow GUI, drag onto a status tab; check that it opens the destination.
    Try releasing outside the board, Escape, slight click movement and edge
    scrolling. None of the cancelled gestures should alter status/order.
13. Use Me in a task and form. Check the face beside the nickname, the online
    roster picker, neutral Unassigned, and the fallback for an unknown skin.
14. Create through C/Ctrl+N, Enter, and a column's + button. Check filter defaults,
    expanded optional fields, validation without scrolling, and resumed drafts.
15. Add several issues using Ctrl+Enter/Create + next; confirm titles/descriptions
    clear, planning choices remain, confirmation updates, and title gets focus.
    Check that Ctrl+Enter in Edit updates the original without creating a copy.
16. Expand details at a wide GUI scale and compare with the inspector/narrow
    view; check independent property scrolling and the collapsed activity section.
17. Add several sections of the same type, rename/reorder/remove them, cancel an
    edit, then save another edit. Check multiline selection/copy/paste and Enter
    for adding checklist steps. Change templates without losing existing sections.
18. With NEI installed, drag from its item and bookmark panels into body,
    Materials, and an existing material row. Check quantities, alternatives,
    rejected drops, NEI search, and that no items are given or inventory slots
    changed. Repeat plain Forge without NEI.
19. Compare automatic counts against main inventory, armor, cursor stack, carried
    and equipped supported bags. Try duplicate/shared bags and nested storage.
    Exercise item/variant/tag/ore matching, scopes, manual checks, overlapping
    requirements, and removal of items. Check availability warnings.
20. Undo moves, section edits, properties, comments and creation; check order and
    progress. Pin a task, close the UI, change carried items, reopen with J, then
    disconnect/reconnect. Repeat dedicated-server inventory observation in GTNH.
21. Select several text/widget blocks and change each without saving in between.
    Check one shared toolbar, no default block headings/cards, compact text input,
    and retained edits after selection changes. Cancel/Escape must discard all
    document changes while keeping details open. Save another edit and Undo once
    to restore the complete original description and widgets.
22. Use + gaps to insert before, between and after blocks. Type / in an empty
    paragraph, search for a widget and select it; ensure the paragraph is replaced.
    Split text at the start/middle/end with Enter, including a selection;
    Shift+Enter must preserve a newline. Drag :: handles to each insertion gap,
    including the first/last and either side of the source. Try Escape, rejected
    drops and edge scrolling. Move the original description between widgets.
    Repeat in creation/full Edit, with templates, narrow/inspector views, and
    near the bottom of the viewport. Check pinned toolbar/actions and focus.
23. Exercise Markdown headings/anchors, nested lists, quotes, bold/italic/strike,
    escapes/entities, hard breaks, reference links, code with indentation, tables
    with alignment, task lists, external/issue links and local/remote images.
    Check Copy, source/preview, selection formatting, failed images, literal HTML,
    narrow/inspector layouts and a resource pack. No runtime check has been run
    by the assistant for these changes.
24. Compare checkbox/material/drop behavior before and during document editing.
    During editing, Cancel must roll back those changes too. Direct checks in
    reading mode remain independently undoable. Navigate away, open the full
    issue form, switch projects or Undo during an edit and check draft cleanup.
25. Open each settings section at wide and narrow GUI scales, with and without
    NEI. Check the rail/picker, scroll edges, long names, field focus, name/column
    save footer, and tooltips with a resource pack. Create and cancel a project;
    try an invalid key, save a valid one, and rename it without changing its key.
26. Repeat settings as Owner, Editor and Viewer. Give/update/remove access; review
    and cancel an ownership transfer. Archive/restore items, including a workspace.
    Search Archives and Activity, filter them, and exercise their empty states.
27. Start/complete a sprint, select a destination for unfinished issues, and switch
    projects while editing board columns. Check that saving status assignments
    moves them between columns together. Try a nonnumeric column order.
28. Select/edit workflow statuses and transitions through both graph and lists.
    Add a transition with From/To, toggle required fields, choose a starting status,
    duplicate a workflow, edit scheme mappings, and review replacement statuses
    before publishing. Repeat at a narrow scale where the graph is replaced by lists.
29. With two clients, edit the same project name and exercise both conflict actions.
    Check that the local form survives a remote rename, saving keeps the page visible,
    and permission loss clears unavailable editing state. Escape should leave a New
    form before leaving settings; C, Ctrl+N and / must not act on hidden issue views.

The added settings regression cases in `WorkspaceSynchronizationTest` cover creation
versus editing, key validation, cancelled creation, concurrent renames, project-scoped
column selection and duplicate workspace names. They have not been run by the assistant.
