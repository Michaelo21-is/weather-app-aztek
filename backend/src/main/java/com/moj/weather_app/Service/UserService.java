package com.moj.weather_app.Service;

import com.moj.weather_app.Entity.Users;
import com.moj.weather_app.Repository.UsersRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class UserService {
    private final UsersRepository usersRepository;
    public UserService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }
    public String getNickname(UUID userId){
        Users user = usersRepository.findById(userId).orElseThrow(()-> new ResponseStatusException(HttpStatus.UNAUTHORIZED,"user is not loged in"));
        return user.getNickname();
    }
}
