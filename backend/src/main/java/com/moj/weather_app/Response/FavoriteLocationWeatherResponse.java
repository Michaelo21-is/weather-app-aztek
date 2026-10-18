package com.moj.weather_app.Response;

public record FavoriteLocationWeatherResponse(String cityName, String countryName, WeatherResponse weather) {
}
