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

import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = DefaultAcceptanceTestConfig.class)
public class LdapLoginAcceptanceTests {


    @Value("${BASE_URL}")
    private String baseUrl;

    @Value("${PROTOCOL}")
    private String protocol;

    @Autowired
    private WebDriver webDriver;

    @Before
    @After
    public void clearWebDriverOfCookies() {
        webDriver.get(protocol + baseUrl + "/logout.do");
    }

    @Test
    public void ldap_login_IsSuccessful() {
        webDriver.findElement(By.name("username")).sendKeys("marissa-ldap");
        webDriver.findElement(By.name("password")).sendKeys("marissa-ldap");
        webDriver.findElement(By.xpath("//input[@value='Sign in']")).click();

        assertThat(webDriver.findElement(By.cssSelector("h1")).getText(), Matchers.containsString("Where to?"));

        webDriver.get(protocol + baseUrl + "/logout.do");

        assertThat(webDriver.findElement(By.cssSelector("h1")).getText(), Matchers.containsString("Welcome!"));
    }
}
