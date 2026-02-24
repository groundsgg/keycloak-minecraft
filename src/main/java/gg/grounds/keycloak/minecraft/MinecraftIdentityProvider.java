package gg.grounds.keycloak.minecraft;

import gg.grounds.keycloak.minecraft.api.MicrosoftAuthApi;
import gg.grounds.keycloak.minecraft.api.MinecraftApi;
import gg.grounds.keycloak.minecraft.api.XboxAuthApi;
import org.jboss.logging.Logger;
import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider;
import org.keycloak.broker.provider.AuthenticationRequest;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.events.EventBuilder;
import org.keycloak.http.simple.SimpleHttpRequest;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import jakarta.ws.rs.core.UriBuilder;
import java.io.IOException;

/**
 * Identity Provider that authenticates users via Microsoft/Xbox OAuth2
 * and retrieves their Minecraft username.
 */
public class MinecraftIdentityProvider extends AbstractOAuth2IdentityProvider<MinecraftIdentityProviderConfig> {

    private static final Logger logger = Logger.getLogger(MinecraftIdentityProvider.class);
    public static final String PROVIDER_ID = "minecraft";

    private final MicrosoftAuthApi microsoftAuthApi;
    private final XboxAuthApi xboxAuthApi;
    private final MinecraftApi minecraftApi;

    public MinecraftIdentityProvider(KeycloakSession session, MinecraftIdentityProviderConfig config) {
        super(session, config);
        this.microsoftAuthApi = new MicrosoftAuthApi();
        this.xboxAuthApi = new XboxAuthApi();
        this.minecraftApi = new MinecraftApi();
    }

    @Override
    protected String getDefaultScopes() {
        return getConfig().getDefaultScope();
    }

    @Override
    protected boolean supportsExternalExchange() {
        return true;
    }

    @Override
    protected BrokeredIdentityContext extractIdentityFromProfile(EventBuilder event, com.fasterxml.jackson.databind.JsonNode profile) {
        // This method is not used in our flow since we override doGetFederatedIdentity
        String id = profile.has("id") ? profile.get("id").asText() : null;
        String username = profile.has("name") ? profile.get("name").asText() : null;
        
        BrokeredIdentityContext user = new BrokeredIdentityContext(id, getConfig());
        user.setUsername(username);

        return user;
    }

