# Persistent collaborative backend

Implementation is present; compilation, automated checks, packet interoperability,
in-game behavior and crash/restart behavior remain for the user to verify. No new
verification commands or game instances were run by the assistant.

## Workspace and access model

A world database contains multiple workspaces. Each workspace contains multiple
projects and has exactly one Owner. Any joined player can create a private workspace;
creation also supplies a main project, standard issue types, a default workflow,
scheme and board. Other players receive no workspace records until invited.

| Role | Capabilities |
| --- | --- |
| Owner | All Editor actions; membership, ownership transfer, workspace settings, issue types, workflows and schemes |
| Editor | Read all projects; manage projects, columns, sprints, tasks and their own comments; restore archives |
| Viewer | Read every project in the workspace |
| No role | No workspace records or activity in synchronization replies |

Membership and assignment use server player UUIDs. The world retains known player
names for offline selection. The reply supplies the current player's canonical
server UUID; permissions never depend on the UUID supplied by a client session.
Ownership transfer promotes the recipient and makes the previous Owner an Editor.
Membership changes immediately change subsequent writes and pushed snapshots.
Losing write access closes local editors; losing read access removes the projection
and pinned tracker. Unsaved typing is never broadcast.

Operators do not automatically read private workspaces through the UI. Permission
level 2 grants these explicit recovery commands, which record an activity entry:

```text
/miraadmin list
/miraadmin role <workspace UUID> <known player nickname or UUID> OWNER
/miraadmin role <workspace UUID> <known player nickname or UUID> EDITOR
/miraadmin role <workspace UUID> <known player nickname or UUID> VIEWER
/miraadmin role <workspace UUID> <known player nickname or UUID> NONE
```

Use a UUID when stored nicknames are ambiguous. Removing the Owner is rejected;
transfer ownership first. The console can run the same commands.

## Transaction API

`mira.core.jira.JiraDatabase` owns the authoritative state. Its synchronized API is
independent of Forge, NBT, networking, rendering and third-party database libraries:

- `rememberPlayer(uuid, name)` persists a known player or nickname update.
- `view(uuid)` returns a detached, permission-filtered snapshot.
- `snapshot()` returns a detached administrative/storage snapshot for trusted adapters.
- `execute(actorUuid, actorName, operator, command)` validates and durably commits
  one atomic operation. The platform derives the actor and operator flag.

`Model.Entity` is a typed entity (`Kind`) with an immutable UUID and workspace UUID,
a schema-checked field map and a version map. Entities cover workspaces, memberships,
projects, issue types, workflows, statuses, transitions, schemes, type mappings,
columns, sprints, tasks, description blocks and comments. References are checked
for kind and workspace; task parents and sprints additionally stay inside a project.
Task keys are allocated by the server from a persistent project counter and are
never reused after archive, Undo or restart.

Use explicit creation operations for workspaces, projects, workflows and schemes.
Other entities and ordinary edits use `EDIT` patches. Every written field carries
its original version. Extra expected fields act as read guards, for example when
archiving a block that another player might be editing:

```java
Entity issue = snapshot.require(issueId, Kind.TASK);
Command command = new Command();
command.workspace = issue.workspace;
command.patches.add(Patch.edit(issue).set(issue, "title", "Expand steam supply"));
database.execute(serverPlayerId, serverPlayerName, isOperator, command);
```

Only changed fields are written. Disjoint fields merge even when the editors began
from the same snapshot. A version mismatch rejects the entire operation with
`Model.Conflict`; domain and authorization failures use `Model.Rejected`. Description
blocks have independent `content` and `rank` fields, preserving widget IDs, order,
checklists, material alternatives/matching settings, locations and blocker links.
Two edits to the same block conflict; different blocks merge. Independent new
comments append, and the server sets their authors. Editors can edit/archive their
own comments; Owners can moderate all comments.

The client's form retains its original snapshot until an acknowledged save. On
conflict, **Keep my edits** explicitly rebases the retained draft while keeping
other players' changes to untouched fields/blocks. **Reload saved** discards it.
For an immediate action, Keep my edits retries its changed fields against current
versions. Graph/configuration conflicts retain their forms and can be reviewed
and resubmitted after selecting the current saved configuration.

Accepted command UUIDs are stored in activity history. Retrying an accepted command,
including after a restart, acknowledges it without creating duplicate tasks/comments.
Activity is generated by the server with actor, timestamp, revisions and before/after
values. Undo targets your latest eligible non-undone action and checks every affected
field before applying a new audited inverse. It rejects overwriting later edits or
hiding work someone added to an entity you created. Project numbering never rewinds.
Publication, sprint completion, ownership transfer and operator recovery are explicit
operations and are not exposed as ordinary Undo actions.

## Workflows and boards

Owners use **Settings > Workflows** to create or copy a graph. A status has a stable
logical ID, name, category (`TODO`, `IN_PROGRESS`, `DONE`) and graph position. A named
transition joins statuses or starts at **Any status**. It can require Editor/Owner
access and any of `title`, `description`, `assignee`, `priority`, `sprint`, `epic`.
Description checks include actual content in the ordered task-body blocks.

