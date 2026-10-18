package com.moj.weather_app.Repository;

import com.moj.weather_app.Entity.FavoriteLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FavoriteLocationRepository extends JpaRepository <FavoriteLocation, Long> {
    List<FavoriteLocation> findByUsers_id(UUID userId);
    int deleteByUsers_idAndCityNameAndCountryName(UUID userId, String cityName, String countryName);
    boolean existsByUsers_idAndCityNameAndCountryName(UUID userId, String cityName, String countryName);
}
