package org.cloudfoundry.identity.acceptance;

import org.hamcrest.Matchers;
import org.junit.*;
import org.junit.runner.RunWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.Assert.assertThat;

@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = DefaultAcceptanceTestConfig.class)
public class SiteMinderIntegrationTests {

    public static final String CA_SITEMINDER_SAML_FOR_IDATS = "CA Siteminder SAML for IDaTS";
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

    private String siteMinderOriginKey = "idats-siteminder";

    private String clientId = "test-client-" + UUID.randomUUID();
    private String clientSecret = clientId + "-password";

    private String zoneId = "idats";

    @Before
    public void setUp() {
        adminToken = testClient.getClientAccessToken(adminClientId, adminClientSecret, "");
        baseUrlWithProtocol = protocol + baseUrl;
        System.out.println("Logging out from previous session.");
        webDriver.get(baseUrlWithProtocol + "/logout.do");
        System.out.println("Log out complete.");

        System.out.println("URL: "+ baseUrlWithProtocol);
        Assume.assumeTrue("This test is against GCP environment", baseUrlWithProtocol.contains(".uaa-acceptance.cf-app.com"));
    }

    @Test
    public void testGCPSiteMinder() throws Exception {
        setupIdp(testClient, baseUrlWithProtocol, adminToken, siteMinderMetadata);
        testClient.createPasswordClient(adminToken, clientId, clientSecret);

        assertLoginFlow(baseUrlWithProtocol);
        assertCustomAttributeMappings(baseUrlWithProtocol, testClient);

        testClient.deleteClient(adminToken, clientId);

        webDriver.get(baseUrlWithProtocol + "/logout.do");
    }

    @Test
    public void testGCPSiteMinderOnNonSystemZone() throws Exception {
        String zoneUrl = protocol + zoneId + "." + baseUrl;
        TestClient zoneClient = new TestClient(restTemplate, zoneUrl);

        String zoneAdminClientId = "test-client-" + UUID.randomUUID();
        String zoneAdminClientSecret = zoneAdminClientId + "-password";

        testClient.createZoneAdminClient(adminToken, zoneAdminClientId, zoneAdminClientSecret, zoneId);
        String zoneAdminToken = zoneClient.getClientAccessToken(zoneAdminClientId, zoneAdminClientSecret, "");

        setupIdp(zoneClient, zoneUrl, zoneAdminToken, siteMinderMetadataForIDATsZone);
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
        webDriver.findElement(By.xpath("//a[text()='" + CA_SITEMINDER_SAML_FOR_IDATS + "']")).click();

        if (!webDriver.getCurrentUrl().contains(url)) {
            webDriver.findElement(By.xpath("//b[contains(text(), 'Please Login')]"));

            webDriver.findElement(By.name("USER")).clear();
            webDriver.findElement(By.name("USER")).sendKeys("techuser1");
            webDriver.findElement(By.name("PASSWORD")).sendKeys("Password01");
            webDriver.findElement(By.xpath("//input[@value='Login']")).click();
        }
        assertThat(webDriver.findElement(By.cssSelector("h1")).getText(), Matchers.containsString("Where to?"));
    }

    private void assertCustomAttributeMappings(String url, TestClient zoneClient) throws RuntimeException {
        webDriver.get(url + "/passcode");
        assertThat(webDriver.findElement(By.cssSelector("h1")).getText(), Matchers.containsString("Temporary Authentication Code"));
        String passcode = webDriver.findElement(By.cssSelector("h2")).getText();
        System.out.println("Passcode: " + passcode);

        String passwordToken = zoneClient.getPasswordToken(clientId, clientSecret, passcode);
        Map<String, Object> userInfo = zoneClient.getUserInfo(passwordToken);
        Map<String, Object> userAttributes = (Map<String, Object>) userInfo.get("user_attributes");
        assertThat(userAttributes, Matchers.hasEntry("email", Arrays.asList("techuser1@gmail.com")));
        assertThat(userAttributes, Matchers.hasEntry("fixedCustomAttributeToTestValue", Arrays.asList("testvalue")));
    }

