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

import org.apache.commons.codec.binary.Base64;
import org.junit.Assert;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestClient {

    private final RestTemplate restTemplate;
    private final String url;

    public TestClient(RestTemplate restTemplate, String url) {
        this.restTemplate = restTemplate;
        this.url = url;
    }

    public String getBasicAuthHeaderValue(String username, String password) {
        return "Basic " + new String(Base64.encodeBase64((username + ":" + password).getBytes()));
    }

    public String getClientAccessToken(String clientId, String clientSecret, String scope) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", getBasicAuthHeaderValue(clientId, clientSecret));

        MultiValueMap<String, String> postParameters = new LinkedMultiValueMap<String, String>();
        postParameters.add("grant_type", "client_credentials");
        postParameters.add("client_id", clientId);
        postParameters.add("scope", scope);
        postParameters.add("token_format", "opaque");

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(postParameters, headers);
        ResponseEntity<Map> exchange = restTemplate.exchange(url + "/oauth/token", HttpMethod.POST, requestEntity, Map.class);
        return exchange.getBody().get("access_token").toString();
    }

    public List<Map> getIdentityProviders(String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + token);
        headers.add("Accept", "application/json");
        MultiValueMap<String, String> postParameters = new LinkedMultiValueMap<String, String>();
        postParameters.add("rawConfig", "true");
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(postParameters, headers);
        ResponseEntity<List<Map>> exchange = restTemplate.exchange(url + "/identity-providers", HttpMethod.GET, requestEntity, new ParameterizedTypeReference<List<Map>>() {
        });
        return exchange.getBody();
    }

    public Map<String, Object> createIdentityProvider(String url, String token, Map<String, Object> json) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + token);
        headers.add("Accept", "application/json");
        headers.add("Content-Type", "application/json");
        HttpEntity<Map> requestEntity = new HttpEntity<>(json, headers);
        ResponseEntity<Map> exchange = restTemplate.exchange(url + "/identity-providers?rawConfig=true", HttpMethod.POST, requestEntity, Map.class);
        return exchange.getBody();
    }

    public Map<String, Object> updateIdentityProvider(String url, String token, String id, Map<String, Object> json) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + token);
        headers.add("Accept", "application/json");
        headers.add("Content-Type", "application/json");
        HttpEntity<Map> requestEntity = new HttpEntity<>(json, headers);
        ResponseEntity<Map> exchange = restTemplate.exchange(url + "/identity-providers/" + id + "?rawConfig=true", HttpMethod.PUT, requestEntity, Map.class);
        return exchange.getBody();
    }

    public void createScimClient(String adminAccessToken, String clientId) throws Exception {
        restfulCreate(adminAccessToken, "{" +
                "\"scope\":[\"uaa.none\"]," +
                "\"client_id\":\"" + clientId + "\"," +
                "\"client_secret\":\"scimsecret\"," +
                "\"resource_ids\":[\"oauth\"]," +
                "\"authorized_grant_types\":[\"client_credentials\"]," +
                "\"authorities\":[\"password.write\",\"scim.write\",\"scim.read\",\"oauth.approvals\"]" +
                "}", url + "/oauth/clients", null);
    }

    public void createPasswordClient(String adminAccessToken, String clientId, String clientSecret) {
        restfulCreate(adminAccessToken, "{\n" +
                "  \"scope\": [\n" +
                "    \"user_attributes\",\n" +
                "    \"openid\"\n" +
                "  ],\n" +
                "  \"client_id\": \"" + clientId + "\",\n" +
                "  \"client_secret\": \"" + clientSecret + "\",\n" +
                "  \"authorized_grant_types\": [\n" +
                "    \"password\"\n" +
                "  ]\n" +
                "}", url + "/oauth/clients", null);
    }

    public void createZoneAdminClient(String adminToken, String adminClientId, String adminClientSecret, String zoneId) {
        restfulCreate(adminToken,
                //language=JSON
                "{\n" +
                        "  \"authorities\": [\"uaa.admin\"],\n" +
                        "  \"authorized_grant_types\": [\"client_credentials\"],\n" +
                        "  \"client_id\": \"" + adminClientId + "\",\n" +
                        "  \"client_secret\": \"" + adminClientSecret + "\"\n" +
                        "}",
                url + "/oauth/clients",
                zoneId
        );
    }

    public void deleteClient(String adminToken, String clientId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + adminToken);

        HttpEntity<String> requestEntity = new HttpEntity<>(headers);
        restTemplate.exchange(url + "/oauth/clients/" + clientId, HttpMethod.DELETE, requestEntity, Map.class);
    }

    public void createUser(String scimAccessToken, String userName, String email, String password, Boolean verified) throws Exception {

        restfulCreate(scimAccessToken, "{" +
                "\"meta\":{\"version\":0,\"created\":\"2014-03-24T18:01:24.584Z\"}," +
                "\"userName\":\"" + userName + "\"," +
                "\"name\":{\"formatted\":\"Joe User\",\"familyName\":\"User\",\"givenName\":\"Joe\"}," +
                "\"emails\":[{\"value\":\"" + email + "\"}]," +
                "\"password\":\"" + password + "\"," +
                "\"active\":true," +
                "\"verified\":" + verified + "," +
                "\"schemas\":[\"urn:scim:schemas:core:1.0\"]" +
                "}", url + "/Users", null);
    }

    public void createZone(String identityAccessToken, String zoneId, String zoneName, String subdomain, String description) {
        restfulCreate(identityAccessToken, "{" +
                "\"id\":\"" + zoneId + "\"," +
                "\"name\":\"" + zoneName + "\"," +
                "\"subdomain\":\"" + subdomain + "\"," +
                "\"description\":\"" + description + "\"" +
                "}", url + "/identity-zones", null);
    }

    private void restfulCreate(String adminAccessToken, String json, String url, String zoneId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + adminAccessToken);
        headers.add("Accept", "application/json");
        headers.add("Content-Type", "application/json");
        if (zoneId != null) {
            headers.add("X-Identity-Zone-Id", zoneId);
        }

        HttpEntity<String> requestEntity = new HttpEntity<String>(json, headers);
        ResponseEntity<Void> exchange = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Void.class);
        Assert.assertEquals(HttpStatus.CREATED, exchange.getStatusCode());
    }


    public String extractLink(String messageBody) {
        Pattern linkPattern = Pattern.compile("<a href=\"(.*?)\">.*?</a>");
        Matcher matcher = linkPattern.matcher(messageBody);
        matcher.find();
        String encodedLink = matcher.group(1);
        return HtmlUtils.htmlUnescape(encodedLink);
    }

    public String getPasswordToken(String clientId, String clientSecret, String passcode) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", getBasicAuthHeaderValue(clientId, clientSecret));
        headers.add("Content-Type", "application/x-www-form-urlencoded");
        headers.add("Accept", "application/json");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "password");
        params.add("token_format", "token");
        params.add("passcode", passcode);
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);

        ResponseEntity<Map> exchange = restTemplate.exchange(url + "/oauth/token", HttpMethod.POST, requestEntity, Map.class);
        Map<String, Object> response = exchange.getBody();
        return (String) response.get("access_token");
    }

    public Map<String, Object> getUserInfo(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + token);
        headers.add("Accept", "application/json");

        HttpEntity<String> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<Map> exchange = restTemplate.exchange(url + "/userinfo", HttpMethod.GET, requestEntity, Map.class);
        return exchange.getBody();
    }
}

