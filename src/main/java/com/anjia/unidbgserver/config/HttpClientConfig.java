package com.anjia.unidbgserver.config;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

@Configuration
public class HttpClientConfig {

    @Value("${application.http-client.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${application.http-client.read-timeout-ms:15000}")
    private int readTimeoutMs;

    @Value("${application.http-client.max-connections:50}")
    private int maxConnections;

    @Value("${application.http-client.max-connections-per-route:20}")
    private int maxConnectionsPerRoute;

    @Bean
    public RestTemplate restTemplate() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxConnections);
        connectionManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);
        connectionManager.setValidateAfterInactivity(30000);

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(connectTimeoutMs)
            .setSocketTimeout(readTimeoutMs)
            .setConnectionRequestTimeout(connectTimeoutMs)
            .build();

        CloseableHttpClient httpClient = HttpClientBuilder.create()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .evictIdleConnections(60, TimeUnit.SECONDS)
            .setConnectionTimeToLive(120, TimeUnit.SECONDS)
            .disableCookieManagement()
            .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        factory.setConnectionRequestTimeout(connectTimeoutMs);

        return new RestTemplate(factory);
    }
}
