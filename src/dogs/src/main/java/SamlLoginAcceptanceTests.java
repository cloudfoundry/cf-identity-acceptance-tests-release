import org.hamcrest.Matchers;
import org.junit.After;
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

import static org.junit.Assert.assertEquals;
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
public class SamlLoginAcceptanceTests {
    @Value("${integration.test.base_url:http://localhost:8080/uaa}")
    private String baseUrl;

    @Autowired
    private WebDriver webDriver;

    @Before
    @After
    public void clearWebDriverOfCookies() throws Exception {
        webDriver.get(baseUrl + "/logout.do");
        webDriver.manage().deleteAllCookies();
    }

    @Test
    public void testXmlSamlPhpLogin() {
        samlPhpLogin("Log in with Simple SAML PHP");
    }

    @Test
    public void testUrlSamlPhpLogin() throws Exception {
        samlPhpLogin("Log in with Simple SAML PHP URL");
    }

    private void samlPhpLogin(String linkText) {
        webDriver.get(baseUrl + "/login");
        Assert.assertEquals("Cloud Foundry", webDriver.getTitle());
        webDriver.findElement(By.xpath("//a[text()='" + linkText + "']")).click();
        webDriver.findElement(By.xpath("//h2[contains(text(), 'Enter your username and password')]"));
        webDriver.findElement(By.name("username")).clear();
        webDriver.findElement(By.name("username")).sendKeys("marissa");
        webDriver.findElement(By.name("password")).sendKeys("koala");
        webDriver.findElement(By.xpath("//input[@value='Login']")).click();
        assertEquals("marissa@test.org", webDriver.findElement(By.xpath("//div[@class='dropdown-trigger']")).getText());
        assertThat(webDriver.findElement(By.cssSelector("h1")).getText(), Matchers.containsString("Where to?"));
    }
}