    @Override
    protected BrokeredIdentityContext doGetFederatedIdentity(String accessToken) {
        try {
            logger.info("Starting Minecraft authentication flow");

            // Step 1: Authenticate with Xbox Live using Microsoft access token
            logger.debug("Authenticating with Xbox Live...");
            XboxAuthApi.XboxAuthResponse xboxResponse = xboxAuthApi.authenticateWithXbox(accessToken);
            String xboxUserToken = xboxResponse.getToken();
            String userHash = xboxResponse.getUserHash();
            logger.debugf("Xbox authentication successful, user hash: %s", userHash);

            // Step 2: Get XSTS token for Minecraft services
            logger.debug("Obtaining XSTS token...");
            XboxAuthApi.XboxAuthResponse xstsResponse;
            try {
                xstsResponse = xboxAuthApi.obtainXstsToken(xboxUserToken);
            } catch (XboxAuthApi.XboxAuthException e) {
                // User-friendly error handling for Xbox errors
                logger.warnf("Xbox XSTS authentication failed: %s", e.getMessage());
                throw new IdentityBrokerException(e.getMessage(), e);
            }
            
            String xstsToken = xstsResponse.getToken();
            String xboxGamertag = xstsResponse.getGamertag();
            String xboxUserId = xstsResponse.getXboxUserId();
            logger.debugf("XSTS token obtained successfully, Gamertag: %s", xboxGamertag);

            // Step 3: Authenticate with Minecraft services
            logger.debug("Authenticating with Minecraft services...");
            MinecraftApi.MinecraftAuthResponse mcAuthResponse = minecraftApi.authenticateWithMinecraft(userHash, xstsToken);
            String minecraftAccessToken = mcAuthResponse.getAccessToken();
            logger.debug("Minecraft authentication successful");

            // Step 4: Try to get Minecraft Java Edition profile
            logger.debug("Fetching Minecraft profile...");
            try {
                MinecraftApi.MinecraftProfile profile = minecraftApi.getProfile(minecraftAccessToken);
                logger.infof("Minecraft Java Edition profile retrieved: %s (UUID: %s)", profile.getName(), profile.getFormattedUuid());

                // Create identity for Java Edition user
                BrokeredIdentityContext identity = new BrokeredIdentityContext(profile.getFormattedUuid(), getConfig());
                identity.setUsername(profile.getName());
                identity.setBrokerUserId(profile.getFormattedUuid());
                
                // Store attributes for Java Edition
                identity.setUserAttribute("minecraft_uuid", profile.getId());
                identity.setUserAttribute("minecraft_username", profile.getName());
                identity.setUserAttribute("minecraft_edition", "java");
                identity.setUserAttribute("xbox_gamertag", xboxGamertag);
                if (xboxUserId != null) {
                    identity.setUserAttribute("xbox_user_id", xboxUserId);
                }

                return identity;

            } catch (MinecraftApi.MinecraftNotOwnedException e) {
                // Fallback: User has Bedrock Edition only, use Xbox Gamertag
                logger.infof("User does not own Java Edition, falling back to Xbox Gamertag: %s", xboxGamertag);

                if (xboxGamertag == null || xboxGamertag.isBlank()) {
                    throw new IdentityBrokerException("Could not retrieve Xbox Gamertag for Bedrock user");
                }

                // Use Xbox User ID as unique identifier, fallback to gamertag hash if not available
                String uniqueId = xboxUserId != null ? "xbox-" + xboxUserId : "xbox-" + xboxGamertag.hashCode();
                
                BrokeredIdentityContext identity = new BrokeredIdentityContext(uniqueId, getConfig());
                identity.setUsername(xboxGamertag);
                identity.setBrokerUserId(uniqueId);
                
                // Store attributes for Bedrock Edition
                identity.setUserAttribute("minecraft_username", xboxGamertag);
                identity.setUserAttribute("minecraft_edition", "bedrock");
                identity.setUserAttribute("xbox_gamertag", xboxGamertag);
                if (xboxUserId != null) {
                    identity.setUserAttribute("xbox_user_id", xboxUserId);
                }

                return identity;
            }

        } catch (XboxAuthApi.XboxAuthException e) {
            // This is a user-friendly message, pass it through
            logger.warnf("Xbox authentication failed: %s", e.getMessage());
            throw new IdentityBrokerException(e.getMessage(), e);
        } catch (IOException | InterruptedException e) {
            logger.error("Failed to authenticate with Minecraft services", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IdentityBrokerException("Minecraft authentication failed. Please try again.", e);
        }
    }

    @Override
    protected String getProfileEndpointForValidation(EventBuilder event) {
        // We don't use a standard profile endpoint since we call multiple APIs
        return null;
    }

    @Override
    protected UriBuilder createAuthorizationUrl(AuthenticationRequest request) {
        UriBuilder builder = super.createAuthorizationUrl(request);
        String overrideUri = System.getenv("MINECRAFT_IDP_REDIRECT_URI");
        if (overrideUri != null && !overrideUri.isBlank()) {
            builder.replaceQueryParam(OAUTH2_PARAMETER_REDIRECT_URI, overrideUri);
        }
        return builder;
    }

    @Override
    public Object callback(RealmModel realm, org.keycloak.broker.provider.UserAuthenticationIdentityProvider.AuthenticationCallback callback, EventBuilder event) {
        return new MinecraftEndpoint(callback, realm, event, this);
    }

    protected static class MinecraftEndpoint extends Endpoint {
        public MinecraftEndpoint(org.keycloak.broker.provider.UserAuthenticationIdentityProvider.AuthenticationCallback callback,
                                  RealmModel realm, EventBuilder event, AbstractOAuth2IdentityProvider<?> provider) {
            super(callback, realm, event, provider);
        }

        @Override
        public SimpleHttpRequest generateTokenRequest(String authorizationCode) {
            SimpleHttpRequest tokenRequest = super.generateTokenRequest(authorizationCode);
            String overrideUri = System.getenv("MINECRAFT_IDP_REDIRECT_URI");
            if (overrideUri != null && !overrideUri.isBlank()) {
                tokenRequest.param(OAUTH2_PARAMETER_REDIRECT_URI, overrideUri);
            }
            return tokenRequest;
        }
    }

    @Override
    public SimpleHttpRequest authenticateTokenRequest(SimpleHttpRequest tokenRequest) {
        // Use POST body for client credentials (not Basic Auth)
        return tokenRequest
                .param("client_id", getConfig().getClientId())
                .param("client_secret", getConfig().getClientSecret());
    }
}

