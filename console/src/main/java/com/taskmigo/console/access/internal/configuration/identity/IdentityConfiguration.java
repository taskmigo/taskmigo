package com.taskmigo.console.access.internal.configuration.identity;

import com.taskmigo.console.access.internal.application.identity.BootstrapAdministrator;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class IdentityConfiguration {
  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  ApplicationRunner administratorBootstrap(
      BootstrapAdministrator administrator, IdentityProperties properties) {
    return arguments -> administrator.provision(properties.bootstrap());
  }
}
