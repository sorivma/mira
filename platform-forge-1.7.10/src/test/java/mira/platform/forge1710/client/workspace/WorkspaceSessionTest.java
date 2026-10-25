package mira.platform.forge1710.client.workspace;

import java.util.List;
import mira.platform.forge1710.client.workspace.WorkspaceData.Issue;
import mira.platform.forge1710.client.workspace.WorkspaceData.Status;
import org.junit.Test;

import static org.junit.Assert.*;

/** Exercises user interactions without initializing a Minecraft client or an OpenGL context. */
public class WorkspaceSessionTest {
    private final DemoWorkspace data = new DemoWorkspace();
    private final WorkspaceSession session = new WorkspaceSession(data);

    @Test
    public void filtersIntersectAndStayInsideTheCurrentProject() {
        session.query("  BASE-5  ");
        assertEquals(1, session.issues().size());
        session.assigneeFilter("Unassigned");
        assertTrue(session.issues().isEmpty());
        session.assigneeFilter("You");
        session.sprintFilter("Backlog");
        assertTrue(session.issues().isEmpty());
        session.sprintFilter("Sprint 4");
        assertEquals("BASE-5", session.issues().get(0).key);
        session.project("Orbital Outpost");
        assertTrue(session.issues().isEmpty());
    }

    @Test
    public void blankTitleDoesNotCreateOrCloseTheDraft() {
        int count = data.issues.size();
        session.createIssue();
        session.draft().title = "   ";
        assertFalse(session.saveDraft());
        assertEquals(count, data.issues.size());
        assertNotNull(session.draft());
        assertFalse(session.draft().validation.isEmpty());
    }

    @Test
    public void createDraftsBelongToTheirProjectAndSurviveCancel() {
        session.createIssue();
        session.draft().title = "Base draft";
        session.draft().priority = "Critical";
        session.cancelEditor();
        session.project("Orbital Outpost");
        session.createIssue();
        assertEquals("", session.draft().title);
        session.draft().title = "Outpost draft";
        session.cancelEditor();
        session.project("Main Base");
        session.createIssue();
        assertEquals("Base draft", session.draft().title);
        assertEquals("Critical", session.draft().priority);
        assertTrue(session.saveDraft());
        assertEquals("BASE-9", session.selected().key);
        assertEquals("Base draft", session.selected().title);
        session.createIssue();
        assertEquals("", session.draft().title);
        assertEquals("Medium", session.draft().priority);
    }

    @Test
    public void editingIsDetachedUntilSavedAndCannotOverwriteACreateDraft() {
        session.createIssue();
        session.draft().title = "Unfinished creation";
        session.cancelEditor();
        Issue issue = data.issues.get(0);
        String originalTitle = issue.title;
        session.select(issue);
        session.editIssue();
        session.draft().title = "Unsaved edit";
        session.draft().assignee = "You";
        session.cancelEditor();
        assertEquals(originalTitle, issue.title);
        assertEquals("Sam", issue.assignee);
        session.createIssue();
        assertEquals("Unfinished creation", session.draft().title);
        assertEquals("Unassigned", session.draft().assignee);
    }

    @Test
    public void savingAnEditUpdatesTheSameIssueAndKeepsItsStatus() {
        Issue issue = data.issues.get(0);
        int count = data.issues.size();
        session.select(issue);
        session.editIssue();
        session.draft().title = "  Better steam supply  ";
        session.draft().description = "  Enough for two turbines.  ";
        session.draft().type = "Story";
        session.draft().priority = "Critical";
        session.draft().assignee = "You";
        session.draft().sprint = "Sprint 5";
        assertTrue(session.saveDraft());
        assertSame(issue, session.selected());
        assertEquals(count, data.issues.size());
        assertEquals("Better steam supply", issue.title);
        assertEquals("  Enough for two turbines.  ", issue.description);
        assertEquals("Story", issue.type);
        assertEquals("Critical", issue.priority);
        assertEquals("You", issue.assignee);
        assertEquals("Sprint 5", issue.sprint);
        assertEquals(Status.DOING, issue.status);
        assertEquals("You edited the issue", issue.activity.get(issue.activity.size() - 1));
    }

    @Test
    public void escapeReturnsThroughEditorAndDetailsWithoutLosingNavigation() {
        session.navigate(WorkspaceSession.View.BACKLOG);
        session.query("steam");
        session.select(data.issues.get(0));
        session.editIssue();
        assertTrue(session.back());
        assertNull(session.draft());
        assertNotNull(session.selected());
        assertTrue(session.back());
        assertNull(session.selected());
        assertEquals(WorkspaceSession.View.BACKLOG, session.view());
        assertEquals("steam", session.query());
        assertFalse(session.back());
    }

