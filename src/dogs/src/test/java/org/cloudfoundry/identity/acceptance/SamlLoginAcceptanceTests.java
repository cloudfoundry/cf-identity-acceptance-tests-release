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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cloudfoundry.identity.acceptance.DefaultAcceptanceTestConfig.clickAndWait;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DefaultAcceptanceTestConfig.class)
class SamlLoginAcceptanceTests {

    @Value("${BASE_URL}")
    private String baseUrl;

    @Value("${PROTOCOL}")
    private String protocol;

    @Autowired
    private WebDriver webDriver;
    private String url;

    @BeforeEach
    @AfterEach
    void clearWebDriverOfCookies() {
        url = protocol + baseUrl;
        webDriver.get(protocol + baseUrl + "/logout.do");
        webDriver.manage().deleteAllCookies();
    }

    @Test
    void urlSamlPhpLoginAWS() {
        Assumptions.assumeTrue(url.contains(".identity.cf-app.com"), "This test is against AWS environment");
        samlPhpLogin("Log in with Simple SAML PHP URL");
    }

    private void samlPhpLogin(String linkText) {
        webDriver.get(protocol + baseUrl + "/login");
        assertThat(webDriver.getTitle()).isEqualTo("Cloud Foundry");
        webDriver.findElement(By.xpath("//a[text()='" + linkText + "']")).click();
        webDriver.findElement(By.xpath("//h2[text()='Enter your username and password']"));
        webDriver.findElement(By.name("username")).clear();
        webDriver.findElement(By.name("username")).sendKeys("marissa");
        webDriver.findElement(By.name("password")).sendKeys("koala");
        clickAndWait(webDriver, By.xpath("//input[@value='Login']"));
        assertThat(webDriver.findElement(By.xpath("//div[@class='dropdown-trigger']")).getText()).isEqualTo("marissa@test.org");
        assertThat(webDriver.findElement(By.cssSelector("h1")).getText()).contains("Where to?");
    }
}
