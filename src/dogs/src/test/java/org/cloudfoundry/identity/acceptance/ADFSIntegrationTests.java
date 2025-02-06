package org.cloudfoundry.identity.acceptance;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Since Oct 2017, Pend ADFS to unblock uaa-acceptance-gcp pipeline")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DefaultAcceptanceTestConfig.class)
class ADFSIntegrationTests {

    private static final String AD_FS_SAML_FOR_IDATS = "ADFS SAML for IDaTS";

    @Value("${BASE_URL}")
    private String baseUrl;

    @Value("${PROTOCOL:https://}")
    private String protocol;

    @Value("${ADMIN_CLIENT_ID:admin}")
    private String adminClientId;

    @Value("${ADMIN_CLIENT_SECRET:adminsecret}")
    private String adminClientSecret;

    @Autowired
    private TestClient testClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private WebDriver webDriver;

    private String adminToken;
    private String baseUrlWithProtocol;

    private final String adfsOriginKey = "idats-adfs";

    private final String clientId = "test-client-" + UUID.randomUUID();
    private final String clientSecret = clientId + "-password";
    private String adfsMetadata = "";

    @BeforeEach
    void setUp() throws FileNotFoundException {
        String metadataFileName = "adfsmetadata.xml";
        ClassLoader classLoader = getClass().getClassLoader();
        File metadataFile = new File(classLoader.getResource(metadataFileName).getFile());
        adfsMetadata = new Scanner(metadataFile).useDelimiter("\\Z").next();

        adminToken = testClient.getClientAccessToken(adminClientId, adminClientSecret, "");
        baseUrlWithProtocol = protocol + baseUrl;
        System.out.println("Logging out from previous session.");
        webDriver.get(baseUrlWithProtocol + "/logout.do");
        System.out.println("Log out complete.");

        System.out.println("URL: " + baseUrlWithProtocol);
        Assumptions.assumeTrue(baseUrlWithProtocol.contains(".uaa-acceptance.cf-app.com"), "This test is against GCP environment");
    }

    @Test
    void gcpAdfs() {
        setupIdp(testClient, baseUrlWithProtocol, adminToken, adfsMetadata);
        testClient.createPasswordClient(adminToken, clientId, clientSecret);

        assertLoginFlow(baseUrlWithProtocol);
        assertCustomAttributeMappings(baseUrlWithProtocol, testClient);

        testClient.deleteClient(adminToken, clientId);

        webDriver.get(baseUrlWithProtocol + "/logout.do");
    }

    @Test
    void gcpAdfsNonSystemZone() {
        String zoneId = "idats";
        String zoneUrl = protocol + zoneId + "." + baseUrl;
        TestClient zoneClient = new TestClient(restTemplate, zoneUrl);

        String zoneAdminClientId = "test-client-" + UUID.randomUUID();
        String zoneAdminClientSecret = zoneAdminClientId + "-password";

        testClient.createZoneAdminClient(adminToken, zoneAdminClientId, zoneAdminClientSecret, zoneId);
        String zoneAdminToken = zoneClient.getClientAccessToken(zoneAdminClientId, zoneAdminClientSecret, "");

        setupIdp(zoneClient, zoneUrl, zoneAdminToken, adfsMetadata);
        zoneClient.createPasswordClient(zoneAdminToken, clientId, clientSecret);

        assertLoginFlow(zoneUrl);
        assertCustomAttributeMappings(zoneUrl, zoneClient);

        zoneClient.deleteClient(zoneAdminToken, clientId);
        zoneClient.deleteClient(zoneAdminToken, zoneAdminClientId);

        webDriver.get(zoneUrl + "/logout.do");
    }

    private void assertLoginFlow(String url) throws RuntimeException {
        //browser login flow
        webDriver.get(url + "/login");
        webDriver.findElement(By.xpath("//a[text()='" + AD_FS_SAML_FOR_IDATS + "']")).click();

        if (!webDriver.getCurrentUrl().contains(url)) {
            assertThat(webDriver.findElement(By.id("loginMessage")).getText()).contains("Sign in with your organizational account");

            webDriver.findElement(By.name("UserName")).clear();
            webDriver.findElement(By.name("UserName")).sendKeys("techuser1@adfs.cf-app.com");
            webDriver.findElement(By.name("Password")).sendKeys("Password01");
            webDriver.findElement(By.id("submitButton")).click();
        }
        assertThat(webDriver.findElement(By.cssSelector("h1")).getText()).contains("Where to?");
    }

    private void assertCustomAttributeMappings(String url, TestClient zoneClient) throws RuntimeException {
        webDriver.get(url + "/passcode");
        assertThat(webDriver.findElement(By.cssSelector("h1")).getText()).contains("Temporary Authentication Code");
        String passcode = webDriver.findElement(By.cssSelector("h2")).getText();
        System.out.println("Passcode: " + passcode);

        String passwordToken = zoneClient.getPasswordToken(clientId, clientSecret, passcode);
        Map<String, Object> userInfo = zoneClient.getUserInfo(passwordToken);
        Map<String, Object> userAttributes = (Map<String, Object>) userInfo.get("user_attributes");
        assertThat(userAttributes).containsEntry("email", List.of("techuser1@adfs.cf-app.com"))
                .containsEntry("fixedCustomAttributeToTestValue", List.of("microsoft"));
    }

    private void setupIdp(TestClient zoneClient, String zoneUrl, String adminToken, String idpMetadata) {
        List<Map> identityProviders = zoneClient.getIdentityProviders(zoneUrl, adminToken);

        Optional<Map> existingIdp = identityProviders.stream()
                .filter(entry -> adfsOriginKey.equals(entry.get("originKey")))
                .findFirst();

        Map<String, Object> idp = existingIdp.isPresent() ?
                zoneClient.updateIdentityProvider(zoneUrl, adminToken, (String) existingIdp.get().get("id"), getAdfsIDP(idpMetadata)) :
                zoneClient.createIdentityProvider(zoneUrl, adminToken, getAdfsIDP(idpMetadata));

        String adfsIdp = "Created IDP:%n\tid:%s%n\tname:%s%n\ttype:%s%n\torigin:%s%n\tactive:%s".formatted(
                idp.get("id"),
                idp.get("name"),
                idp.get("type"),
                idp.get("originKey"),
                idp.get("active")
        );
        System.out.println(adfsIdp);
    }

    private Map<String, Object> getAdfsIDP(String idpMetadata) {
        Map<String, Object> config = new HashMap<>();
        HashMap<String, String> attributeMappings = new HashMap<>();
        attributeMappings.put("user.attribute.email", "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress");
        attributeMappings.put("user.attribute.fixedCustomAttributeToTestValue", "http://schemas.microsoft.com/ws/2008/06/identity/claims/role");

        config.put("attributeMappings", attributeMappings);
        config.put("addShadowUserOnLogin", true);
        config.put("storeCustomAttributes", true);
        config.put("metaDataLocation", idpMetadata);
        config.put("nameID", "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified");
        config.put("assertionConsumerIndex", 0);
        config.put("metadataTrustCheck", false);
        config.put("showSamlLink", true);
        config.put("linkText", AD_FS_SAML_FOR_IDATS);
        config.put("skipSslValidation", true);

        Map<String, Object> result = new HashMap<>();
        result.put("type", "saml");
        result.put("originKey", adfsOriginKey);
        result.put("name", AD_FS_SAML_FOR_IDATS);
        result.put("active", true);
        result.put("config", config);
        return result;
    }
}