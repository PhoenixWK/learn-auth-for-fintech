package com.fintech.auth_service.config;

import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fintech.auth_service.domain.User;

public class MyUserDetails implements UserDetails {
    private User user;

    public MyUserDetails(User newUser) {
        this.user = newUser;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // TODO Auto-generated method stub
        //throw new UnsupportedOperationException("Unimplemented method 'getAuthorities'");
        return List.of(new SimpleGrantedAuthority(user.getRole().toString()));
    }


    @Override
    public @Nullable String getPassword() {
        // TODO Auto-generated method stub
       // throw new UnsupportedOperationException("Unimplemented method 'getPassword'");
       
       return user.getPassword_hash();
    }

    @Override
    public String getUsername() {
        // TODO Auto-generated method stub
        //throw new UnsupportedOperationException("Unimplemented method 'getUsername'");
        return user.getEmail();
    }
}
