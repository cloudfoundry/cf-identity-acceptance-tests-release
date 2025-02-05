package org.cloudfoundry.identity.acceptance; /*******************************************************************************
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
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
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
}
