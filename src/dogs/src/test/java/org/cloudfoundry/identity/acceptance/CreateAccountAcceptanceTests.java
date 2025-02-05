/* ******************************************************************************
 *     Cloud Foundry
 *     Copyright (c) [2009-2017] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/
package org.cloudfoundry.identity.acceptance;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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

import java.security.SecureRandom;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DefaultAcceptanceTestConfig.class)
public class CreateAccountAcceptanceTests {

    public static final String SECRET = "s3Cret";

    @Autowired
    WebDriver webDriver;

    @Autowired
    private TestClient testClient;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${PROTOCOL}")
    private String protocol;

    @Value("${BASE_URL}")
    private String baseUrl;
    private String userEmail;

    @Value("${IDENTITY_CLIENT_SECRET:identity_secret}")
    private String identityClientSecret;

    @BeforeEach
    void setUp() {
        webDriver.get(protocol + baseUrl + "/logout.do");
        userEmail = "uaa-user-" + new SecureRandom().nextInt() + "@mailinator.com";
    }

    //cf-coreservices-eng@pivotal.io key
    @Value("${MAILINATOR_API_KEY}")
    private String mailinatorApiKey;

    @Test
    @Disabled
    void signup() throws Exception {
        webDriver.get(protocol + baseUrl + "/");
        webDriver.findElement(By.xpath("//*[text()='Create account']")).click();

        assertThat(webDriver.findElement(By.tagName("h1")).getText()).isEqualTo("Create your account");


        webDriver.findElement(By.name("email")).sendKeys(userEmail);
        webDriver.findElement(By.name("password")).sendKeys(SECRET);
        webDriver.findElement(By.name("password_confirmation")).sendKeys(SECRET);

        webDriver.findElement(By.xpath("//input[@value='Send activation link']")).click();

        assertThat(webDriver.findElement(By.tagName("h1")).getText()).isEqualTo("Create your account");
        assertThat(webDriver.findElement(By.cssSelector(".instructions-sent")).getText()).isEqualTo("Please check email for an activation link.");

        String email = fetchEmail(userEmail.split("@")[0]);
        assertThat(email).contains("Activate your account");
        String link = testClient.extractLink(email);
        assertThat(isEmpty(link)).isFalse();
        webDriver.get(link);

        assertThat(webDriver.findElement(By.cssSelector("h1")).getText()).contains("Welcome!");

        webDriver.findElement(By.name("username")).sendKeys(userEmail);
        webDriver.findElement(By.name("password")).sendKeys(SECRET);
        webDriver.findElement(By.xpath("//input[@value='Sign in']")).click();

        assertThat(webDriver.findElement(By.cssSelector("h1")).getText()).contains("Where to?");
    }

    @Test
    @Disabled
    void signupInZone() throws Exception {
        String subdomain = "koala-" + new SecureRandom().nextInt();

        String identityClientToken = testClient.getClientAccessToken("identity", identityClientSecret, "zones.write");
        testClient.createZone(identityClientToken, subdomain, subdomain, subdomain, subdomain + " description");

        webDriver.get(protocol + subdomain + "." + baseUrl + "/");
        webDriver.findElement(By.xpath("//*[text()='Create account']")).click();

        assertThat(webDriver.findElement(By.tagName("h1")).getText()).isEqualTo("Create your account");


        webDriver.findElement(By.name("email")).sendKeys(userEmail);
        webDriver.findElement(By.name("password")).sendKeys(SECRET);
        webDriver.findElement(By.name("password_confirmation")).sendKeys(SECRET);

        webDriver.findElement(By.xpath("//input[@value='Send activation link']")).click();

        assertThat(webDriver.findElement(By.tagName("h1")).getText()).isEqualTo("Create your account");
        assertThat(webDriver.findElement(By.cssSelector(".instructions-sent")).getText()).isEqualTo("Please check email for an activation link.");

        String email = fetchEmail(userEmail.split("@")[0]);
        assertThat(email).contains("Activate your account")
                .contains("https://" + subdomain + "." + baseUrl);
    }

    private String fetchEmail(String username) throws InterruptedException, JSONException {
        int maxTimesToCheck = 120;
        JSONObject receivedEmail = null;
        String url = "https://api.mailinator.com/api/inbox?to=" + username + "&token=" + mailinatorApiKey;
        for (int i = 0; receivedEmail == null && i < maxTimesToCheck; i++) {
            String jsonEmail = restTemplate.getForObject(url, String.class);
            JSONObject messages = new JSONObject(jsonEmail);
            JSONArray receivedEmails = messages.getJSONArray("messages");
            if (!receivedEmails.isEmpty()) {
                receivedEmail = (JSONObject) receivedEmails.get(0);
            } else {
                Thread.sleep(5000);
            }
        }
        assertThat(receivedEmail).as("No email was received in inbox " + username + " after " + maxTimesToCheck + " seconds. Check Mailinator to see if there is an email for that inbox: " + url).isNotNull();
        String fullEmail = null;
        for (int i = 0; fullEmail == null && i < maxTimesToCheck; i++) {
            fullEmail = restTemplate.getForObject("https://api.mailinator.com/api/email?id=" + receivedEmail.getString("id") + "&token=" + mailinatorApiKey, String.class);
            if (fullEmail == null) {
                Thread.sleep(5000);
            }
        }
        JSONObject body = new JSONObject(fullEmail);
        return ((JSONObject) body.getJSONObject("data").getJSONArray("parts").get(0)).getString("body");
    }
}
