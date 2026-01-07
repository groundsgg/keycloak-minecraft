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
 * Handles Xbox Live authentication to obtain Xbox User Token and XSTS Token.
 */
public class XboxAuthApi {

    private static final Logger logger = Logger.getLogger(XboxAuthApi.class);
    private static final String XBOX_USER_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XBOX_XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public XboxAuthApi() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    /**
     * Authenticates with Xbox Live using a Microsoft access token.
     *
     * @param microsoftAccessToken The access token from Microsoft OAuth2
     * @return XboxAuthResponse containing the Xbox User Token
     * @throws IOException          If the request fails
     * @throws InterruptedException If the request is interrupted
     */
    public XboxAuthResponse authenticateWithXbox(String microsoftAccessToken) throws IOException, InterruptedException {
        Map<String, Object> requestBody = Map.of(
                "Properties", Map.of(
                        "AuthMethod", "RPS",
                        "SiteName", "user.auth.xboxlive.com",
                        "RpsTicket", "d=" + microsoftAccessToken
                ),
                "RelyingParty", "http://auth.xboxlive.com",
                "TokenType", "JWT"
        );

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(XBOX_USER_AUTH_URL))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.errorf("Xbox authentication failed: %s", response.body());
            throw new IOException("Xbox authentication failed with status: " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), XboxAuthResponse.class);
    }

    /**
     * Obtains an XSTS token for Minecraft services.
     *
     * @param xboxUserToken The Xbox User Token from authenticateWithXbox
     * @return XboxAuthResponse containing the XSTS token
     * @throws IOException          If the request fails
     * @throws InterruptedException If the request is interrupted
     */
    public XboxAuthResponse obtainXstsToken(String xboxUserToken) throws IOException, InterruptedException {
        Map<String, Object> requestBody = Map.of(
                "Properties", Map.of(
                        "SandboxId", "RETAIL",
                        "UserTokens", List.of(xboxUserToken)
                ),
                "RelyingParty", "rp://api.minecraftservices.com/",
                "TokenType", "JWT"
        );

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(XBOX_XSTS_AUTH_URL))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.warnf("XSTS token request failed with status %d: %s", response.statusCode(), response.body());
            
            // Check for specific Xbox Live errors
            if (response.statusCode() == 401) {
                XstsErrorResponse error = objectMapper.readValue(response.body(), XstsErrorResponse.class);
                throw new XboxAuthException(error.getXErr(), error.getRedirect());
            }
            
            throw new IOException("XSTS token request failed with status: " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), XboxAuthResponse.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class XboxAuthResponse {
        @JsonProperty("Token")
        private String token;

        @JsonProperty("DisplayClaims")
        private DisplayClaims displayClaims;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public DisplayClaims getDisplayClaims() {
            return displayClaims;
        }

        public void setDisplayClaims(DisplayClaims displayClaims) {
            this.displayClaims = displayClaims;
        }

        /**
         * Gets the user hash (uhs) from the display claims.
         *
         * @return The user hash or null if not available
         */
        public String getUserHash() {
            if (displayClaims != null && displayClaims.getXui() != null && !displayClaims.getXui().isEmpty()) {
                return displayClaims.getXui().get(0).getUhs();
            }
            return null;
        }

        /**
         * Gets the Xbox Gamertag from the display claims.
         *
         * @return The gamertag or null if not available
         */
        public String getGamertag() {
            if (displayClaims != null && displayClaims.getXui() != null && !displayClaims.getXui().isEmpty()) {
                return displayClaims.getXui().get(0).getGtg();
            }
            return null;
        }

        /**
         * Gets the Xbox User ID (xid) from the display claims.
         *
         * @return The Xbox User ID or null if not available
         */
        public String getXboxUserId() {
            if (displayClaims != null && displayClaims.getXui() != null && !displayClaims.getXui().isEmpty()) {
                return displayClaims.getXui().get(0).getXid();
            }
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DisplayClaims {
        @JsonProperty("xui")
        private List<XuiClaim> xui;

        public List<XuiClaim> getXui() {
            return xui;
        }

        public void setXui(List<XuiClaim> xui) {
            this.xui = xui;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class XuiClaim {
        @JsonProperty("uhs")
        private String uhs;

        @JsonProperty("gtg")
        private String gtg;

        @JsonProperty("xid")
        private String xid;

        public String getUhs() {
            return uhs;
        }

        public void setUhs(String uhs) {
            this.uhs = uhs;
        }

        public String getGtg() {
            return gtg;
        }

        public void setGtg(String gtg) {
            this.gtg = gtg;
        }

        public String getXid() {
            return xid;
        }

        public void setXid(String xid) {
            this.xid = xid;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class XstsErrorResponse {
        @JsonProperty("XErr")
        private long xErr;

        @JsonProperty("Message")
        private String message;

        @JsonProperty("Redirect")
        private String redirect;

        public long getXErr() {
            return xErr;
        }

        public void setXErr(long xErr) {
            this.xErr = xErr;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getRedirect() {
            return redirect;
        }

        public void setRedirect(String redirect) {
            this.redirect = redirect;
        }
    }

    /**
     * Exception for Xbox Live authentication errors with specific error codes.
     */
    public static class XboxAuthException extends IOException {
        private final long errorCode;
        private final String redirectUrl;

        public XboxAuthException(long errorCode, String redirectUrl) {
            super(getErrorMessage(errorCode));
            this.errorCode = errorCode;
            this.redirectUrl = redirectUrl;
        }

        public long getErrorCode() {
            return errorCode;
        }

        public String getRedirectUrl() {
            return redirectUrl;
        }

        /**
         * Returns a user-friendly error message based on the error code.
         */
        public static String getErrorMessage(long errorCode) {
            if (errorCode == 2148916233L) {
                return "This Microsoft account doesn't have an Xbox account. " +
                       "Please create an Xbox account first at xbox.com/live";
            } else if (errorCode == 2148916235L) {
                return "Xbox Live is not available in your country.";
            } else if (errorCode == 2148916236L || errorCode == 2148916237L) {
                return "This account requires adult verification (South Korea).";
            } else if (errorCode == 2148916238L) {
                return "This is a child account and needs to be added to a family.";
            } else {
                return "Xbox Live authentication failed (Error code: " + errorCode + ")";
            }
        }

        /**
         * Returns true if the user needs to create an Xbox account.
         */
        public boolean needsXboxAccount() {
            return errorCode == 2148916233L;
        }
    }
}

