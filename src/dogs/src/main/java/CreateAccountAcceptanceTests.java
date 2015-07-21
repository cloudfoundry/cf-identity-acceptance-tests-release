/*******************************************************************************
 *     Cloud Foundry
 *     Copyright (c) [2009-2014] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

/**
 * ****************************************************************************
 * Cloud Foundry
 * Copyright (c) [2009-2015] Pivotal Software, Inc. All Rights Reserved.
 * <p>
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 * <p>
 * This product includes a number of subcomponents with
 * separate copyright notices and license terms. Your use of these
 * subcomponents is subject to the terms and conditions of the
 * subcomponent's license, as noted in the LICENSE file.
 * *****************************************************************************
 */
@RunWith(SpringJUnit4ClassRunner.class)
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

    @Before
    public void setUp() {
        webDriver.get(protocol + baseUrl + "/logout.do");
    }

    private final String mailinatorApiKey = "89e1f4da2eb0400c9894f4ebb89d1776";

    @Test
    public void testSignup() throws Exception {
        String userEmail = "user" + new SecureRandom().nextInt() + "@mailinator.com";

        webDriver.get(protocol + baseUrl + "/");
        webDriver.findElement(By.xpath("//*[text()='Create account']")).click();

        Assert.assertEquals("Create your account", webDriver.findElement(By.tagName("h1")).getText());


        webDriver.findElement(By.name("email")).sendKeys(userEmail);
        webDriver.findElement(By.name("password")).sendKeys(SECRET);
        webDriver.findElement(By.name("password_confirmation")).sendKeys(SECRET);

        webDriver.findElement(By.xpath("//input[@value='Send activation link']")).click();

        Assert.assertEquals("Create your account", webDriver.findElement(By.tagName("h1")).getText());
        Assert.assertEquals("Please check email for an activation link.", webDriver.findElement(By.cssSelector(".instructions-sent")).getText());

        String email = fetchEmail(userEmail.split("@")[0]);
        assertThat(email, containsString("Activate your account"));
        String link = testClient.extractLink(email);
        assertFalse(isEmpty(link));
        webDriver.get(link);

        assertThat(webDriver.findElement(By.cssSelector("h1")).getText(), containsString("Where to?"));

        webDriver.findElement(By.xpath("//*[text()='"+userEmail+"']")).click();
        webDriver.findElement(By.linkText("Sign Out")).click();

        webDriver.findElement(By.name("username")).sendKeys(userEmail);
        webDriver.findElement(By.name("password")).sendKeys(SECRET);
        webDriver.findElement(By.xpath("//input[@value='Sign in']")).click();

        assertThat(webDriver.findElement(By.cssSelector("h1")).getText(), containsString("Where to?"));
    }

    @Test
    public void testSignupInZone() throws Exception {
        String subdomain = "koala-" + new SecureRandom().nextInt();

        String identityClientToken = testClient.getOAuthAccessToken("identity", "identity_secret", "client_credentials", "zones.write");
        testClient.createZone(identityClientToken, subdomain, subdomain, subdomain, subdomain + " description");

        String userEmail = "user" + new SecureRandom().nextInt() + "@mailinator.com";

        webDriver.get(protocol + subdomain + "." + baseUrl + "/");
        webDriver.findElement(By.xpath("//*[text()='Create account']")).click();

        Assert.assertEquals("Create your account", webDriver.findElement(By.tagName("h1")).getText());


        webDriver.findElement(By.name("email")).sendKeys(userEmail);
        webDriver.findElement(By.name("password")).sendKeys(SECRET);
        webDriver.findElement(By.name("password_confirmation")).sendKeys(SECRET);

        webDriver.findElement(By.xpath("//input[@value='Send activation link']")).click();

        Assert.assertEquals("Create your account", webDriver.findElement(By.tagName("h1")).getText());
        Assert.assertEquals("Please check email for an activation link.", webDriver.findElement(By.cssSelector(".instructions-sent")).getText());

        String email = fetchEmail(userEmail.split("@")[0]);
        assertThat(email, containsString("Activate your account"));
        assertThat(email, containsString("https://" + subdomain + "." + baseUrl));
    }

    private String fetchEmail(String username) throws InterruptedException, JSONException {
        Thread.sleep(8000);
        String jsonEmail = restTemplate.getForObject("https://api.mailinator.com/api/inbox?to=" + username + "&token=" + mailinatorApiKey, String.class);
        JSONObject messages = new JSONObject(jsonEmail);
        JSONArray receivedEmails = messages.getJSONArray("messages");

        Assert.assertEquals("No email was received in inbox " + username + " after 4 seconds. Check Mailinator to see if there is an email for that inbox.", 1, receivedEmails.length());

        JSONObject receivedEmail = (JSONObject) receivedEmails.get(0);

        String fullEmail = restTemplate.getForObject("https://api.mailinator.com/api/email?id=" + receivedEmail.getString("id") + "&token=" + mailinatorApiKey, String.class);
        JSONObject body = new JSONObject(fullEmail);
        return ((JSONObject)body.getJSONObject("data").getJSONArray("parts").get(0)).getString("body");
    }
}