    private void setupIdp(TestClient zoneClient, String zoneUrl, String adminToken, String idpMetadata) {
        List<Map> identityProviders = zoneClient.getIdentityProviders(zoneUrl, adminToken);

        Optional<Map> existingIdp = identityProviders.stream()
            .filter(entry -> siteMinderOriginKey.equals(entry.get("originKey")))
            .findFirst();

        Map<String, Object> idp = existingIdp.isPresent() ?
            zoneClient.updateIdentityProvider(zoneUrl, adminToken, (String) existingIdp.get().get("id"), getSiteMinderIDP(idpMetadata)) :
            zoneClient.createIdentityProvider(zoneUrl, adminToken, getSiteMinderIDP(idpMetadata));

        String siteminderIdp = String.format("Created IDP:\n\tid:%s\n\tname:%s\n\ttype:%s\n\torigin:%s\n\tactive:%s",
                                      idp.get("id"),
                                      idp.get("name"),
                                      idp.get("type"),
                                      idp.get("originKey"),
                                      idp.get("active")
        );
        System.out.println(siteminderIdp);
    }

    private Map<String, Object> getSiteMinderIDP(String idpMetadata) {
        Map<String, Object> config = new HashMap<>();
        HashMap<String, String> attributeMappings = new HashMap<>();
        attributeMappings.put("user.attribute.email", "emailaddress");
        attributeMappings.put("user.attribute.fixedCustomAttributeToTestValue", "idats");

        config.put("externalGroupsWhitelist", Collections.emptyList());
        config.put("attributeMappings", attributeMappings);
        config.put("addShadowUserOnLogin", true);
        config.put("storeCustomAttributes", true);
        config.put("metaDataLocation", idpMetadata);
        config.put("nameID", "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress");
        config.put("assertionConsumerIndex", 0);
        config.put("metadataTrustCheck", false);
        config.put("showSamlLink", true);
        config.put("linkText", CA_SITEMINDER_SAML_FOR_IDATS);
        config.put("skipSslValidation", true);

        Map<String, Object> result = new HashMap<>();
        result.put("type", "saml");
        result.put("originKey", siteMinderOriginKey);
        result.put("name", CA_SITEMINDER_SAML_FOR_IDATS);
        result.put("active", true);
        result.put("config", config);
        return result;
    }

