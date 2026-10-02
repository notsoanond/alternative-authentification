package one.ggsky.alternativeauth.mixin;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.HttpAuthenticationService;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.yggdrasil.ProfileNotFoundException;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilGameProfileRepository;
import com.mojang.authlib.yggdrasil.response.ProfileSearchResultsResponse;
import one.ggsky.alternativeauth.config.*;
import one.ggsky.alternativeauth.logger.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.net.URL;
import java.util.*;

@Mixin(YggdrasilGameProfileRepository.class)
public class FindProfilesByNamesMixin {
    @Shadow private YggdrasilAuthenticationService authenticationService;

    private final AlternativeAuthLogger LOGGER = AlternativeAuthLoggerManager.getLogger();
    private final AlternativeAuthConfig CONFIG = AlternativeAuthConfigManager.getConfig();

    private static final int ENTRIES_PER_PAGE = 2;
    private static final int MAX_FAIL_COUNT = 3;
    private static final int DELAY_BETWEEN_PAGES = 100;
    private static final int DELAY_BETWEEN_FAILURES = 750;

    @Inject(method = "findProfilesByNames", at = @At("HEAD"), remap = false, cancellable = true)
    private void alternativeAuth$findProfilesByNames(
            String[] names, Agent agent, ProfileLookupCallback callback, CallbackInfo ci
    ) {
        Set<String> criteria = Sets.newHashSet();
        for (String name : names) {
            if (!Strings.isNullOrEmpty(name)) criteria.add(name.toLowerCase(Locale.ROOT));
        }

        for (List<String> request : Iterables.partition(criteria, ENTRIES_PER_PAGE)) {
            int failCount = 0;
            boolean failed;
            do {
                failed = false;
                try {
                    ProfileSearchResultsResponse response = null;

                    for (AlternativeAuthProvider provider : CONFIG.getProviders()) {
                        URL url = HttpAuthenticationService.constantURL(provider.getProfilesUrl());
                        response = ((AuthenticationServiceAccessor) authenticationService)
                                .callMakeRequest(url, request, ProfileSearchResultsResponse.class, null);

                        if (response != null && response.getProfiles().length != 0) break;
                    }

                    if (response == null) {
                        throw new AuthenticationException("No authentication provider returned a response");
                    }

                    Set<String> missing = Sets.newHashSet(request);
                    for (GameProfile profile : response.getProfiles()) {
                        missing.remove(profile.getName().toLowerCase(Locale.ROOT));
                        callback.onProfileLookupSucceeded(profile);
                    }

                    for (String name : missing) {
                        callback.onProfileLookupFailed(
                                new GameProfile(null, name),
                                new ProfileNotFoundException("Server did not find the requested profile")
                        );
                    }

                    failCount = 0;
                    try {
                        Thread.sleep(DELAY_BETWEEN_PAGES);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                } catch (AuthenticationException e) {
                    failCount++;
                    if (failCount == MAX_FAIL_COUNT) {
                        for (String name : request) {
                            callback.onProfileLookupFailed(new GameProfile(null, name), e);
                        }
                    } else {
                        try {
                            Thread.sleep(DELAY_BETWEEN_FAILURES);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        failed = true;
                    }
                }
            } while (failed);
        }
        ci.cancel();
    }
}