    @Test
    public void commentsDoNotLeakIntoOtherIssuesOrAcceptWhitespace() {
        Issue first = data.issues.get(0);
        Issue second = data.issues.get(1);
        session.select(first);
        int comments = first.comments.size();
        session.comment("   ");
        session.postComment();
        assertEquals(comments, first.comments.size());
        session.comment("  Test under full load.  ");
        session.postComment();
        assertEquals("You: Test under full load.", first.comments.get(comments));
        assertEquals("", session.comment());
        session.comment("Unsent text");
        session.select(second);
        assertEquals("", session.comment());
        session.postComment();
        assertTrue(second.comments.isEmpty());
    }

    @Test
    public void statusChangesAreVisibleInEveryViewAndRecordedInActivity() {
        Issue issue = data.issues.get(1);
        session.select(issue);
        session.changeStatus("In progress");
        assertEquals(Status.DOING, issue.status);
        session.navigate(WorkspaceSession.View.ISSUES);
        assertEquals(Status.DOING, session.issues().get(1).status);
        assertEquals("Open -> In progress", issue.activity.get(issue.activity.size() - 1));
    }

    @Test
    public void anOpenEditorKeepsItsProjectAndNavigationStable() {
        session.createIssue();
        session.draft().title = "Base work";
        session.project("Orbital Outpost");
        session.navigate(WorkspaceSession.View.ISSUES);
        assertSame(data.projects.get(0), session.project());
        assertEquals(WorkspaceSession.View.BOARD, session.view());
        assertTrue(session.saveDraft());
        assertEquals("BASE-9", session.selected().key);
    }

    @Test
    public void directSelectionsApplyTheirValuesAndDoNotDuplicateActivity() {
        Issue issue = data.issues.get(0);
        session.select(issue);
        session.changeStatus("Done");
        session.changeAssignee("You");
        session.changePriority("Critical");
        session.changeType("Story");
        session.changeSprint("Sprint 5");
        assertEquals(Status.DONE, issue.status);
        assertEquals("You", issue.assignee);
        assertEquals("Critical", issue.priority);
        assertEquals("Story", issue.type);
        assertEquals("Sprint 5", issue.sprint);
        int events = issue.activity.size();
        session.changeStatus("Done");
        session.changeAssignee("You");
        session.changePriority("Critical");
        session.changeType("Story");
        session.changeSprint("Sprint 5");
        assertEquals(events, issue.activity.size());
    }

    @Test
    public void resettingFiltersKeepsTheProjectAndRestoresItsIssues() {
        session.project("Orbital Outpost");
        session.query("launch");
        session.assigneeFilter("Alex");
        session.sprintFilter("Backlog");
        assertTrue(session.issues().isEmpty());
        session.clearFilters();
        assertEquals("Orbital Outpost", session.project().name);
        assertEquals("", session.query());
        assertEquals("Everyone", session.assignee());
        assertEquals("All work", session.sprint());
        assertEquals(2, session.issues().size());
    }

    @Test
    public void movingACardChangesStatusWithoutOpeningDetailsOrChangingFilters() {
        Issue issue = data.issues.get(0);
        session.assigneeFilter("Sam");
        session.sprintFilter("Sprint 4");
        session.moveIssue(issue, Status.REVIEW);
        assertEquals(Status.REVIEW, issue.status);
        assertNull(session.selected());
        assertEquals(WorkspaceSession.View.BOARD, session.view());
        assertEquals("Sam", session.assignee());
        assertEquals("Sprint 4", session.sprint());
        assertTrue(session.issues().contains(issue));
        assertEquals("In progress -> Review", issue.activity.get(issue.activity.size() - 1));
    }

    @Test
    public void droppingBeforeAnotherCardReordersWithinAColumn() {
        Issue moved = data.issues.get(3);
        Issue before = data.issues.get(1);
        int activity = moved.activity.size();
        session.moveIssueBefore(moved, before);
        List<Issue> issues = session.issues();
        assertEquals(Status.OPEN, moved.status);
        assertTrue(issues.indexOf(moved) < issues.indexOf(before));
        assertEquals(activity, moved.activity.size());
    }

    @Test
    public void droppingBeforeACardInAnotherColumnMovesAndOrdersIt() {
        Issue moved = data.issues.get(1);
        Issue before = data.issues.get(0);
        session.moveIssueBefore(moved, before);
        assertEquals(Status.DOING, moved.status);
        assertEquals(data.issues.indexOf(before) - 1, data.issues.indexOf(moved));
        assertNull(session.selected());
    }

