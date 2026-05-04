package com.cacanode.api.auth.service;

import com.cacanode.api.auth.dto.request.RegisterRequest;
import com.cacanode.api.auth.dto.response.AuthResponse;

public interface AuthService {

    boolean isEmailExist(String email);

    AuthResponse register(RegisterRequest req);

}
