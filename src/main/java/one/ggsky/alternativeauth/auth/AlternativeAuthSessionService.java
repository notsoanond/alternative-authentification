package one.ggsky.alternativeauth.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.HttpAuthenticationService;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilEnvironment;
import one.ggsky.alternativeauth.config.AlternativeAuthConfig;
import one.ggsky.alternativeauth.config.AlternativeAuthProvider;
import one.ggsky.alternativeauth.config.AlternativeAuthConfigManager;
import one.ggsky.alternativeauth.logger.AlternativeAuthLogger;
import one.ggsky.alternativeauth.logger.AlternativeAuthLoggerManager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Delegating Authlib session service. It leaves every Yggdrasil operation alone
 * except hasJoinedServer(), which is the call used by the dedicated server to
 * validate an incoming player session.
 */
public final class AlternativeAuthSessionService extends YggdrasilMinecraftSessionService {
    private final AlternativeAuthLogger logger;
    private final AlternativeAuthConfig config;

    public AlternativeAuthSessionService(YggdrasilAuthenticationService authenticationService) {
        super(authenticationService, YggdrasilEnvironment.PROD);
        this.logger = AlternativeAuthLoggerManager.getLogger();
        this.config = AlternativeAuthConfigManager.getConfig();
    }

    @Override
    public GameProfile hasJoinedServer(GameProfile user, String serverId, InetAddress address) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("username", user.getName());
        arguments.put("serverId", serverId);
        if (address != null) {
            arguments.put("ip", address.getHostAddress());
        }

        for (AlternativeAuthProvider provider : config.getProviders()) {
            logger.debug("Trying to authenticate player via " + provider.name());
            logger.debug("Using " + provider.getCheckUrl());

            try {
                URL url = HttpAuthenticationService.concatenateURL(
                        HttpAuthenticationService.constantURL(provider.getCheckUrl()),
                        HttpAuthenticationService.buildQuery(arguments)
                );

                JsonObject response = getJson(url);
                if (response == null || !response.has("id") || response.get("id").isJsonNull()) {
                    continue;
                }

                UUID id = UUID.fromString(normalizeUuid(response.get("id").getAsString()));
                GameProfile result = new GameProfile(id, user.getName());
                copyProperties(response, result);

                if (provider.getPropertyUrl() != null && response.has("properties")) {
                    String propertyUrl = java.text.MessageFormat.format(
                            provider.getPropertyUrl(), user.getName(), response.get("id").getAsString()
                    );
                    logger.debug("Found " + provider.name() + " property URL, fetching " + propertyUrl);
                    JsonObject propertyResponse = getJson(HttpAuthenticationService.constantURL(propertyUrl));
                    if (propertyResponse != null) {
                        copyProperties(propertyResponse, result);
                    }
                }

                logger.info("Authenticating player via " + provider.name());
                return result;
            } catch (Exception exception) {
                logger.debug("Provider " + provider.name() + " failed: " + exception.getClass().getSimpleName()
                        + ": " + String.valueOf(exception.getMessage()));
            }
        }

        logger.debug("No configured alternative authentication provider accepted " + user.getName());
        return null;
    }

    private static JsonObject getJson(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "AlternativeAuthentication/1.1.1");

        int status = connection.getResponseCode();
        if (status == HttpURLConnection.HTTP_NO_CONTENT || status == HttpURLConnection.HTTP_NOT_FOUND) {
            return null;
        }
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " from " + url);
        }

        try (var input = connection.getInputStream()) {
            String body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) return null;
            JsonElement element = JsonParser.parseString(body);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } finally {
            connection.disconnect();
        }
    }

    private static void copyProperties(JsonObject response, GameProfile profile) {
        if (!response.has("properties") || !response.get("properties").isJsonArray()) return;
        JsonArray properties = response.getAsJsonArray("properties");
        for (JsonElement element : properties) {
            if (!element.isJsonObject()) continue;
            JsonObject property = element.getAsJsonObject();
            if (!property.has("name") || !property.has("value")) continue;
            String name = property.get("name").getAsString();
            String value = property.get("value").getAsString();
            if (property.has("signature") && !property.get("signature").isJsonNull()) {
                profile.getProperties().put(name, new Property(name, value, property.get("signature").getAsString()));
            } else {
                profile.getProperties().put(name, new Property(name, value));
            }
        }
    }

    private static String normalizeUuid(String value) {
        String hex = value.replace("-", "");
        if (hex.length() != 32) throw new IllegalArgumentException("Invalid profile UUID: " + value);
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                + hex.substring(16, 20) + "-" + hex.substring(20);
    }
}
