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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cloudfoundry.identity.acceptance.DefaultAcceptanceTestConfig.clickAndWait;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DefaultAcceptanceTestConfig.class)
class LoginAcceptanceTests {

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

    @BeforeEach
    void setUp() {
        int randomInt = new SecureRandom().nextInt();

        String adminClientToken = testClient.getClientAccessToken(adminClientId, adminClientSecret, "clients.write,uaa.admin");

        String scimClientId = "acceptance-scim-" + randomInt;
        testClient.createScimClient(adminClientToken, scimClientId);

        String scimClientToken = testClient.getClientAccessToken(scimClientId, "scimsecret", "scim.read,scim.write");

        userName = "acceptance-" + randomInt + "@example.com";
        testClient.createUser(scimClientToken, userName, userName, "password", true);

        webDriver.get(protocol + baseUrl + "/logout.do");
    }

    @AfterEach
    void tearDown() throws Exception {
        // TODO: Delete User & SCIM Client
    }

    @Test
    void login() {

        // Test failed login
        webDriver.get(protocol + baseUrl + "/login");

        webDriver.findElement(By.name("username")).sendKeys(userName);
        webDriver.findElement(By.name("password")).sendKeys("invalidpassword");
        clickAndWait(webDriver, By.xpath("//input[@value='Sign in']"));

        assertThat(webDriver.findElement(By.cssSelector("h1")).getText()).contains("Welcome!");

        // Test successful login
        webDriver.findElement(By.name("username")).sendKeys(userName);
        webDriver.findElement(By.name("password")).sendKeys("password");
        clickAndWait(webDriver, By.xpath("//input[@value='Sign in']"));

        assertThat(webDriver.findElement(By.cssSelector("h1")).getText()).contains("Where to?");

        // Test logout
        webDriver.get(protocol + baseUrl + "/logout.do");

        assertThat(webDriver.findElement(By.cssSelector("h1")).getText()).contains("Welcome!");
    }
}
