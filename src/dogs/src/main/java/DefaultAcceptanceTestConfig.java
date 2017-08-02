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
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.RefreshHandler;
import com.gargoylesoftware.htmlunit.WebClient;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URL;

@Configuration
public class DefaultAcceptanceTestConfig {
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean(destroyMethod = "quit")
    public WebDriver webDriver() {
        HtmlUnitDriver driver = new HtmlUnitDriver(true) {
            @Override
            protected WebClient modifyWebClient(WebClient client) {
                client.setRefreshHandler(new RefreshHandler() {
                    @Override
                    public void handleRefresh(Page page, URL url, int seconds) throws IOException {
                        //do nothing
                    }
                });
                return super.modifyWebClient(client);
            }
        };

        return driver;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public TestClient testClient(RestTemplate restTemplate,
                                 @Value("${PROTOCOL}") String protocol,
                                 @Value("${BASE_URL}") String baseUrl) {
        return new TestClient(restTemplate, protocol + baseUrl);
    }

    @PostConstruct
    public void init() {
        SSLValidationDisabler.disableSSLValidation();
    }
}
