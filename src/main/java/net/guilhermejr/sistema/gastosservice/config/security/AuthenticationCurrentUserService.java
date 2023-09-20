package net.guilhermejr.sistema.gastosservice.config.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationCurrentUserService {

    public UserDetailsImpl getCurrentUser() {
        return (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

}
