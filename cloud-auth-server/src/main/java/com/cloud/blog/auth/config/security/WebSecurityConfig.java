package com.cloud.blog.auth.config.security;

import com.cloud.blog.auth.filter.QQAuthenticationFilter;
import com.cloud.blog.auth.handler.FuryAuthFailureHandler;
import com.cloud.blog.auth.handler.FuryAuthSuccessHandler;
import com.cloud.blog.auth.handler.FuryLogoutSuccessHandler;
import com.cloud.blog.auth.handler.RestAuthAccessDeniedHandler;
import com.cloud.blog.auth.mapper.sys.BlogUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;


/**
 * @ClassName WebSecurityConfig
 * @Description security
 * @Author Ailuoli
 * @Date 2019/12/17 10:36
 **/

@Order(1)
@Configuration
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private DataSource dataSource;


    @Autowired
    private UserDetailsService baseUserDetailsService;

    @Autowired
    private BlogUserMapper blogUserMapper;


    @Value("${security.remember.time}")
    private Integer rememberTime;

    @Value("${security.oauth2.secret}")
    private String secret;


    /*
     自定义登录成功处理器
      */
    @Autowired
    private FuryAuthSuccessHandler furyAuthSuccessHandler;

    /**
     * 自定义登录失败处理器
     */
    @Autowired
    private FuryAuthFailureHandler furyAuthFailureHandler;

    /**
     * 自定义的注销成功的处理器
     */
    @Autowired
    private FuryLogoutSuccessHandler logoutSuccessHandler;

    /**
     * 自定义注册没有权限的处理器
     */
    @Autowired
    private RestAuthAccessDeniedHandler restAuthAccessDeniedHandler;

    /*
配置TokenRepository
 */
    @Bean
    public PersistentTokenRepository persistentTokenRepository() {
        JdbcTokenRepositoryImpl jdbcTokenRepository = new JdbcTokenRepositoryImpl();
        jdbcTokenRepository.setDataSource(dataSource);
//        jdbcTokenRepository.setCreateTableOnStartup(true);
        return jdbcTokenRepository;
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Pbkdf2PasswordEncoder(secret);
    }

    @Bean
    @Primary
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    /**
     * @Author myk
     * @Description //用户验证
     * @Date 10:05 2019/12/24
     * @Param [auth]
     * @return void
     **/
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(baseUserDetailsService).passwordEncoder(passwordEncoder());
    }

    /**
     * 允许匿名访问所有接口 主要是 oauth 接口
     * @param http
     * @throws Exception
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .csrf().disable() //关闭CSRF
                .authorizeRequests()
                .antMatchers("/oauth/authorize","/security/**").permitAll()
                .antMatchers("/js/**","/html/**","/img/**","/font/**","/css/**","/layer/**").permitAll()
                .anyRequest()
                .access("@securityAuthorityDecision.hasPermission(request,authentication)")
                .and()
                .addFilterAt(qqAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .formLogin().loginPage("/html/login.html").permitAll()
                .loginProcessingUrl("/security/login")
                .and()
                .logout().logoutUrl("/security/logout")
                .logoutSuccessHandler(logoutSuccessHandler)
                .and()
                .exceptionHandling().accessDeniedHandler(restAuthAccessDeniedHandler)
                .and()
                .rememberMe()
                .tokenRepository(persistentTokenRepository())
                //两个星期
                .tokenValiditySeconds(rememberTime);
        http
                .sessionManagement().invalidSessionUrl("/session/invalid");
        http
                .headers().frameOptions().sameOrigin();

    }

    /**
     * 自定义 QQ登录 过滤器
     */
    private QQAuthenticationFilter qqAuthenticationFilter() {
        QQAuthenticationFilter authenticationFilter = new QQAuthenticationFilter("/qqlogin/success");
        authenticationFilter.setAuthenticationSuccessHandler(furyAuthSuccessHandler);
        authenticationFilter.setAuthenticationManager(new QQAuthenticationManager(blogUserMapper));
        return authenticationFilter;
    }

}