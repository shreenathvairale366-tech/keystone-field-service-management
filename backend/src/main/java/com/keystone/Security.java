package com.keystone;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class JwtService {
  private final SecretKey key; private final long hours;
  JwtService(@Value("${keystone.jwt-secret}") String s, @Value("${keystone.jwt-hours}") long h) {
    key = Keys.hmacShaKeyFor(s.getBytes()); hours = h;
  }
  String create(User u) {
    return Jwts.builder().subject(u.username).claim("role", u.role.name())
        .expiration(new Date(System.currentTimeMillis() + hours * 3_600_000)).signWith(key).compact();
  }
  Claims parse(String t) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(t).getPayload(); }
}

@Component
class JwtFilter extends OncePerRequestFilter {
  private final JwtService jwt;
  JwtFilter(JwtService j) { jwt = j; }
  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String h = req.getHeader("Authorization");
    if (h != null && h.startsWith("Bearer ")) {
      try {
        Claims c = jwt.parse(h.substring(7));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            c.getSubject(), null, List.of(new SimpleGrantedAuthority("ROLE_" + c.get("role")))));
      } catch (JwtException e) { /* invalid token: request stays unauthenticated -> 401/403 */ }
    }
    chain.doFilter(req, res);
  }
}

@Configuration @EnableMethodSecurity
class SecurityConfig {
  @Bean SecurityFilterChain chain(HttpSecurity http, JwtFilter f) throws Exception {
    return http.csrf(c -> c.disable()).cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a.requestMatchers("/api/auth/**").permitAll().anyRequest().authenticated())
        .addFilterBefore(f, UsernamePasswordAuthenticationFilter.class).build();
  }
  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
