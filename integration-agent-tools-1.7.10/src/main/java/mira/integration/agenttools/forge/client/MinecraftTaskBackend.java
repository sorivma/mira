package mira.integration.agenttools.forge.client;

import static dev.minecraftagenttools.protocol.data.Data.map;
import java.util.Map;
import java.util.function.BiConsumer;
import mira.core.jira.Model.Command;
import mira.core.jira.Model.State;
import mira.integration.agenttools.tasks.MiraTaskService;
import mira.platform.forge1710.client.workspace.MiraClientAccess;

final class MinecraftTaskBackend implements MiraTaskService.Backend {
    @Override public Map<String, Object> scope() {
        MiraClientAccess.Scope scope = MiraClientAccess.scope();
        return map("session", scope.session, "dimension", scope.dimension, "ready", scope.ready,
            "player", scope.player, "status", scope.status, "revision", scope.revision);
    }
    @Override public State snapshot(String session, int dimension) { return MiraClientAccess.snapshot(session, dimension); }
    @Override public void submit(String session, int dimension, Command command, BiConsumer<String, String> complete) {
        MiraClientAccess.submit(session, dimension, command, reply -> complete.accept(reply.result, reply.message));
    }
}
