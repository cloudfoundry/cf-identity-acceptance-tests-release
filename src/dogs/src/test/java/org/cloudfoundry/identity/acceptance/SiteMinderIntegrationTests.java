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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Since Oct 2017, add ignore to siteminder AT until Siteminder comes back up")
@ExtendWith(SpringExtension.class)
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

    private final String siteMinderOriginKey = "idats-siteminder";

    private final String clientId = "test-client-" + UUID.randomUUID();
    private final String clientSecret = clientId + "-password";

    @BeforeEach
    void setUp() {
        adminToken = testClient.getClientAccessToken(adminClientId, adminClientSecret, "");
        baseUrlWithProtocol = protocol + baseUrl;
        System.out.println("Logging out from previous session.");
        webDriver.get(baseUrlWithProtocol + "/logout.do");
        System.out.println("Log out complete.");

        System.out.println("URL: " + baseUrlWithProtocol);
        Assumptions.assumeTrue(baseUrlWithProtocol.contains(".uaa-acceptance.cf-app.com"), "This test is against GCP environment");
    }

    @Test
    void gcpSiteMinder() {
        setupIdp(testClient, baseUrlWithProtocol, adminToken, siteMinderMetadata);
        testClient.createPasswordClient(adminToken, clientId, clientSecret);

        assertLoginFlow(baseUrlWithProtocol);
        assertCustomAttributeMappings(baseUrlWithProtocol, testClient);

        testClient.deleteClient(adminToken, clientId);

        webDriver.get(baseUrlWithProtocol + "/logout.do");
    }

    @Test
    void gcpSiteMinderOnNonSystemZone() {
        String zoneId = "idats";
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
        assertThat(userAttributes).containsEntry("email", List.of("techuser1@gmail.com"))
                .containsEntry("fixedCustomAttributeToTestValue", List.of("testvalue"));
    }

    private void setupIdp(TestClient zoneClient, String zoneUrl, String adminToken, String idpMetadata) {
        List<Map> identityProviders = zoneClient.getIdentityProviders(zoneUrl, adminToken);

        Optional<Map> existingIdp = identityProviders.stream()
                .filter(entry -> siteMinderOriginKey.equals(entry.get("originKey")))
                .findFirst();

        Map<String, Object> idp = existingIdp.isPresent() ?
                zoneClient.updateIdentityProvider(zoneUrl, adminToken, (String) existingIdp.get().get("id"), getSiteMinderIDP(idpMetadata)) :
                zoneClient.createIdentityProvider(zoneUrl, adminToken, getSiteMinderIDP(idpMetadata));

        String siteminderIdp = "Created IDP:%n\tid:%s%n\tname:%s%n\ttype:%s%n\torigin:%s%n\tactive:%s".formatted(
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

    private final String siteMinderMetadata = """
            <EntityDescriptor xmlns="urn:oasis:names:tc:SAML:2.0:metadata" ID="SM24275085546f8ff6a82b78b6b7ec0e8b844be4a712f" entityID="smidp">
                <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
            <ds:SignedInfo>
            <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
            <ds:SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1"/>
            <ds:Reference URI="#SM24275085546f8ff6a82b78b6b7ec0e8b844be4a712f">
            <ds:Transforms>
            <ds:Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"/>
            <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
            </ds:Transforms>
            <ds:DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1"/>
            <ds:DigestValue>3ZtZZEFfzdtuNNc287FE57fqdv0=</ds:DigestValue>
            </ds:Reference>
            </ds:SignedInfo>
            <ds:SignatureValue>
            ExBS934avWExVcQmWELBgyFXcuzRZmT9wfAUVlq5gKclkQ9MKAe0rn6Vhx5I1ZQCUd8E+lVBpZWG
            B+YeyKt18ScnDa6cY2Ume0Sa41PXO6mFfvaB4MrkIzte909DcRyjongNVN8JUCJ7J2+ZVQAxoANc
            kQoFs9EIir7vfw3er6E=
            </ds:SignatureValue>
            <ds:KeyInfo>
            <ds:X509Data>
            <ds:X509Certificate>
            MIICRzCCAbCgAwIBAgIEUtf+gjANBgkqhkiG9w0BAQQFADBoMQswCQYDVQQGEwJVUzERMA8GA1UE
            CBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQswCQYDVQQKEwJjYTERMA8GA1UECxMIc2Vj
            dXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwHhcNMTQwMTE2MTU0NTA2WhcNMjQwMTE0MTU0NTA2
            WjBoMQswCQYDVQQGEwJVUzERMA8GA1UECBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQsw
            CQYDVQQKEwJjYTERMA8GA1UECxMIc2VjdXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwgZ8wDQYJ
            KoZIhvcNAQEBBQADgY0AMIGJAoGBAOap0m7c+LSOAoGLUD3TAdS7BcJFns6HPSGAYK9NBY6MxITK
            ElqVWHaVoaqxHCQxdQsF9oZvhPAmiNsbIRniKA+cypUov8U0pNIRPPBfl7p9ojGPZf5OtotnUnEN
            2ZcYuZwxRnKPfpfEs5fshSvcZIa34FCSCw8L0sRDoWFIucBjAgMBAAEwDQYJKoZIhvcNAQEEBQAD
            gYEAFbsuhxBm3lUkycfZZuNYft1j41k+FyLLTyXyPJKmc2s2RPOYtLQyolNB214ZCIZzVSExyfo9
            59ZBvdWz+UinpFNPd8cEc0nuXOmfW/XBEgT0YS1vIDUzfeVRyZLj2u4BdBGwmK5oYRbgHxViFVnn
            3C6UN5rcg5mZl0FBXJ31Zuk=
            </ds:X509Certificate>
            </ds:X509Data>
            </ds:KeyInfo>
            </ds:Signature><IDPSSODescriptor ID="SM1e7cc516f5c67b77db2c635512344647444b86d60d5" WantAuthnRequestsSigned="false" protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                    <KeyDescriptor use="signing">
                        <ns1:KeyInfo xmlns:ns1="http://www.w3.org/2000/09/xmldsig#" Id="SM1187dd08160b3a97e700c3ea76001bee06dd4fbd4a">
                            <ns1:X509Data>
                                <ns1:X509IssuerSerial>
                                    <ns1:X509IssuerName>CN=siteminder,OU=security,O=ca,L=islandia,ST=new york,C=US</ns1:X509IssuerName>
                                    <ns1:X509SerialNumber>1389887106</ns1:X509SerialNumber>
                                </ns1:X509IssuerSerial>
                                <ns1:X509Certificate>MIICRzCCAbCgAwIBAgIEUtf+gjANBgkqhkiG9w0BAQQFADBoMQswCQYDVQQGEwJVUzERMA8GA1UECBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQswCQYDVQQKEwJjYTERMA8GA1UECxMIc2VjdXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwHhcNMTQwMTE2MTU0NTA2WhcNMjQwMTE0MTU0NTA2WjBoMQswCQYDVQQGEwJVUzERMA8GA1UECBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQswCQYDVQQKEwJjYTERMA8GA1UECxMIc2VjdXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAOap0m7c+LSOAoGLUD3TAdS7BcJFns6HPSGAYK9NBY6MxITKElqVWHaVoaqxHCQxdQsF9oZvhPAmiNsbIRniKA+cypUov8U0pNIRPPBfl7p9ojGPZf5OtotnUnEN2ZcYuZwxRnKPfpfEs5fshSvcZIa34FCSCw8L0sRDoWFIucBjAgMBAAEwDQYJKoZIhvcNAQEEBQADgYEAFbsuhxBm3lUkycfZZuNYft1j41k+FyLLTyXyPJKmc2s2RPOYtLQyolNB214ZCIZzVSExyfo959ZBvdWz+UinpFNPd8cEc0nuXOmfW/XBEgT0YS1vIDUzfeVRyZLj2u4BdBGwmK5oYRbgHxViFVnn3C6UN5rcg5mZl0FBXJ31Zuk=</ns1:X509Certificate>
                                <ns1:X509SubjectName>CN=siteminder,OU=security,O=ca,L=islandia,ST=new york,C=US</ns1:X509SubjectName>
                            </ns1:X509Data>
                        </ns1:KeyInfo>
                    </KeyDescriptor>
                    <NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</NameIDFormat>
                    <SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect" Location="https://vp6.casecurecenter.com/affwebservices/public/saml2sso"/>
                    <SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="https://vp6.casecurecenter.com/affwebservices/public/saml2sso"/>
                    <ns2:Attribute xmlns:ns2="urn:oasis:names:tc:SAML:2.0:assertion" Name="emailaddress" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:unspecified"/>
                </IDPSSODescriptor>
            </EntityDescriptor>""";

    private final String siteMinderMetadataForIDATsZone = """
            <EntityDescriptor xmlns="urn:oasis:names:tc:SAML:2.0:metadata" ID="SMe827ddf0248edff0f4ccba51881b6c02eda0b4daf9d" entityID="smidp">
                <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
            <ds:SignedInfo>
            <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
            <ds:SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1"/>
            <ds:Reference URI="#SMe827ddf0248edff0f4ccba51881b6c02eda0b4daf9d">
            <ds:Transforms>
            <ds:Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"/>
            <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
            </ds:Transforms>
            <ds:DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1"/>
            <ds:DigestValue>j/VL/zvY/Jcry7hPCdTHpgpMdNw=</ds:DigestValue>
            </ds:Reference>
            </ds:SignedInfo>
            <ds:SignatureValue>
            UdaN2v2+r+MZmSEiUju9MYH8B9h6kopPEyIyNoPzEKkG7F4nNubp/zL6Zww/Oz0/jnRicCxsWfeZ
            rfx2LqnDK4VMdFwBQIsHFdFgmv8kR56YyKcujz7ECzCQ+nQngxbUapqmtdTiAx1AGlWH4K/sGHsJ
            338Gno6dtivDoWfUsUg=
            </ds:SignatureValue>
            <ds:KeyInfo>
            <ds:X509Data>
            <ds:X509Certificate>
            MIICRzCCAbCgAwIBAgIEUtf+gjANBgkqhkiG9w0BAQQFADBoMQswCQYDVQQGEwJVUzERMA8GA1UE
            CBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQswCQYDVQQKEwJjYTERMA8GA1UECxMIc2Vj
            dXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwHhcNMTQwMTE2MTU0NTA2WhcNMjQwMTE0MTU0NTA2
            WjBoMQswCQYDVQQGEwJVUzERMA8GA1UECBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQsw
            CQYDVQQKEwJjYTERMA8GA1UECxMIc2VjdXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwgZ8wDQYJ
            KoZIhvcNAQEBBQADgY0AMIGJAoGBAOap0m7c+LSOAoGLUD3TAdS7BcJFns6HPSGAYK9NBY6MxITK
            ElqVWHaVoaqxHCQxdQsF9oZvhPAmiNsbIRniKA+cypUov8U0pNIRPPBfl7p9ojGPZf5OtotnUnEN
            2ZcYuZwxRnKPfpfEs5fshSvcZIa34FCSCw8L0sRDoWFIucBjAgMBAAEwDQYJKoZIhvcNAQEEBQAD
            gYEAFbsuhxBm3lUkycfZZuNYft1j41k+FyLLTyXyPJKmc2s2RPOYtLQyolNB214ZCIZzVSExyfo9
            59ZBvdWz+UinpFNPd8cEc0nuXOmfW/XBEgT0YS1vIDUzfeVRyZLj2u4BdBGwmK5oYRbgHxViFVnn
            3C6UN5rcg5mZl0FBXJ31Zuk=
            </ds:X509Certificate>
            </ds:X509Data>
            </ds:KeyInfo>
            </ds:Signature><IDPSSODescriptor ID="SM12098efdb66f91efc594587f20e3666ee6da3941f02e" WantAuthnRequestsSigned="false" protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                    <KeyDescriptor use="signing">
                        <ns1:KeyInfo xmlns:ns1="http://www.w3.org/2000/09/xmldsig#" Id="SMa2a8c8f61398c19ae8c6d8a03f1e67bc6da0171ec16">
                            <ns1:X509Data>
                                <ns1:X509IssuerSerial>
                                    <ns1:X509IssuerName>CN=siteminder,OU=security,O=ca,L=islandia,ST=new york,C=US</ns1:X509IssuerName>
                                    <ns1:X509SerialNumber>1389887106</ns1:X509SerialNumber>
                                </ns1:X509IssuerSerial>
                                <ns1:X509Certificate>MIICRzCCAbCgAwIBAgIEUtf+gjANBgkqhkiG9w0BAQQFADBoMQswCQYDVQQGEwJVUzERMA8GA1UECBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQswCQYDVQQKEwJjYTERMA8GA1UECxMIc2VjdXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwHhcNMTQwMTE2MTU0NTA2WhcNMjQwMTE0MTU0NTA2WjBoMQswCQYDVQQGEwJVUzERMA8GA1UECBMIbmV3IHlvcmsxETAPBgNVBAcTCGlzbGFuZGlhMQswCQYDVQQKEwJjYTERMA8GA1UECxMIc2VjdXJpdHkxEzARBgNVBAMTCnNpdGVtaW5kZXIwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAOap0m7c+LSOAoGLUD3TAdS7BcJFns6HPSGAYK9NBY6MxITKElqVWHaVoaqxHCQxdQsF9oZvhPAmiNsbIRniKA+cypUov8U0pNIRPPBfl7p9ojGPZf5OtotnUnEN2ZcYuZwxRnKPfpfEs5fshSvcZIa34FCSCw8L0sRDoWFIucBjAgMBAAEwDQYJKoZIhvcNAQEEBQADgYEAFbsuhxBm3lUkycfZZuNYft1j41k+FyLLTyXyPJKmc2s2RPOYtLQyolNB214ZCIZzVSExyfo959ZBvdWz+UinpFNPd8cEc0nuXOmfW/XBEgT0YS1vIDUzfeVRyZLj2u4BdBGwmK5oYRbgHxViFVnn3C6UN5rcg5mZl0FBXJ31Zuk=</ns1:X509Certificate>
                                <ns1:X509SubjectName>CN=siteminder,OU=security,O=ca,L=islandia,ST=new york,C=US</ns1:X509SubjectName>
                            </ns1:X509Data>
                        </ns1:KeyInfo>
                    </KeyDescriptor>
                    <NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</NameIDFormat>
                    <SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect" Location="https://vp6.casecurecenter.com/affwebservices/public/saml2sso"/>
                    <SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="https://vp6.casecurecenter.com/affwebservices/public/saml2sso"/>
                    <ns2:Attribute xmlns:ns2="urn:oasis:names:tc:SAML:2.0:assertion" Name="emailaddress" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:unspecified"/>
                    <ns3:Attribute xmlns:ns3="urn:oasis:names:tc:SAML:2.0:assertion" Name="idats" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:unspecified"/>
                </IDPSSODescriptor>
            </EntityDescriptor>
            """;
}