    @Test
    public void droppingOnItselfDoesNotChangeOrderOrActivity() {
        Issue issue = data.issues.get(1);
        int index = data.issues.indexOf(issue), activity = issue.activity.size();
        session.moveIssueBefore(issue, issue);
        assertEquals(index, data.issues.indexOf(issue));
        assertEquals(activity, issue.activity.size());
    }

    @Test
    public void aCardFromAnotherProjectCannotBeMoved() {
        Issue foreign = data.issues.get(8);
        Status original = foreign.status;
        try {
            session.moveIssue(foreign, Status.DONE);
            fail("Expected project isolation");
        } catch (IllegalArgumentException expected) {
            assertEquals(original, foreign.status);
            assertEquals(8, data.issues.indexOf(foreign));
        }
    }

    @Test
    public void playerAliasUsesTheActualLocalNickname() {
        DemoWorkspace samples = new DemoWorkspace();
        WorkspaceSession named = new WorkspaceSession(samples, "Builder");
        assertEquals("Builder", named.self());
        assertEquals("Builder", samples.issues.get(4).assignee);
    }

    @Test
    public void aFreshDraftInheritsColumnAndConcreteFilters() {
        session.assigneeFilter("Sam");
        session.sprintFilter("Sprint 4");
        session.createIssue(Status.DOING);
        assertEquals(Status.DOING, session.draft().status);
        assertEquals("Sam", session.draft().assignee);
        assertEquals("Sprint 4", session.draft().sprint);
        session.draft().title = "Quick task";
        assertTrue(session.saveDraft());
        assertEquals(Status.DOING, session.selected().status);
        assertEquals("Sam", session.selected().assignee);
        assertEquals("Sprint 4", session.selected().sprint);
    }

    @Test
    public void openingAnExistingDraftPreservesItsOriginalContext() {
        session.createIssue(Status.OPEN);
        session.draft().title = "Unfinished";
        session.draft().priority = "Critical";
        session.cancelEditor();
        session.assigneeFilter("Alex");
        session.sprintFilter("Sprint 5");
        session.createIssue(Status.REVIEW);
        assertEquals("Unfinished", session.draft().title);
        assertEquals("Critical", session.draft().priority);
        assertEquals(Status.OPEN, session.draft().status);
        assertEquals("Unassigned", session.draft().assignee);
        assertEquals("Backlog", session.draft().sprint);
    }

    @Test
    public void createAnotherClearsTextAndKeepsPlanningContext() {
        int count = data.issues.size();
        session.createIssue(Status.REVIEW);
        session.draft().title = "First task";
        session.draft().description = "Specific to the first task";
        session.draft().type = "Story";
        session.draft().priority = "High";
        session.draft().assignee = "Sam";
        session.draft().sprint = "Sprint 4";
        assertTrue(session.saveAndCreateAnother());
        assertEquals(count + 1, data.issues.size());
        assertNull(session.selected());
        assertEquals("", session.draft().title);
        assertEquals("", session.draft().description);
        assertEquals("Story", session.draft().type);
        assertEquals("High", session.draft().priority);
        assertEquals("Sam", session.draft().assignee);
        assertEquals("Sprint 4", session.draft().sprint);
        assertEquals(Status.REVIEW, session.draft().status);
        assertEquals("Created BASE-9", session.notice());
        session.draft().title = "Second task";
        assertTrue(session.saveDraft());
        assertEquals(count + 2, data.issues.size());
        assertEquals("BASE-10", session.selected().key);
        assertEquals("Second task", session.selected().title);
    }

    @Test
    public void createAnotherRejectsEmptyTitlesWithoutLosingTheDraft() {
        session.createIssue();
        session.draft().title = "   ";
        int count = data.issues.size();
        assertFalse(session.saveAndCreateAnother());
        assertEquals(count, data.issues.size());
        assertNotNull(session.draft());
        assertFalse(session.draft().validation.isEmpty());
    }

    @Test
    public void createAnotherCannotTurnAnEditIntoADuplicate() {
        Issue issue = data.issues.get(0);
        String original = issue.title;
        int count = data.issues.size();
        session.select(issue);
        session.editIssue();
        session.draft().title = "Changed title";
        assertFalse(session.saveAndCreateAnother());
        assertEquals(count, data.issues.size());
        assertEquals(original, issue.title);
        assertTrue(session.saveDraft());
        assertEquals("Changed title", issue.title);
    }
}