- Click a node or arrow to select it; the transition list also selects every edge.
- Drag a node onto another node to create a transition, or onto empty graph space
  to change its position. Name, category, coordinates, role and requirements use
  the form below the graph. Make initial chooses the creation status.
- Graph edits save to an Owner-only draft. Existing tasks use immutable published
  generations until **Publish workflow** succeeds.
- Schemes map issue types to reusable workflows, with a required default mapping.
  Projects can share a scheme. Scheme edits are drafted and published separately.
- Publishing or assigning a different project scheme lists tasks whose current
  status will disappear, including archived tasks. Choose replacement statuses and
  apply the publication/migration atomically. An invalid or stale migration changes
  neither tasks nor the published configuration.

New tasks start in the applicable workflow's initial status. Changing type preserves
the current status when valid; otherwise the new workflow's initial status is used.
Changing type cannot bypass a transition gate within the same applicable workflow.
Every status write uses the same server-side rules as board dragging.

Columns are project configuration and can contain several statuses from different
workflows. Board moves consider the issue's applicable workflow and current role.
If several transitions lead into a destination column, the player chooses one.
Deleted statuses are removed from column mappings at publication. Newly introduced
unmapped statuses appear in an **Unmapped** column until configured.

Sprints belong to projects and progress from `PLANNED` to `ACTIVE` to `COMPLETED`.
Only one non-archived sprint can be active per project. Completing a sprint leaves
finished tasks there and sends unfinished, non-archived tasks to a chosen planned
sprint or the backlog. Completed sprints cannot receive new assignments.
Archives hide work without hard deletion. Restore a parent project/task before
editing its children. If another player archives work while its editor is open,
the local draft is retained and the screen offers restoration before editing
continues. Workflows and schemes used by active projects cannot be archived.

## World storage and synchronization

The overworld save directory owns these files, shared by every dimension:

```text
<world>/data/mira-workspaces.dat       current database
<world>/data/mira-workspaces.dat.bak   preceding successful database
```

`WorldDatabase` checks Minecraft's save-session lock, encodes a versioned database,
writes a SHA-256 checksum and payload to a sibling temporary file, flushes/syncs it,
preserves the old database and replaces the main file. It uses atomic replacement
when the filesystem supports it, with replacement fallback otherwise. Domain state
and acknowledgments are published only after the store returns successfully. The
database is saved on each accepted mutation; waiting for a vanilla world save is
not required. No JDBC driver, external service or separate database mod is needed.

Checksum, format and domain failures stop database loading. A missing main file
with an existing backup also fails closed. To recover, stop the server/world,
preserve the damaged files and explicitly restore a known-good world backup or
`mira-workspaces.dat.bak` to the main filename. The `.bak` is one prior transaction,
not an archival backup policy. Do not edit/copy a live database in place.

`JiraNetwork` transports GZIP payloads in bounded 24,000-byte fragments. Netty only
assembles/queues requests; the server tick thread performs authorization, domain
work and disk writes. After commits, connected players receive filtered snapshots
on that tick. There is no polling interval for task updates. The client tick thread
applies snapshots, preserves detached drafts and executes acknowledgment callbacks.
Unacknowledged commands retry with the same UUID. Disconnect resets connection
state; late replies from an old connection are ignored.

Current limits: 64 MiB encoded database/snapshot, 100,000 entities, 4 MiB per command,
512 patched entities and 1,024 arguments per transaction. Block values are bounded
to 1 MiB of base64, with additional item/list/text bounds. Oversized transactions
are rejected without a partial commit. Snapshots currently transmit the authorized
workspace records and history in full; very large installations will benefit from
delta/paged synchronization and a larger-scale storage strategy.

## User-owned verification

None of these commands have been run for this implementation:

```powershell
.\gradlew.bat :core:check
.\gradlew.bat :platform-forge-1.7.10:test
.\gradlew.bat check build
```

Use the JDK selections from the README. New tests cover permission enforcement,
field/block conflicts, comment attribution, type-change gate bypasses, ownership,
retry idempotency, persistence rollback, numbering, Undo, sprint completion,
workflow migration, file/checksum recovery and editor snapshot integration.

Manual acceptance, with two players and a third read-only account:

1. Create a private workspace. Confirm another player sees no records; invite them
   as Editor and the third player as Viewer, including an offline member if possible.
2. Create multiple projects and tasks. Observe saved edits, card order, comments and
   body widgets immediately on the other authorized client. Confirm typing is local.
3. Edit different fields, then different description blocks concurrently. Save both.
   Repeat on the same field/block and exercise Keep my edits and Reload saved.
4. Verify Viewer writes fail, Editor administration fails, role revocation updates an
   open screen and pinned HUD, and operator recovery appears in workspace activity.
5. Configure different type workflows, restricted/required-field transitions and
   multi-status board columns. Try forbidden moves and type-change bypasses. Publish
   a removed status with migration and confirm both clients see consistent results.
6. Start/complete sprints, archive/restore projects and tasks, edit/moderate comments,
   and use Undo before and after another player edits the same field.
7. Quit/restart the world and dedicated server. Confirm roles, graphs/drafts, schemes,
   sprints, keys/order, comments, body values and history survive. In a disposable
   copied world, exercise corrupt-file/missing-file recovery and rejected disk writes.
