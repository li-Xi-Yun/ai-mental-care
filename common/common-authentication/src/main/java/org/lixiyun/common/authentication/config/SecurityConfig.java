package org.lixiyun.common.authentication.config;

import lombok.RequiredArgsConstructor;
import org.lixiyun.common.authentication.filer.JwtAuthenticationTokenFilter;
import org.lixiyun.common.authentication.handler.AuthenticationHandler;
import org.lixiyun.common.authentication.handler.LoginSuccessHandler;
import org.lixiyun.common.authentication.handler.PermissionDeniedHandler;
import org.lixiyun.common.authentication.oauth2.CustomOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // 创建BCryptPasswordEncoder，并注入容器，这里的加密因子默认为10,
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    private final JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter;

    private final AuthenticationHandler authenticationEntryPoint; // 认证异常处理器

    private final PermissionDeniedHandler accessDeniedHandler; // 授权异常处理器

    private final LoginSuccessHandler loginSuccessHandler;  // 注入自定义的第三方登录成功处理器

    private final CustomOAuth2UserService customOAuth2UserService;


    // 设置放行，一般这里设置认证的放行路径，而权限的判定一般使用注解在方法上进行设置
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 配置CORS跨域策略
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // authorizeHttpRequests：配置请求授权规则
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // 放行预检请求
                        .anyRequest().permitAll() // 允许所有请求
                );
        // 禁用HTTP Session机制，使应用完全无状态。
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS) );
        // 匿名用户支持,为未认证请求自动创建匿名身份，避免 SecurityContextHolder 为空。
//        http.anonymous(anon -> anon.principal("guest"));
        // 阻止默认的登录功能
        http.formLogin(AbstractHttpConfigurer::disable);
        // 阻止默认的注销功能
        http.logout(AbstractHttpConfigurer::disable);
        // 添加过滤器
        http.addFilterBefore(jwtAuthenticationTokenFilter, UsernamePasswordAuthenticationFilter.class);
        // 禁用CSRF,仅限API场景
        http.csrf(AbstractHttpConfigurer::disable);
        // 覆盖默认的异常处理
        http.exceptionHandling(handling -> handling
                .authenticationEntryPoint(authenticationEntryPoint) // 认证失败时触发
                .accessDeniedHandler(accessDeniedHandler)          // 权限不足时触发
        );
        // 配置OAuth2,将自定义的第三方回调处理添加到OAuth2登录流程中
        http.oauth2Login(oauth2 -> oauth2.userInfoEndpoint(userInfo -> userInfo.userService((OAuth2UserService<OAuth2UserRequest, OAuth2User>) customOAuth2UserService))
                // 使用您的自定义成功处理器
                .successHandler(loginSuccessHandler));

        return http.build();
    }


    /**
     * 把AuthenticationManager注入容器，因为我们要调用authenticate方法进行认证
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("*")); // 请替换为你的前端实际域名，生产环境不要用 "*"
        configuration.setAllowedMethods(Arrays.asList("*"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("X-Custom-Header")); // 如果需要，暴露自定义响应头给前端
        configuration.setAllowCredentials(false); // 如果前端需要发送Cookie或Authorization头，此项必须为true
        configuration.setMaxAge(3600L); // 预检请求的缓存时间（秒）

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // 对所有路径应用CORS配置
        return source;
    }

}