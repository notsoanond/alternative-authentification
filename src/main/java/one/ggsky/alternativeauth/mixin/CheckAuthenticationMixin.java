package one.ggsky.alternativeauth.mixin;

import com.mojang.authlib.AuthenticationService;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.HttpAuthenticationService;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import com.mojang.authlib.yggdrasil.response.HasJoinedMinecraftServerResponse;
import one.ggsky.alternativeauth.config.*;
import one.ggsky.alternativeauth.logger.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.net.InetAddress;
import java.net.URL;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

@Mixin(YggdrasilMinecraftSessionService.class)
public abstract class CheckAuthenticationMixin {
    @Shadow public abstract AuthenticationService getAuthenticationService();

    private final AlternativeAuthLogger LOGGER = AlternativeAuthLoggerManager.getLogger();
    private final AlternativeAuthConfig CONFIG = AlternativeAuthConfigManager.getConfig();

    @Inject(method = "hasJoinedServer", at = @At("HEAD"), remap = false, cancellable = true)
    private void alternativeAuth$checkAuthentication(
            GameProfile user, String serverId, InetAddress address,
            CallbackInfoReturnable<GameProfile> cir
    ) throws AuthenticationUnavailableException {
        AuthenticationServiceAccessor authenticationService =
                (AuthenticationServiceAccessor) getAuthenticationService();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("username", user.getName());
        arguments.put("serverId", serverId);
        if (address != null) arguments.put("ip", address.getHostAddress());

        for (AlternativeAuthProvider provider : CONFIG.getProviders()) {
            LOGGER.debug("Trying to authenticate player via " + provider.name());
            LOGGER.debug("Using " + provider.getCheckUrl());

            URL url = HttpAuthenticationService.concatenateURL(
                    HttpAuthenticationService.constantURL(provider.getCheckUrl()),
                    HttpAuthenticationService.buildQuery(arguments)
            );

            try {
                HasJoinedMinecraftServerResponse response =
                        authenticationService.callMakeRequest(
                                url, null, HasJoinedMinecraftServerResponse.class, null
                        );

                if (response != null && response.getId() != null) {
                    GameProfile result = new GameProfile(response.getId(), user.getName());

                    if (response.getProperties() != null) {
                        PropertyMap properties;
                        if (provider.getPropertyUrl() != null) {
                            LOGGER.debug(MessageFormat.format(
                                    "Found {0} property URL, fetching {1}",
                                    provider.name(),
                                    MessageFormat.format(
                                            provider.getPropertyUrl(),
                                            user.getName(), response.getId()
                                    )
                            ));

                            URL propertyUrl = HttpAuthenticationService.concatenateURL(
                                    HttpAuthenticationService.constantURL(
                                            MessageFormat.format(
                                                    provider.getPropertyUrl(),
                                                    user.getName(), response.getId()
                                            )
                                    ), null
                            );

                            HasJoinedMinecraftServerResponse propertyResponse =
                                    authenticationService.callMakeRequest(
                                            propertyUrl, null,
                                            HasJoinedMinecraftServerResponse.class, null
                                    );

                            properties = propertyResponse != null
                                    ? propertyResponse.getProperties()
                                    : response.getProperties();
                        } else {
                            properties = response.getProperties();
                        }
                        result.getProperties().putAll(properties);
                    }

                    LOGGER.info("Authenticating player via " + provider.name());
                    cir.setReturnValue(result);
                    return;
                }
            } catch (AuthenticationUnavailableException exception) {
                throw exception;
            } catch (AuthenticationException ignored) {
                cir.setReturnValue(null);
                return;
            }
        }

        cir.setReturnValue(null);
    }
}
