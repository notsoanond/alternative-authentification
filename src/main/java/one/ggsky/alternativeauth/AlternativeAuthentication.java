package one.ggsky.alternativeauth;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import one.ggsky.alternativeauth.config.AlternativeAuthConfigManager;
import one.ggsky.alternativeauth.logger.AlternativeAuthLoggerManager;

@Mod(AlternativeAuthentication.MOD_ID)
public class AlternativeAuthentication {
    public static final String MOD_ID = "alternative_auth";

    public AlternativeAuthentication() {
        AlternativeAuthConfigManager.loadConfig();
        AlternativeAuthLoggerManager.configureLogger(
                LogManager.getLogger(MOD_ID),
                AlternativeAuthConfigManager.getConfig().isDebuggerEnabled()
        );

        AlternativeAuthLoggerManager.getLogger().info(
                "Alternative Authentication is now powering your Minecraft server!"
        );
    }
}