    private String siteMinderMetadata = "<EntityDescriptor xmlns=\"urn:oasis:names:tc:SAML:2.0:metadata\" ID=\"SM24275085546f8ff6a82b78b6b7ec0e8b844be4a712f\" entityID=\"smidp\">\n" +
        "    <ds:Signature xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\">\n" +
        "<ds:SignedInfo>\n" +
        "<ds:CanonicalizationMethod Algorithm=\"http://www.w3.org/2001/10/xml-exc-c14n#\"/>\n" +
        "<ds:SignatureMethod Algorithm=\"http://www.w3.org/2000/09/xmldsig#rsa-sha1\"/>\n" +
        "<ds:Reference URI=\"#SM24275085546f8ff6a82b78b6b7ec0e8b844be4a712f\">\n" +
        "<ds:Transforms>\n" +
        "<ds:Transform Algorithm=\"http://www.w3.org/2000/09/xmldsig#enveloped-signature\"/>\n" +
        "<ds:Transform Algorithm=\"http://www.w3.org/2001/10/xml-exc-c14n#\"/>\n" +
        "</ds:Transforms>\n" +
        "<ds:DigestMethod Algorithm=\"http://www.w3.org/2000/09/xmldsig#sha1\"/>\n" +
        "<ds:DigestValue>3ZtZZEFfzdtuNNc287FE57fqdv0=</ds:DigestValue>\n" +
        "</ds:Reference>\n" +
        "</ds:SignedInfo>\n" +
        "<ds:SignatureValue>\n" +
        "ExBS934avWExVcQmWELBgyFXcuzRZmT9wfAUVlq5gKclkQ9MKAe0rn6Vhx5I1ZQCUd8E+lVBpZWG\n" +
        "B+YeyKt18ScnDa6cY2Ume0Sa41PXO6mFfvaB4MrkIzte909DcRyjongNVN8JUCJ7J2+ZVQAxoANc\n" +
        "kQoFs9EIir7vfw3er6E=\n" +
        "</ds:SignatureValue>\n" +
        "<ds:KeyInfo>\n" +
        "<ds:X509Data>\n" +
        "<ds:X509Certificate>\n" +
        "MIICRzCCAbCgAwIBAgIEUtf+gjANBgkqhkiG9w0BAQQFADBoMQswCQYDVQQGEwJVUzERMA8GA1UE\n" +
        "CBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQswCQYDVQQKEwJjYTERMA8GA1UECxMIc2Vj\n" +
        "dXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwHhcNMTQwMTE2MTU0NTA2WhcNMjQwMTE0MTU0NTA2\n" +
        "WjBoMQswCQYDVQQGEwJVUzERMA8GA1UECBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQsw\n" +
        "CQYDVQQKEwJjYTERMA8GA1UECxMIc2VjdXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwgZ8wDQYJ\n" +
        "KoZIhvcNAQEBBQADgY0AMIGJAoGBAOap0m7c+LSOAoGLUD3TAdS7BcJFns6HPSGAYK9NBY6MxITK\n" +
        "ElqVWHaVoaqxHCQxdQsF9oZvhPAmiNsbIRniKA+cypUov8U0pNIRPPBfl7p9ojGPZf5OtotnUnEN\n" +
        "2ZcYuZwxRnKPfpfEs5fshSvcZIa34FCSCw8L0sRDoWFIucBjAgMBAAEwDQYJKoZIhvcNAQEEBQAD\n" +
        "gYEAFbsuhxBm3lUkycfZZuNYft1j41k+FyLLTyXyPJKmc2s2RPOYtLQyolNB214ZCIZzVSExyfo9\n" +
        "59ZBvdWz+UinpFNPd8cEc0nuXOmfW/XBEgT0YS1vIDUzfeVRyZLj2u4BdBGwmK5oYRbgHxViFVnn\n" +
        "3C6UN5rcg5mZl0FBXJ31Zuk=\n" +
        "</ds:X509Certificate>\n" +
        "</ds:X509Data>\n" +
        "</ds:KeyInfo>\n" +
        "</ds:Signature><IDPSSODescriptor ID=\"SM1e7cc516f5c67b77db2c635512344647444b86d60d5\" WantAuthnRequestsSigned=\"false\" protocolSupportEnumeration=\"urn:oasis:names:tc:SAML:2.0:protocol\">\n" +
        "        <KeyDescriptor use=\"signing\">\n" +
        "            <ns1:KeyInfo xmlns:ns1=\"http://www.w3.org/2000/09/xmldsig#\" Id=\"SM1187dd08160b3a97e700c3ea76001bee06dd4fbd4a\">\n" +
        "                <ns1:X509Data>\n" +
        "                    <ns1:X509IssuerSerial>\n" +
        "                        <ns1:X509IssuerName>CN=siteminder,OU=security,O=ca,L=islandia,ST=new york,C=US</ns1:X509IssuerName>\n" +
        "                        <ns1:X509SerialNumber>1389887106</ns1:X509SerialNumber>\n" +
        "                    </ns1:X509IssuerSerial>\n" +
        "                    <ns1:X509Certificate>MIICRzCCAbCgAwIBAgIEUtf+gjANBgkqhkiG9w0BAQQFADBoMQswCQYDVQQGEwJVUzERMA8GA1UECBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQswCQYDVQQKEwJjYTERMA8GA1UECxMIc2VjdXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwHhcNMTQwMTE2MTU0NTA2WhcNMjQwMTE0MTU0NTA2WjBoMQswCQYDVQQGEwJVUzERMA8GA1UECBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQswCQYDVQQKEwJjYTERMA8GA1UECxMIc2VjdXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAOap0m7c+LSOAoGLUD3TAdS7BcJFns6HPSGAYK9NBY6MxITKElqVWHaVoaqxHCQxdQsF9oZvhPAmiNsbIRniKA+cypUov8U0pNIRPPBfl7p9ojGPZf5OtotnUnEN2ZcYuZwxRnKPfpfEs5fshSvcZIa34FCSCw8L0sRDoWFIucBjAgMBAAEwDQYJKoZIhvcNAQEEBQADgYEAFbsuhxBm3lUkycfZZuNYft1j41k+FyLLTyXyPJKmc2s2RPOYtLQyolNB214ZCIZzVSExyfo959ZBvdWz+UinpFNPd8cEc0nuXOmfW/XBEgT0YS1vIDUzfeVRyZLj2u4BdBGwmK5oYRbgHxViFVnn3C6UN5rcg5mZl0FBXJ31Zuk=</ns1:X509Certificate>\n" +
        "                    <ns1:X509SubjectName>CN=siteminder,OU=security,O=ca,L=islandia,ST=new york,C=US</ns1:X509SubjectName>\n" +
        "                </ns1:X509Data>\n" +
        "            </ns1:KeyInfo>\n" +
        "        </KeyDescriptor>\n" +
        "        <NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</NameIDFormat>\n" +
        "        <SingleSignOnService Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect\" Location=\"https://vp6.casecurecenter.com/affwebservices/public/saml2sso\"/>\n" +
        "        <SingleSignOnService Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\" Location=\"https://vp6.casecurecenter.com/affwebservices/public/saml2sso\"/>\n" +
        "        <ns2:Attribute xmlns:ns2=\"urn:oasis:names:tc:SAML:2.0:assertion\" Name=\"emailaddress\" NameFormat=\"urn:oasis:names:tc:SAML:2.0:attrname-format:unspecified\"/>\n" +
        "    </IDPSSODescriptor>\n" +
        "</EntityDescriptor>";

