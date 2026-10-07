package com.campusfix.config;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import java.util.List;
@Configuration @EnableWebSecurity
public class SecurityConfig {
 @Bean public PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean public SecurityFilterChain filterChain(HttpSecurity http)throws Exception{
  http.cors(c->{}).csrf(c->c.disable()).authorizeHttpRequests(a->a
   .requestMatchers("/","/api/auth/**","/uploads/**").permitAll()
   .requestMatchers("/api/student/**").hasRole("STUDENT").requestMatchers("/api/staff/**").hasRole("STAFF")
   .requestMatchers("/api/admin/**").hasRole("ADMIN").requestMatchers("/api/me","/api/notifications/**","/api/profile/**").authenticated().anyRequest().permitAll())
   .formLogin(f->f.loginProcessingUrl("/api/auth/login").successHandler((q,r,x)->{r.setStatus(200);r.setContentType("application/json");r.getWriter().write("{\"success\":true}");})
    .failureHandler((q,r,x)->{r.setStatus(401);r.setContentType("application/json");r.getWriter().write("{\"success\":false,\"message\":\"Invalid username or password\"}");}))
   .logout(l->l.logoutUrl("/api/auth/logout").logoutSuccessHandler((q,r,x)->r.setStatus(204)));
  return http.build();
 }
 @Bean public CorsConfigurationSource corsConfigurationSource(){CorsConfiguration c=new CorsConfiguration();c.setAllowedOrigins(List.of("http://localhost:5173","http://127.0.0.1:5173"));c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("*"));c.setAllowCredentials(true);UrlBasedCorsConfigurationSource s=new UrlBasedCorsConfigurationSource();s.registerCorsConfiguration("/**",c);return s;}
}
