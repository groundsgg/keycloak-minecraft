package gg.grounds.keycloak.minecraft;

import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig;
import org.keycloak.models.IdentityProviderModel;

/**
 * Configuration for the Minecraft Identity Provider.
 * Extends OAuth2IdentityProviderConfig to include Microsoft OAuth2 settings.
 */
public class MinecraftIdentityProviderConfig extends OAuth2IdentityProviderConfig {

    public MinecraftIdentityProviderConfig(IdentityProviderModel model) {
        super(model);
    }

    public MinecraftIdentityProviderConfig() {
        super();
    }

    /**
     * Microsoft OAuth2 Authorization URL.
     */
    @Override
    public String getAuthorizationUrl() {
        return "https://login.live.com/oauth20_authorize.srf";
    }

    /**
     * Microsoft OAuth2 Token URL.
     */
    @Override
    public String getTokenUrl() {
        return "https://login.live.com/oauth20_token.srf";
    }

    /**
     * Default scopes required for Xbox Live authentication.
     */
    @Override
    public String getDefaultScope() {
        return "XboxLive.signin offline_access";
    }
}

