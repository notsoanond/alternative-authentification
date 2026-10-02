package one.ggsky.alternativeauth.mixin;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.response.Response;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.net.URL;

@Mixin(YggdrasilAuthenticationService.class)
public interface AuthenticationServiceAccessor {
    @Invoker
    <T extends Response> T callMakeRequest(URL url, Object input, Class<T> classOfT, @Nullable String authentication)
            throws AuthenticationException;
}
