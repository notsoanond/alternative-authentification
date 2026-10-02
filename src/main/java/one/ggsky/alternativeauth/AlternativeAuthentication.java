package one.ggsky.alternativeauth;

import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import one.ggsky.alternativeauth.auth.AlternativeAuthSessionService;
import one.ggsky.alternativeauth.config.AlternativeAuthConfigManager;
import one.ggsky.alternativeauth.logger.AlternativeAuthLoggerManager;

import java.lang.reflect.Field;
import sun.misc.Unsafe;

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
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        try {
            installSessionService(server);
        } catch (Exception exception) {
            AlternativeAuthLoggerManager.getLogger().info(
                    "Failed to install Alternative Authentication session service: "
                            + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            throw new IllegalStateException("Alternative Authentication could not install its session service", exception);
        }
    }

    private static void installSessionService(MinecraftServer server) throws Exception {
        Field servicesField = findFieldContaining(server, "net.minecraft.server.Services");
        servicesField.setAccessible(true);
        Object services = servicesField.get(server);
        if (services == null) throw new IllegalStateException("MinecraftServer services field is null");

        Field sessionField = null;
        for (Field field : services.getClass().getDeclaredFields()) {
            if (YggdrasilMinecraftSessionService.class.isAssignableFrom(field.getType())
                    || com.mojang.authlib.minecraft.MinecraftSessionService.class.isAssignableFrom(field.getType())) {
                sessionField = field;
                break;
            }
        }
        if (sessionField == null) {
            throw new IllegalStateException("Could not locate MinecraftSessionService in Services");
        }

        sessionField.setAccessible(true);
        Object current = sessionField.get(services);
        if (!(current instanceof YggdrasilMinecraftSessionService yggdrasil)) {
            throw new IllegalStateException("Unexpected session service: " + current);
        }

        AlternativeAuthSessionService replacement =
                new AlternativeAuthSessionService(yggdrasil.getAuthenticationService());
        setFinalField(services, sessionField, replacement);
        AlternativeAuthLoggerManager.getLogger().info("Installed Alternative Authentication session service");
    }

    private static void setFinalField(Object target, Field field, Object value) throws Exception {
        Unsafe unsafe = getUnsafe();
        long offset = unsafe.objectFieldOffset(field);
        unsafe.putObject(target, offset, value);
    }

    @SuppressWarnings("unchecked")
    private static Unsafe getUnsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static Field findFieldContaining(Object target, String className) throws IllegalAccessException {
        Class<?> type = target.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType().getName().equals(className)) return field;
            }
            type = type.getSuperclass();
        }
        throw new IllegalStateException("Could not locate " + className + " on " + target.getClass().getName());
    }
}
