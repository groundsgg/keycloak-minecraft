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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles Microsoft OAuth2 token exchange operations.
 */
public class MicrosoftAuthApi {

    private static final Logger logger = Logger.getLogger(MicrosoftAuthApi.class);
    private static final String TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public MicrosoftAuthApi() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    /**
     * Exchanges an authorization code for an access token.
     *
     * @param clientId     The Microsoft Azure App client ID
     * @param clientSecret The Microsoft Azure App client secret
     * @param code         The authorization code from OAuth2 callback
     * @param redirectUri  The redirect URI used in the authorization request
     * @return TokenResponse containing the access token
     * @throws IOException          If the request fails
     * @throws InterruptedException If the request is interrupted
     */
    public TokenResponse exchangeCodeForToken(String clientId, String clientSecret, String code, String redirectUri)
            throws IOException, InterruptedException {

        Map<String, String> params = Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code,
                "grant_type", "authorization_code",
                "redirect_uri", redirectUri
        );

        String body = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" +
                        URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.errorf("Microsoft token exchange failed: %s", response.body());
            throw new IOException("Microsoft token exchange failed with status: " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), TokenResponse.class);
    }

    /**
     * Refreshes an access token using a refresh token.
     *
     * @param clientId     The Microsoft Azure App client ID
     * @param clientSecret The Microsoft Azure App client secret
     * @param refreshToken The refresh token
     * @return TokenResponse containing the new access token
     * @throws IOException          If the request fails
     * @throws InterruptedException If the request is interrupted
     */
    public TokenResponse refreshToken(String clientId, String clientSecret, String refreshToken)
            throws IOException, InterruptedException {

        Map<String, String> params = Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "refresh_token", refreshToken,
                "grant_type", "refresh_token"
        );

        String body = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" +
                        URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.errorf("Microsoft token refresh failed: %s", response.body());
            throw new IOException("Microsoft token refresh failed with status: " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), TokenResponse.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("refresh_token")
        private String refreshToken;

        @JsonProperty("expires_in")
        private int expiresIn;

        @JsonProperty("token_type")
        private String tokenType;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public int getExpiresIn() {
            return expiresIn;
        }

        public void setExpiresIn(int expiresIn) {
            this.expiresIn = expiresIn;
        }

        public String getTokenType() {
            return tokenType;
        }

        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }
    }
}

