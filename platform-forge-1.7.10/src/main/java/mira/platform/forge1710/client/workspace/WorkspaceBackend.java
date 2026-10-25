package mira.platform.forge1710.client.workspace;

import java.util.function.Consumer;
import mira.core.jira.Model.Command;
import mira.core.jira.Model.State;
import mira.platform.forge1710.jira.DatabaseCodec.Reply;

/** Async transport seam; session and conflict handling do not depend on Minecraft. */
interface WorkspaceBackend {
    State state();
    String player();
    boolean ready();
    String status();
    void submit(Command command, Consumer<Reply> complete);
    void listen(Consumer<State> listener);
}
