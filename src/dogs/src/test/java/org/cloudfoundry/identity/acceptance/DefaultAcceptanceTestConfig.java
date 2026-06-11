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

import jakarta.annotation.PostConstruct;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.FluentWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class DefaultAcceptanceTestConfig {
    static final Duration IMPLICIT_WAIT_TIME = Duration.ofSeconds(30L);
    static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(40L);
    static final Duration SCRIPT_TIMEOUT = Duration.ofSeconds(30L);

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean(destroyMethod = "quit")
    public WebDriver webDriver() {
        System.setProperty("webdriver.chrome.logfile", "/tmp/chromedriver.log");
        System.setProperty("webdriver.chrome.verboseLogging", "true");
        System.setProperty("webdriver.http.factory", "jdk-http-client");

        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless",
                "--disable-web-security",
                "--ignore-certificate-errors",
                "--allow-running-insecure-content",
                "--allow-insecure-localhost",
                "--no-sandbox",
                "--disable-gpu",
                "--remote-allow-origins=*"
        );

        options.setAcceptInsecureCerts(true);

        ChromeDriver driver = new ChromeDriver(options);
        driver.manage().timeouts()
                .implicitlyWait(IMPLICIT_WAIT_TIME)
                .pageLoadTimeout(PAGE_LOAD_TIMEOUT)
                .scriptTimeout(SCRIPT_TIMEOUT);
        return driver;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public TestClient testClient(RestTemplate restTemplate,
                                 @Value("${PROTOCOL:https://}") String protocol,
                                 @Value("${BASE_URL}") String baseUrl) {
        return new TestClient(restTemplate, protocol + baseUrl);
    }

    @PostConstruct
    public void init() {
        SSLValidationDisabler.disableSSLValidation();
    }

    /**
     * Click on the element, and wait for a page reload. This is accomplished by waiting
     * for the reference to the clicked element to become "stale", ie not be in the current
     * DOM anymore, throwing {@link StaleElementReferenceException}. Sometimes, the Chrome driver
     * throws a 500 error, which body contains code -32000, so we use that as a signal as well.
     */
    protected static void clickAndWait(WebDriver webDriver, By locator) {
        var clickableElement = webDriver.findElement(locator);
        clickableElement.click();

        new FluentWait<>(webDriver).withTimeout(Duration.ofSeconds(5))
                .pollingEvery(Duration.ofMillis(100))
                .withMessage(() -> "Waiting for navigation after clicking on [%s]. Current URL [%s].".formatted(locator, webDriver.getCurrentUrl()))
                .until(_ -> {
                    try {
                        clickableElement.isDisplayed();
                        return false;
                    } catch (StaleElementReferenceException _) {
                        return true;
                    } catch (WebDriverException e) {
                        return e.getMessage().contains("-32000");
                    }
                });
    }
}
