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
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cloudfoundry.identity.acceptance.DefaultAcceptanceTestConfig.clickAndWait;

@SpringJUnitConfig(classes = DefaultAcceptanceTestConfig.class)
class LdapLoginAcceptanceTests {

    @Value("${BASE_URL}")
    private String baseUrl;

    @Value("${PROTOCOL}")
    private String protocol;

    @Autowired
    private WebDriver webDriver;

    @BeforeEach
    @AfterEach
    void clearWebDriverOfCookies() {
        webDriver.get(protocol + baseUrl + "/logout.do");
    }

    @Test
    void ldap_login_IsSuccessful() {
        webDriver.findElement(By.name("username")).sendKeys("marissa-ldap");
        webDriver.findElement(By.name("password")).sendKeys("marissa-ldap");
        clickAndWait(webDriver, By.xpath("//input[@value='Sign in']"));

        assertThat(webDriver.findElement(By.cssSelector("h1")).getText()).contains("Where to?");

        webDriver.get(protocol + baseUrl + "/logout.do");

        assertThat(webDriver.findElement(By.cssSelector("h1")).getText()).contains("Welcome!");
    }
}
