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

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.security.SecureRandom;

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
public class LoginAcceptanceTests {
    @Value("${BASE_URL}")
    private String baseUrl;
    @Value("${PROTOCOL}")
    private String protocol;

    @Value("${ADMIN_CLIENT_ID:admin}")
    private String adminClientId;

    @Value("${ADMIN_CLIENT_SECRET:adminsecret}")
    private String adminClientSecret;

    @Autowired
    private WebDriver webDriver;

    @Autowired
    private TestClient testClient;

    private String userName;
    private String scimClientId;
    private String adminClientToken;
    private String scimClientToken;

    @Before
    public void setUp() throws Exception {
        int randomInt = new SecureRandom().nextInt();

        adminClientToken = testClient.getOAuthAccessToken(adminClientId, adminClientSecret, "client_credentials", "clients.write");

        scimClientId = "acceptance-scim-" + randomInt;
        testClient.createScimClient(adminClientToken, scimClientId);

        scimClientToken = testClient.getOAuthAccessToken(scimClientId, "scimsecret", "client_credentials", "scim.read,scim.write");

        userName = "acceptance-" + randomInt + "@example.com";
        testClient.createUser(scimClientToken, userName, userName, "password", true);

        webDriver.get(protocol + baseUrl + "/logout.do");
    }

    @After
    public void tearDown() throws Exception {
        // TODO: Delete User
        // TODO: Delete SCIM Client
    }

    @Test
    public void testLogin() throws Exception {

        // Test failed login
        webDriver.get(protocol + baseUrl + "/login");

        webDriver.findElement(By.name("username")).sendKeys(userName);
        webDriver.findElement(By.name("password")).sendKeys("invalidpassword");
        webDriver.findElement(By.xpath("//input[@value='Sign in']")).click();

        assertThat(webDriver.findElement(By.cssSelector("h1")).getText(), Matchers.containsString("Welcome!"));

        // Test successful login
        webDriver.findElement(By.name("username")).sendKeys(userName);
        webDriver.findElement(By.name("password")).sendKeys("password");
        webDriver.findElement(By.xpath("//input[@value='Sign in']")).click();

        assertThat(webDriver.findElement(By.cssSelector("h1")).getText(), Matchers.containsString("Where to?"));

        // Test logout
        webDriver.get(protocol + baseUrl + "/logout.do");

        assertThat(webDriver.findElement(By.cssSelector("h1")).getText(), Matchers.containsString("Welcome!"));
    }
}
