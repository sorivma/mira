package mira.platform.forge1710.client.workspace;

import mira.core.taskbody.NoteWidget;
import mira.core.taskbody.TaskBody;
import mira.core.taskbody.TaskWidget;

/** Detached description document. Selection and edits share one ordered sequence of blocks. */
final class DocumentDraft {
    final TaskBody body;
    private String selectedId;
    private int focusRevision;

    DocumentDraft(TaskBody source) { body = source.copy(); }

    static TaskBody compose(String description, TaskBody widgets, NoteWidget introduction) {
        TaskBody document = new TaskBody();
        if (!description.isEmpty()) {
            introduction.text = description;
            document.widgets.add(introduction);
        }
        document.widgets.addAll(widgets.widgets);
        return document;
    }

    TaskWidget selected() {
        for (TaskWidget widget : body.widgets) if (widget.id.equals(selectedId)) return widget;
        return null;
    }
    int focusRevision() { return focusRevision; }
    int selectedIndex() { return body.widgets.indexOf(selected()); }
    int insertionIndex() { int index = selectedIndex(); return index < 0 ? body.widgets.size() : index + 1; }
    void select(String id) {
        if (id != null && id.equals(selectedId)) return;
        selectedId = id; focusRevision++;
    }
    void insert(TaskWidget widget, int position) {
        body.widgets.add(Math.max(0, Math.min(position, body.widgets.size())), widget);
        select(widget.id);
    }
    void insertFromSlash(TaskWidget widget) {
        TaskWidget current = selected();
        int index = selectedIndex();
        if (current instanceof NoteWidget && ((NoteWidget) current).text.trim().isEmpty()) {
            body.widgets.remove(current); insert(widget, index);
        } else insert(widget, insertionIndex());
    }
    void paragraph() { insert(new NoteWidget(), insertionIndex()); }
    void splitText(String before, String after) {
        TaskWidget current = selected();
        if (!(current instanceof NoteWidget)) return;
        ((NoteWidget) current).text = before;
        NoteWidget next = new NoteWidget(); next.text = after;
        insert(next, selectedIndex() + 1);
    }
    void removeSelected() {
        int index = selectedIndex();
        if (index < 0) return;
        body.widgets.remove(index); selectedId = null;
        if (!body.widgets.isEmpty()) select(body.widgets.get(Math.min(index, body.widgets.size() - 1)).id);
        else focusRevision++;
    }
    void moveSelected(int direction) {
        int from = selectedIndex(), to = from + direction;
        if (from < 0 || to < 0 || to >= body.widgets.size()) return;
        TaskWidget widget = body.widgets.remove(from); body.widgets.add(to, widget);
        focusRevision++;
    }
    /** Position is a gap in the sequence before removal, including the final gap. */
    void moveTo(String id, int position) {
        TaskWidget widget = null;
        for (TaskWidget candidate : body.widgets) if (candidate.id.equals(id)) { widget = candidate; break; }
        if (widget == null) return;
        int from = body.widgets.indexOf(widget);
        position = Math.max(0, Math.min(position, body.widgets.size()));
        body.widgets.remove(from);
        if (from < position) position--;
        body.widgets.add(position, widget);
        if (widget.id.equals(selectedId)) focusRevision++; else select(widget.id);
    }
}
