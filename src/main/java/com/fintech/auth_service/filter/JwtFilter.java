package com.fintech.auth_service.filter;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import org.springframework.web.filter.OncePerRequestFilter;

import com.fintech.auth_service.config.MyUserDetailService;
import com.fintech.auth_service.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/*
    When building web applications with Spring Boot, 
    you often need to perform certain actions for each HTTP request. 
    However, in some cases, the same request might be processed multiple times due to internal forwarding or inclusion, 
    leading to redundant executions of your logic. Spring provides the OncePerRequestFilter to handle such scenarios, 
    which ensures your filter logic is executed only once per request.
*/
/*
USE CASE:
    Logging Requests: Ensuring request details are logged only once per request, regardless of internal forwarding or inclusion.
    Security Filters: Checking authentication tokens or user permissions once per request to avoid redundant security checks.
    Performance Monitoring: Collecting performance metrics for each request, such as recording the time taken to process a request, ensuring accurate metrics.
    Cross-Site Request Forgery (CSRF) Protection: Checking and validating CSRF tokens once per request to ensure the request’s integrity.
    Custom Header Injection: Injecting custom headers into the response once per request to ensure headers are added consistently.
*/
public class JwtFilter extends OncePerRequestFilter {

    private JwtService jwtService;
    private MyUserDetailService myUserDetailService;

    public JwtFilter(JwtService jwtService, MyUserDetailService myUserDetailService) {
        this.jwtService = jwtService;
        this.myUserDetailService = myUserDetailService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Bước 1: Lấy header Authorization
        String authHeader = request.getHeader("Authorization");

        // Bước 2: Nếu không có hoặc sai format -> bỏ qua, để request đi tiếp (sẽ bị chặn ở SecurityConfig nếu cần auth)
        if(authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Bước 3: Cắt bỏ "Bearer " để lấy token thuần
        final String jwt = authHeader.substring(7);

        // Bước 4: Lấy username từ token
        final String userName = jwtService.extractUsername(jwt);

        // Bước 5: Nếu có username và chưa được authenticate trong context hiện tại
        if(userName != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = myUserDetailService.loadUserByUsername(userName);

            if(jwtService.isTokenValid(userName, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                // Bước 7: Set vào SecurityContext -> từ đây Controller mới biết "user hiện tại là ai"
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);


    }
    
}
