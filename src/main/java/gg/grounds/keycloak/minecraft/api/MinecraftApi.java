package gg.grounds.keycloak.minecraft.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Handles Minecraft Services API calls to get player profile information.
 */
public class MinecraftApi {

    private static final Logger logger = Logger.getLogger(MinecraftApi.class);
    private static final String MINECRAFT_AUTH_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MINECRAFT_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public MinecraftApi() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    /**
     * Authenticates with Minecraft services using Xbox XSTS token.
     *
     * @param userHash  The user hash from Xbox authentication
     * @param xstsToken The XSTS token
     * @return MinecraftAuthResponse containing the Minecraft access token
     * @throws IOException          If the request fails
     * @throws InterruptedException If the request is interrupted
     */
    public MinecraftAuthResponse authenticateWithMinecraft(String userHash, String xstsToken)
            throws IOException, InterruptedException {

        Map<String, String> requestBody = Map.of(
                "identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken
        );

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MINECRAFT_AUTH_URL))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.errorf("Minecraft authentication failed: %s", response.body());
            throw new IOException("Minecraft authentication failed with status: " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), MinecraftAuthResponse.class);
    }

    /**
     * Gets the Minecraft profile (username and UUID) using the Minecraft access token.
     *
     * @param minecraftAccessToken The access token from authenticateWithMinecraft
     * @return MinecraftProfile containing username and UUID
     * @throws IOException          If the request fails
     * @throws InterruptedException If the request is interrupted
     */
    public MinecraftProfile getProfile(String minecraftAccessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MINECRAFT_PROFILE_URL))
                .header("Authorization", "Bearer " + minecraftAccessToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            logger.warn("User does not own Minecraft");
            throw new MinecraftNotOwnedException("The user does not own Minecraft Java Edition");
        }

        if (response.statusCode() != 200) {
            logger.errorf("Minecraft profile request failed: %s", response.body());
            throw new IOException("Minecraft profile request failed with status: " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), MinecraftProfile.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MinecraftAuthResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("token_type")
        private String tokenType;

        @JsonProperty("expires_in")
        private int expiresIn;

        @JsonProperty("username")
        private String username;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getTokenType() {
            return tokenType;
        }

        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }

        public int getExpiresIn() {
            return expiresIn;
        }

        public void setExpiresIn(int expiresIn) {
            this.expiresIn = expiresIn;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MinecraftProfile {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("skins")
        private List<Skin> skins;

        @JsonProperty("capes")
        private List<Cape> capes;

        /**
         * Gets the UUID in standard format (with hyphens).
         *
         * @return The formatted UUID
         */
        public String getFormattedUuid() {
            if (id == null || id.length() != 32) {
                return id;
            }
            return id.substring(0, 8) + "-" +
                    id.substring(8, 12) + "-" +
                    id.substring(12, 16) + "-" +
                    id.substring(16, 20) + "-" +
                    id.substring(20);
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Skin> getSkins() {
            return skins;
        }

        public void setSkins(List<Skin> skins) {
            this.skins = skins;
        }

        public List<Cape> getCapes() {
            return capes;
        }

        public void setCapes(List<Cape> capes) {
            this.capes = capes;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Skin {
        @JsonProperty("id")
        private String id;

        @JsonProperty("state")
        private String state;

        @JsonProperty("url")
        private String url;

        @JsonProperty("variant")
        private String variant;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getVariant() {
            return variant;
        }

        public void setVariant(String variant) {
            this.variant = variant;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cape {
        @JsonProperty("id")
        private String id;

        @JsonProperty("state")
        private String state;

        @JsonProperty("url")
        private String url;

        @JsonProperty("alias")
        private String alias;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }
    }

    /**
     * Exception thrown when a user doesn't own Minecraft Java Edition.
     */
    public static class MinecraftNotOwnedException extends IOException {
        public MinecraftNotOwnedException(String message) {
            super(message);
        }
    }
}

