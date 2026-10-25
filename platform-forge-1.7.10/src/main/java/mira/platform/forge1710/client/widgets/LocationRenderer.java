package mira.platform.forge1710.client.widgets;

import mira.core.taskbody.LocationWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.client.gui.GuiScreen;
import mira.platform.forge1710.client.ui.Ui;
import java.util.function.IntSupplier;
import java.util.function.IntConsumer;
import static mira.platform.forge1710.client.ui.Ui.*;

final class LocationRenderer implements WidgetRenderer<LocationWidget> {
    public Class<LocationWidget> type() { return LocationWidget.class; }
    public String name() { return "Location"; }
    public LocationWidget create() { LocationWidget value = new LocationWidget(); locate(value); return value; }
    private static void locate(LocationWidget value) {
        Minecraft minecraft = Minecraft.getMinecraft();
        value.dimension = minecraft.thePlayer.dimension;
        MovingObjectPosition target = minecraft.objectMouseOver;
        if (target != null && target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            value.x = target.blockX; value.y = target.blockY; value.z = target.blockZ;
        } else {
            value.x = (int) Math.floor(minecraft.thePlayer.posX); value.y = (int) Math.floor(minecraft.thePlayer.posY); value.z = (int) Math.floor(minecraft.thePlayer.posZ);
        }
    }
    public View build(LocationWidget widget, WidgetContext context) {
        String coordinates = "Dim " + widget.dimension + ": " + widget.x + ", " + widget.y + ", " + widget.z;
        Ui.Node content = column(4);
        if (context.editing) content.add(row(6).height(33).add(
            coordinate(context, widget.id + "/dimension", "Dimension", () -> widget.dimension, value -> widget.dimension = value),
            coordinate(context, widget.id + "/x", "X", () -> widget.x, value -> widget.x = value)),
            row(6).height(33).add(
                coordinate(context, widget.id + "/y", "Y", () -> widget.y, value -> widget.y = value),
                coordinate(context, widget.id + "/z", "Z", () -> widget.z, value -> widget.z = value)));
        else content.add(text(coordinates).height(12).hint(coordinates));
        Ui.Node actions = row(4).height(20).add(
            button("Copy", () -> GuiScreen.setClipboardString(coordinates)),
            button("Use target", () -> context.change(() -> locate(widget))).hint("Use the block you are looking at", "Falls back to your current position"));
        if (context.editing) content.add(actions);
        else content.add(button("Copy coordinates", () -> GuiScreen.setClipboardString(coordinates)).height(20).width(108));
        return new View(content, context.editing ? 94 : 36);
    }
    private Ui.Node coordinate(WidgetContext context, Object key, String label, IntSupplier read, IntConsumer write) {
        return column(3).height(33).add(text(label, MUTED).height(10),
            Ui.field(context.input(key, 11, label, () -> Integer.toString(read.getAsInt()), value -> {
                try { write.accept(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
            }).preserveEditing()).hint(label, "Whole number; invalid input keeps the previous value"));
    }
}
