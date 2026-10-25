package mira.platform.forge1710.client.workspace;

import java.util.HashMap;
import java.util.Map;
import mira.core.taskbody.MaterialsWidget;
import mira.core.taskbody.MaterialsWidget.Material;
import mira.core.taskbody.TaskBody;
import mira.core.taskbody.TaskWidget;
import mira.platform.forge1710.client.InventoryClient;
import mira.platform.forge1710.client.ui.MarkdownEditor;
import mira.platform.forge1710.client.ui.Ui;
import mira.platform.forge1710.client.widgets.WidgetCatalog;
import mira.platform.forge1710.client.widgets.WidgetContext;
import mira.platform.forge1710.client.widgets.WidgetRenderer;
import mira.platform.forge1710.inventory.ItemDescriptors;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import static mira.platform.forge1710.client.ui.Ui.*;

/** One document flow, one selected block, and one shared toolbar. */
final class DocumentView {
    private final WorkspaceSession session;
    private final FontRenderer font;
    private final boolean form;
    private WidgetContext.Inputs selectedInputs = new WidgetContext.Inputs();
    private final WidgetContext.Inputs readingInputs = new WidgetContext.Inputs();
    private final Map<String, WidgetContext.Inputs> blockInputs = new HashMap<>();
    private final Map<String, BlockDrag> drags = new HashMap<>();
    private DocumentDraft displayedDraft;
    private TaskWidget displayedSelection;
    private WidgetContext selectedContext;
    private int focusRevision = -1;
    private boolean focusPending;

