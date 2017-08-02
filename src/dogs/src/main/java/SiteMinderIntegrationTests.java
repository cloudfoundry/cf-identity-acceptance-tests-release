import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertThat;


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
    private WebDriver webDriver;

    private String adminToken;
    private String url;

    private String siteMinderOriginKey = "idats-siteminder";

    @Before
    public void setUp() {
        adminToken = testClient.getClientAccessToken(adminClientId, adminClientSecret, "");
        url = protocol + baseUrl;
        System.out.println("Logging out from previous session.");
        webDriver.get(url + "/logout.do");
        System.out.println("Log out complete.");
    }



    @Test
    public void test() throws Exception {
        System.out.println("URL: "+url);
        Assume.assumeTrue("This test is against GCP environment", url.contains(".uaa-acceptance.cf-app.com"));
        List<Map> identityProviders = testClient.getIdentityProviders(url, adminToken);

        Optional<Map> existingIdp = identityProviders.stream()
            .filter(entry -> siteMinderOriginKey.equals(entry.get("originKey")))
            .findFirst();

        Map<String, Object> idp = existingIdp.isPresent() ?
            testClient.updateIdentityProvider(url, adminToken, (String) existingIdp.get().get("id"), getSiteMinderIDP()) :
            testClient.createIdentityProvider(url, adminToken, getSiteMinderIDP());

        String siteminderIdp = String.format("Created IDP:\n\tid:%s\n\tname:%s\n\ttype:%s\n\torigin:%s\n\tactive:%s",
                                      idp.get("id"),
                                      idp.get("name"),
                                      idp.get("type"),
                                      idp.get("originKey"),
                                      idp.get("active")
        );
        System.out.println(siteminderIdp);

        //browser login flow
        webDriver.get(url + "/login");
        webDriver.findElement(By.xpath("//a[text()='" + CA_SITEMINDER_SAML_FOR_IDATS + "']")).click();
        webDriver.findElement(By.xpath("//b[contains(text(), 'Please Login')]"));

        webDriver.findElement(By.name("USER")).clear();
        webDriver.findElement(By.name("USER")).sendKeys("techuser1");
        webDriver.findElement(By.name("PASSWORD")).sendKeys("Password01");
        webDriver.findElement(By.xpath("//input[@value='Login']")).click();
        assertThat(webDriver.findElement(By.cssSelector("h1")).getText(), Matchers.containsString("Where to?"));
    }

    private Map<String, Object> getSiteMinderIDP() {
        Map<String, Object> config = new HashMap<>();
        config.put("externalGroupsWhitelist", Collections.emptyList());
        config.put("attributeMappings", Collections.emptyMap());
        config.put("addShadowUserOnLogin", true);
        config.put("storeCustomAttributes", true);
        config.put("metaDataLocation", siteMinderMetadata);
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

    private String siteMinderMetadata = "<EntityDescriptor ID=\"SM1598b525eada26863eb23b38179c4a01f77472293709\" entityID=\"smidp\" xmlns=\"urn:oasis:names:tc:SAML:2.0:metadata\">\n" +
        "    <IDPSSODescriptor WantAuthnRequestsSigned=\"true\" ID=\"SM1371f007ef6afdd435cd1057831e4198325494ddfd6\" protocolSupportEnumeration=\"urn:oasis:names:tc:SAML:2.0:protocol\">\n" +
        "        <KeyDescriptor use=\"encryption\">\n" +
        "            <ns1:KeyInfo Id=\"SM213f05e6446b0930163464f88780e2b972539fbc99b\" xmlns:ns1=\"http://www.w3.org/2000/09/xmldsig#\">\n" +
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
        "        <KeyDescriptor use=\"signing\">\n" +
        "            <ns2:KeyInfo Id=\"SMd64ce50806db6db3f0cd9fa16a9162b977478bd3ae9\" xmlns:ns2=\"http://www.w3.org/2000/09/xmldsig#\">\n" +
        "                <ns2:X509Data>\n" +
        "                    <ns2:X509IssuerSerial>\n" +
        "                        <ns2:X509IssuerName>CN=siteminder,OU=security,O=ca,L=islandia,ST=new york,C=US</ns2:X509IssuerName>\n" +
        "                        <ns2:X509SerialNumber>1389887106</ns2:X509SerialNumber>\n" +
        "                    </ns2:X509IssuerSerial>\n" +
        "                    <ns2:X509Certificate>MIICRzCCAbCgAwIBAgIEUtf+gjANBgkqhkiG9w0BAQQFADBoMQswCQYDVQQGEwJVUzERMA8GA1UECBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQswCQYDVQQKEwJjYTERMA8GA1UECxMIc2VjdXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwHhcNMTQwMTE2MTU0NTA2WhcNMjQwMTE0MTU0NTA2WjBoMQswCQYDVQQGEwJVUzERMA8GA1UECBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQswCQYDVQQKEwJjYTERMA8GA1UECxMIc2VjdXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAOap0m7c+LSOAoGLUD3TAdS7BcJFns6HPSGAYK9NBY6MxITKElqVWHaVoaqxHCQxdQsF9oZvhPAmiNsbIRniKA+cypUov8U0pNIRPPBfl7p9ojGPZf5OtotnUnEN2ZcYuZwxRnKPfpfEs5fshSvcZIa34FCSCw8L0sRDoWFIucBjAgMBAAEwDQYJKoZIhvcNAQEEBQADgYEAFbsuhxBm3lUkycfZZuNYft1j41k+FyLLTyXyPJKmc2s2RPOYtLQyolNB214ZCIZzVSExyfo959ZBvdWz+UinpFNPd8cEc0nuXOmfW/XBEgT0YS1vIDUzfeVRyZLj2u4BdBGwmK5oYRbgHxViFVnn3C6UN5rcg5mZl0FBXJ31Zuk=</ns2:X509Certificate>\n" +
        "                    <ns2:X509SubjectName>CN=siteminder,OU=security,O=ca,L=islandia,ST=new york,C=US</ns2:X509SubjectName>\n" +
        "                </ns2:X509Data>\n" +
        "            </ns2:KeyInfo>\n" +
        "        </KeyDescriptor>\n" +
        "        <NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</NameIDFormat>\n" +
        "        <SingleSignOnService Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect\" Location=\"https://vp6.casecurecenter.com/affwebservices/public/saml2sso\"/>\n" +
        "        <SingleSignOnService Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\" Location=\"https://vp6.casecurecenter.com/affwebservices/public/saml2sso\"/>\n" +
        "        <ns3:Attribute Name=\"emailaddress\" NameFormat=\"urn:oasis:names:tc:SAML:2.0:attrname-format:unspecified\" xmlns:ns3=\"urn:oasis:names:tc:SAML:2.0:assertion\"/>\n" +
        "    </IDPSSODescriptor>\n" +
        "</EntityDescriptor>\n";

}
