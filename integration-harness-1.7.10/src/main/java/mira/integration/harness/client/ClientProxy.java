package mira.integration.harness.client;

import dev.minecraftharness.forge.HarnessWidgets;
import mira.integration.harness.CommonProxy;

public final class ClientProxy extends CommonProxy {
    @Override public void initialize() {
        if (HarnessWidgets.API_VERSION != 1) throw new IllegalStateException("Mira requires Harness widget API v1");
        LiveTasks backend = new LiveTasks();
        HarnessWidgets.register("mira:tasks-v1", TasksWidget.instructions(), payload -> TasksWidget.parse(payload, backend));
    }
}
