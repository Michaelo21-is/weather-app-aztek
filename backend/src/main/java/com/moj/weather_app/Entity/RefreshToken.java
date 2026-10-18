package com.moj.weather_app.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "refresh_token")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "jwt_id_generator")
    @SequenceGenerator(name = "jwt_id_generator", sequenceName = "jwt_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "refresh_token", nullable = false)
    private String refreshToken;

    @Column(name = "expiration_date", nullable = false )
    private Instant expirationDate;

    @OneToOne
    @JoinColumn(name = "users_id")
    private Users users;
}