    private String siteMinderMetadataForIDATsZone = "<EntityDescriptor xmlns=\"urn:oasis:names:tc:SAML:2.0:metadata\" ID=\"SMe827ddf0248edff0f4ccba51881b6c02eda0b4daf9d\" entityID=\"smidp\">\n" +
            "    <ds:Signature xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\">\n" +
            "<ds:SignedInfo>\n" +
            "<ds:CanonicalizationMethod Algorithm=\"http://www.w3.org/2001/10/xml-exc-c14n#\"/>\n" +
            "<ds:SignatureMethod Algorithm=\"http://www.w3.org/2000/09/xmldsig#rsa-sha1\"/>\n" +
            "<ds:Reference URI=\"#SMe827ddf0248edff0f4ccba51881b6c02eda0b4daf9d\">\n" +
            "<ds:Transforms>\n" +
            "<ds:Transform Algorithm=\"http://www.w3.org/2000/09/xmldsig#enveloped-signature\"/>\n" +
            "<ds:Transform Algorithm=\"http://www.w3.org/2001/10/xml-exc-c14n#\"/>\n" +
            "</ds:Transforms>\n" +
            "<ds:DigestMethod Algorithm=\"http://www.w3.org/2000/09/xmldsig#sha1\"/>\n" +
            "<ds:DigestValue>j/VL/zvY/Jcry7hPCdTHpgpMdNw=</ds:DigestValue>\n" +
            "</ds:Reference>\n" +
            "</ds:SignedInfo>\n" +
            "<ds:SignatureValue>\n" +
            "UdaN2v2+r+MZmSEiUju9MYH8B9h6kopPEyIyNoPzEKkG7F4nNubp/zL6Zww/Oz0/jnRicCxsWfeZ\n" +
            "rfx2LqnDK4VMdFwBQIsHFdFgmv8kR56YyKcujz7ECzCQ+nQngxbUapqmtdTiAx1AGlWH4K/sGHsJ\n" +
            "338Gno6dtivDoWfUsUg=\n" +
            "</ds:SignatureValue>\n" +
            "<ds:KeyInfo>\n" +
            "<ds:X509Data>\n" +
            "<ds:X509Certificate>\n" +
            "MIICRzCCAbCgAwIBAgIEUtf+gjANBgkqhkiG9w0BAQQFADBoMQswCQYDVQQGEwJVUzERMA8GA1UE\n" +
            "CBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQswCQYDVQQKEwJjYTERMA8GA1UECxMIc2Vj\n" +
            "dXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwHhcNMTQwMTE2MTU0NTA2WhcNMjQwMTE0MTU0NTA2\n" +
            "WjBoMQswCQYDVQQGEwJVUzERMA8GA1UECBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQsw\n" +
            "CQYDVQQKEwJjYTERMA8GA1UECxMIc2VjdXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwgZ8wDQYJ\n" +
            "KoZIhvcNAQEBBQADgY0AMIGJAoGBAOap0m7c+LSOAoGLUD3TAdS7BcJFns6HPSGAYK9NBY6MxITK\n" +
            "ElqVWHaVoaqxHCQxdQsF9oZvhPAmiNsbIRniKA+cypUov8U0pNIRPPBfl7p9ojGPZf5OtotnUnEN\n" +
            "2ZcYuZwxRnKPfpfEs5fshSvcZIa34FCSCw8L0sRDoWFIucBjAgMBAAEwDQYJKoZIhvcNAQEEBQAD\n" +
            "gYEAFbsuhxBm3lUkycfZZuNYft1j41k+FyLLTyXyPJKmc2s2RPOYtLQyolNB214ZCIZzVSExyfo9\n" +
            "59ZBvdWz+UinpFNPd8cEc0nuXOmfW/XBEgT0YS1vIDUzfeVRyZLj2u4BdBGwmK5oYRbgHxViFVnn\n" +
            "3C6UN5rcg5mZl0FBXJ31Zuk=\n" +
            "</ds:X509Certificate>\n" +
            "</ds:X509Data>\n" +
            "</ds:KeyInfo>\n" +
            "</ds:Signature><IDPSSODescriptor ID=\"SM12098efdb66f91efc594587f20e3666ee6da3941f02e\" WantAuthnRequestsSigned=\"false\" protocolSupportEnumeration=\"urn:oasis:names:tc:SAML:2.0:protocol\">\n" +
            "        <KeyDescriptor use=\"signing\">\n" +
            "            <ns1:KeyInfo xmlns:ns1=\"http://www.w3.org/2000/09/xmldsig#\" Id=\"SMa2a8c8f61398c19ae8c6d8a03f1e67bc6da0171ec16\">\n" +
            "                <ns1:X509Data>\n" +
            "                    <ns1:X509IssuerSerial>\n" +
            "                        <ns1:X509IssuerName>CN=siteminder,OU=security,O=ca,L=islandia,ST=new york,C=US</ns1:X509IssuerName>\n" +
            "                        <ns1:X509SerialNumber>1389887106</ns1:X509SerialNumber>\n" +
            "                    </ns1:X509IssuerSerial>\n" +
            "                    <ns1:X509Certificate>MIICRzCCAbCgAwIBAgIEUtf+gjANBgkqhkiG9w0BAQQFADBoMQswCQYDVQQGEwJVUzERMA8GA1UECBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQswCQYDVQQKEwJjYTERMA8GA1UECxMIc2VjdXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwHhcNMTQwMTE2MTU0NTA2WhcNMjQwMTE0MTU0NTA2WjBoMQswCQYDVQQGEwJVUzERMA8GA1UECBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQswCQYDVQQKEwJjYTERMA8GA1UECxMIc2VjdXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAOap0m7c+LSOAoGLUD3TAdS7BcJFns6HPSGAYK9NBY6MxITKElqVWHaVoaqxHCQxdQsF9oZvhPAmiNsbIRniKA+cypUov8U0pNIRPPBfl7p9ojGPZf5OtotnUnEN2ZcYuZwxRnKPfpfEs5fshSvcZIa34FCSCw8L0sRDoWFIucBjAgMBAAEwDQYJKoZIhvcNAQEEBQADgYEAFbsuhxBm3lUkycfZZuNYft1j41k+FyLLTyXyPJKmc2s2RPOYtLQyolNB214ZCIZzVSExyfo959ZBvdWz+UinpFNPd8cEc0nuXOmfW/XBEgT0YS1vIDUzfeVRyZLj2u4BdBGwmK5oYRbgHxViFVnn3C6UN5rcg5mZl0FBXJ31Zuk=</ns1:X509Certificate>\n" +
            "                    <ns1:X509SubjectName>CN=siteminder,OU=security,O=ca,L=islandia,ST=new york,C=US</ns1:X509SubjectName>\n" +
            "                </ns1:X509Data>\n" +
            "            </ns1:KeyInfo>\n" +
            "        </KeyDescriptor>\n" +
            "        <NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</NameIDFormat>\n" +
            "        <SingleSignOnService Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect\" Location=\"https://vp6.casecurecenter.com/affwebservices/public/saml2sso\"/>\n" +
            "        <SingleSignOnService Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\" Location=\"https://vp6.casecurecenter.com/affwebservices/public/saml2sso\"/>\n" +
            "        <ns2:Attribute xmlns:ns2=\"urn:oasis:names:tc:SAML:2.0:assertion\" Name=\"emailaddress\" NameFormat=\"urn:oasis:names:tc:SAML:2.0:attrname-format:unspecified\"/>\n" +
            "        <ns3:Attribute xmlns:ns3=\"urn:oasis:names:tc:SAML:2.0:assertion\" Name=\"idats\" NameFormat=\"urn:oasis:names:tc:SAML:2.0:attrname-format:unspecified\"/>\n" +
            "    </IDPSSODescriptor>\n" +
            "</EntityDescriptor>\n";
}