    DocumentView(WorkspaceSession session, FontRenderer font, boolean form) {
        this.session = session; this.font = font; this.form = form;
    }
    private DocumentDraft draft() {
        if (!form) return session.documentDraft();
        return session.draft() == null ? null : session.draft().document();
    }
    private DocumentDraft activate(String id) {
        if (form) { if (session.draft() != null) session.draft().document().select(id); }
        else session.beginDocument(id);
        return draft();
    }
    Ui.Node build(Ui ui, int width) {
        DocumentDraft document = draft();
        TaskBody body = document == null ? session.documentBody() : document.body;
        TaskWidget selected = document == null ? null : document.selected();
        if (displayedDraft != document) { displayedDraft = document; drags.clear(); blockInputs.clear(); focusRevision = -1; displayedSelection = null; }
        if (displayedSelection != selected) {
            displayedSelection = selected;
            selectedInputs = selected == null ? new WidgetContext.Inputs()
                : blockInputs.computeIfAbsent(selected.id, ignored -> new WidgetContext.Inputs());
        }
        focusPending = document != null && focusRevision != document.focusRevision();
        if (document != null) focusRevision = document.focusRevision();
        WidgetContext reading = new WidgetContext(body, false, InventoryClient.snapshot(), font, ui, Math.max(1, width - 20),
            readingInputs, action -> { if (document == null) session.mutateBody(action); else action.run(); }, session::openIssue, session::issueDone);
        selectedContext = selected == null ? null : new WidgetContext(body, true, reading.inventory, font, ui,
            Math.max(1, width - 20), selectedInputs, Runnable::run, session::openIssue, session::issueDone);
        if (selectedContext != null) { selectedContext.sharedTextTools = true; selectedContext.nextParagraph = () -> paragraph(ui); }
        Ui.Node flow = column(0); int height = 0;
        for (int index = 0; index <= body.widgets.size(); index++) {
            final int position = index;
            Ui.Node gap = insertionPoint(() -> chooseInsert(ui, position, false));
            if (document != null) gap.dropBefore(BlockDrag.class, moved -> drop(ui, document, moved, position));
            flow.add(gap); height += 10;
            if (index == body.widgets.size()) break;
            TaskWidget widget = body.widgets.get(index);
            boolean active = widget == selected;
            WidgetRenderer.View view = WidgetCatalog.build(widget, active ? selectedContext : reading);
            Ui.Node content = column(3); int blockHeight = view.height;
            // A custom heading is content. Default widget names live in the shared toolbar.
            if (!WidgetCatalog.textBlock(widget) && !widget.title.equals(WidgetCatalog.name(widget)) && !widget.title.isEmpty()) {
                content.add(heading(widget.title).height(12)); blockHeight += 15;
            }
            content.add(view.node.height(view.height));
            Ui.Node handle = text("::", active ? ACCENT : MUTED).height(16).width(16)
                .click(() -> activate(widget.id)).hint("Select block", "Drag to an insertion line to reorder").revealOnParentHover();
            if (document != null) handle.draggable(drags.computeIfAbsent(widget.id, ignored -> new BlockDrag(document, widget.id)));
            flow.add(row(4).height(Math.max(16, blockHeight)).hoverScope(active).anchor("block:" + widget.id)
                .background(active ? 0x11305A8A : 0).click(() -> activate(widget.id))
                .add(handle, content.height(blockHeight)));
            height += Math.max(16, blockHeight);
        }
        if (body.widgets.isEmpty()) {
            flow.add(text("Write text, or click + to insert a block.", MUTED).height(20).click(() -> paragraph(ui)));
            height += 20;
        }
        configureText(ui);
        return flow.height(height).dropTarget(ItemStack.class, stack -> addMaterial(ui, stack));
    }
    Ui.Node toolbar(Ui ui, int width) {
        DocumentDraft document = draft();
        if (document == null) return row(4).height(20).add(heading("Description"),
            text("Edit", ACCENT).width(28).click(() -> editFirst(ui)).hint("Edit the document", "Click any text or widget; / inserts a block"));
        Ui.Node controls = row(3).height(20).add(
            select("Insert block", "Insert block", WidgetCatalog.names(), name -> insert(ui, name, document.insertionIndex(), false)));
        TaskWidget selected = document.selected();
        if (selected != null) controls.add(
            button("^", () -> move(ui, -1)).width(18).hint("Move selected block up"),
            button("v", () -> move(ui, 1)).width(18).hint("Move selected block down"),
            button("-", () -> remove(ui)).width(18).hint("Remove selected block", "Saved when you save the document"));
        Ui.Node tools = column(4).add(controls); int height = 20;
        if (selectedContext != null && selected != null) {
            MarkdownEditor textEditor = selectedContext.firstMarkdown();
            if (textEditor != null) {
                mira.platform.forge1710.client.ui.Markdown.View formatting = textEditor.toolbar(ui, width);
                tools.add(formatting.node); height += formatting.height + 4;
            } else {
                tools.add(Ui.field(selectedContext.input(selected.id + "/title", 100, "Widget title", () -> selected.title, value -> selected.title = value)));
                height += 24;
            }
            if (focusPending) selectedContext.focusFirstInput();
        }
        focusPending = false;
        return tools.height(height);
    }
    private void configureText(Ui ui) {
        if (selectedContext == null) return;
        MarkdownEditor editor = selectedContext.firstMarkdown();
        if (editor == null) return;
        DocumentDraft owner = draft();
        editor.input.onParagraph((before, after) -> {
            if (draft() == owner) {
                owner.splitText(before, after); ui.blur();
                ui.revealAnchor("block:" + owner.selected().id);
            }
        });
        editor.input.onEmptySlash(() -> chooseInsert(ui, owner.selectedIndex(), true));
    }
    private void editFirst(Ui ui) {
        DocumentDraft document = activate(null);
        if (document == null) return;
        if (document.body.widgets.isEmpty()) document.paragraph();
        else document.select(document.body.widgets.get(0).id);
    }
    void chooseInsert(Ui ui) {
        DocumentDraft document = draft();
        chooseInsert(ui, document == null ? session.documentBody().widgets.size() : document.insertionIndex(), false);
    }
    private void chooseInsert(Ui ui, int position, boolean slash) {
        DocumentDraft document = draft();
        if (document == null) document = activate(null);
        if (document == null) return;
        DocumentDraft owner = document;
        ui.chooseAtFocus("Insert block", WidgetCatalog.names(), name -> {
            if (draft() == owner) insert(ui, name, position, slash);
        });
    }
    private void insert(Ui ui, String name, int position, boolean slash) {
        DocumentDraft document = draft();
        if (document == null) return;
        TaskWidget widget = WidgetCatalog.create(name);
        if (slash) document.insertFromSlash(widget); else document.insert(widget, position);
        ui.blur();
        ui.revealAnchor("block:" + widget.id);
    }
    void paragraph(Ui ui) {
        DocumentDraft document = draft();
        if (document == null) document = activate(null);
        if (document != null) { document.paragraph(); ui.blur(); ui.revealAnchor("block:" + document.selected().id); }
    }
    boolean ownsInput() { return selectedContext != null && selectedContext.focusedInput(); }
    private void move(Ui ui, int direction) {
        DocumentDraft document = draft(); if (document == null) return;
        document.moveSelected(direction);
        if (document.selected() != null) ui.revealAnchor("block:" + document.selected().id);
    }
    private void remove(Ui ui) {
        DocumentDraft document = draft(); if (document == null) return;
        document.removeSelected();
        if (document.selected() != null) ui.revealAnchor("block:" + document.selected().id);
    }
    private void drop(Ui ui, DocumentDraft owner, BlockDrag moved, int position) {
        if (draft() != owner || moved.owner != owner) return;
        owner.moveTo(moved.id, position); ui.revealAnchor("block:" + moved.id);
    }
    private void addMaterial(Ui ui, ItemStack stack) {
        if (stack == null || stack.getItem() == null) return;
        DocumentDraft document = draft();
        if (document == null) document = activate(null);
        if (document == null) return;
        MaterialsWidget materials = document.selected() instanceof MaterialsWidget ? (MaterialsWidget) document.selected() : null;
        if (materials == null) {
            materials = new MaterialsWidget(); document.insert(materials, document.insertionIndex());
        }
        mira.core.taskbody.ItemSpec item = ItemDescriptors.describe(stack);
        Material existing = null;
        for (Material material : materials.materials) if (material.alternatives.size() == 1
                && material.alternatives.get(0).item.equals(item.item) && material.alternatives.get(0).damage == item.damage
                && material.alternatives.get(0).tagHash.equals(item.tagHash)) { existing = material; break; }
        if (existing != null) existing.required = Math.min(1000000000L, existing.required + Math.max(1, stack.stackSize));
        else { Material material = new Material(item); material.required = Math.max(1, stack.stackSize); materials.materials.add(material); }
        document.select(materials.id); ui.revealAnchor("block:" + materials.id);
    }
    private static final class BlockDrag {
        final DocumentDraft owner;
        final String id;
        BlockDrag(DocumentDraft owner, String id) { this.owner = owner; this.id = id; }
    }
}
