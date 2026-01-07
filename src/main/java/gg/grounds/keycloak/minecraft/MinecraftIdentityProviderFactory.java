package gg.grounds.keycloak.minecraft;

import org.keycloak.broker.provider.AbstractIdentityProviderFactory;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

/**
 * Factory for creating Minecraft Identity Provider instances.
 */
public class MinecraftIdentityProviderFactory
        extends AbstractIdentityProviderFactory<MinecraftIdentityProvider> {

    public static final String PROVIDER_ID = "minecraft";
    public static final String PROVIDER_NAME = "Minecraft";

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public MinecraftIdentityProvider create(KeycloakSession session, IdentityProviderModel model) {
        return new MinecraftIdentityProvider(session, new MinecraftIdentityProviderConfig(model));
    }

    @Override
    public MinecraftIdentityProviderConfig createConfig() {
        return new MinecraftIdentityProviderConfig();
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name("clientId")
                .label("Client ID")
                .helpText("The Client ID from your Microsoft Azure App Registration")
                .type(ProviderConfigProperty.STRING_TYPE)
                .add()
                .property()
                .name("clientSecret")
                .label("Client Secret")
                .helpText("The Client Secret from your Microsoft Azure App Registration")
                .type(ProviderConfigProperty.PASSWORD)
                .secret(true)
                .add()
                .build();
    }
}

