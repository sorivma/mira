package mira.integration.agenttools.forge.client;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.minecraftagenttools.gamebridge.GameBridge;
import java.util.UUID;
import mira.integration.agenttools.contract.MiraTools;
import mira.integration.agenttools.forge.CommonProxy;
import mira.integration.agenttools.tasks.MiraTaskService;
import net.minecraftforge.common.config.Configuration;

public final class ClientProxy extends CommonProxy {
    private int port;
    private String token;
    private GameBridge bridge;
    @Override public void preInit(FMLPreInitializationEvent event) {
        // The mod ID makes this filename discoverable by the neighboring MCP host.
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        port = config.getInt("port", "bridge", 25585, 1024, 65535, "Loopback-only Mira bridge port");
        token = config.getString("token", "bridge", UUID.randomUUID().toString() + UUID.randomUUID().toString(),
            "Private bridge credential. External stdio hosts use MAT_MIRA_PORT and MAT_MIRA_TOKEN.");
        config.save();
        if (token.length() < 32) throw new IllegalArgumentException("Mira bridge token must contain at least 32 characters");
    }
    @Override public void start() throws java.io.IOException {
        MiraTaskService service = new MiraTaskService(new MinecraftTaskBackend());
        bridge = new GameBridge(port, token, MiraTools::validate, service::call);
        FMLCommonHandler.instance().bus().register(this);
        Runtime.getRuntime().addShutdownHook(new Thread(bridge::close, "mira-bridge-shutdown"));
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && bridge != null) bridge.tick();
    }
}
