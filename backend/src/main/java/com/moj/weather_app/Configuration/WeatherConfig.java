package com.moj.weather_app.Configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration
public class WeatherConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public RestClient openWeatherRestClient(WeatherProperties properties) {
        return restClient(properties.openWeatherBaseUrl(), properties);
    }

    @Bean
    public RestClient autocompleteRestClient(WeatherProperties properties) {
        return restClient(properties.autocompleteBaseUrl(), properties);
    }

    private static RestClient restClient(String baseUrl, WeatherProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
