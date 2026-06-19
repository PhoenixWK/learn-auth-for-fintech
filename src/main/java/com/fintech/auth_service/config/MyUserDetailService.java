package com.fintech.auth_service.config;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.fintech.auth_service.domain.User;
import com.fintech.auth_service.repository.UserRepository;

@Service
public class MyUserDetailService implements UserDetailsService {

    private UserRepository userRepository;

    public MyUserDetailService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User fetchedUser = userRepository.findByEmail(username)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));

        return new MyUserDetails(fetchedUser);
    }
    
}
