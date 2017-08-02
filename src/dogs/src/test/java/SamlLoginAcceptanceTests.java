/*******************************************************************************
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
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = DefaultAcceptanceTestConfig.class)
public class SamlLoginAcceptanceTests {
    @Value("${BASE_URL}")
    private String baseUrl;

    @Value("${PROTOCOL}")
    private String protocol;

    @Autowired
    private WebDriver webDriver;
    private String url;

    @Before
    @After
    public void clearWebDriverOfCookies() throws Exception {
        url = protocol + baseUrl;
        webDriver.get(protocol + baseUrl + "/logout.do");
        webDriver.manage().deleteAllCookies();
    }

    @Test
    public void testUrlSamlPhpLoginAWS() throws Exception {
        Assume.assumeTrue("This test is against AWS environment", url.contains(".identity.cf-app.com"));
        samlPhpLogin("Log in with Simple SAML PHP URL");
    }

    private void samlPhpLogin(String linkText) {
        webDriver.get(protocol + baseUrl + "/login");
        Assert.assertEquals("Cloud Foundry", webDriver.getTitle());
        webDriver.findElement(By.xpath("//a[text()='" + linkText + "']")).click();
        webDriver.findElement(By.xpath("//h2[text()='Enter your username and password']"));
        webDriver.findElement(By.name("username")).clear();
        webDriver.findElement(By.name("username")).sendKeys("marissa");
        webDriver.findElement(By.name("password")).sendKeys("koala");
        webDriver.findElement(By.xpath("//input[@value='Login']")).click();
        assertEquals("marissa@test.org", webDriver.findElement(By.xpath("//div[@class='dropdown-trigger']")).getText());
        assertThat(webDriver.findElement(By.cssSelector("h1")).getText(), Matchers.containsString("Where to?"));
    }
}
