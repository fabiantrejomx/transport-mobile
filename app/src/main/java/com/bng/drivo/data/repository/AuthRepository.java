package com.bng.drivo.data.repository;

public interface AuthRepository {

    boolean isLoggedIn();

    boolean login(String email, String password);

    boolean register(String name, String email, String phone, String password);

    void logout();
}
