package com.moj.weather_app.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(name = "favorite_location")
public class FavoriteLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "favorite_location_id_generator")
    @SequenceGenerator(name = "favorite_location_id_generator", sequenceName = "favorite_location_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "city_name", nullable = false)
    private String cityName;

    @Column(name = "country_name", nullable = false)
    private String countryName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id", nullable = false)
    private Users users;

}
