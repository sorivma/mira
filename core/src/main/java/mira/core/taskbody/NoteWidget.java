package mira.core.taskbody;

public final class NoteWidget extends TaskWidget {
    public String text = "";
    public NoteWidget() { super("Note"); }
    public NoteWidget(String id, String title) { super(id, title); }
    @Override public NoteWidget copy() { NoteWidget value = new NoteWidget(id, title); value.text = text; return value; }
}
