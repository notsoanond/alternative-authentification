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

        java.lang.reflect.RecordComponent[] components = services.getClass().getRecordComponents();
        if (components == null) {
            throw new IllegalStateException("MinecraftServer Services is not a record");
        }

        Object[] arguments = new Object[components.length];
        Class<?>[] parameterTypes = new Class<?>[components.length];
        AlternativeAuthSessionService replacement = null;
        boolean replaced = false;

        for (int i = 0; i < components.length; i++) {
            java.lang.reflect.RecordComponent component = components[i];
            parameterTypes[i] = component.getType();
            Object value = component.getAccessor().invoke(services);

            if (!replaced && value instanceof YggdrasilMinecraftSessionService yggdrasil) {
                replacement = new AlternativeAuthSessionService(yggdrasil.getAuthenticationService());
                value = replacement;
                replaced = true;
            } else if (!replaced && value instanceof com.mojang.authlib.minecraft.MinecraftSessionService sessionService) {
                throw new IllegalStateException(
                        "Services contains a non-Yggdrasil MinecraftSessionService: "
                                + sessionService.getClass().getName()
                );
            }
            arguments[i] = value;
        }

        if (!replaced) {
            throw new IllegalStateException("Could not locate Yggdrasil MinecraftSessionService in Services record");
        }

        java.lang.reflect.Constructor<?> constructor = services.getClass().getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        Object replacementServices = constructor.newInstance(arguments);

        setFinalField(server, servicesField, replacementServices);
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
