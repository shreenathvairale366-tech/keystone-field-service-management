package com.keystone;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class KeystoneApplication {
  public static void main(String[] a) { SpringApplication.run(KeystoneApplication.class, a); }

  /** Demo users, created once on an empty database. Change these passwords. */
  @Bean CommandLineRunner seed(UserRepo users, PasswordEncoder pe) {
    return args -> {
      if (users.count() > 0) return;
      users.save(new User("admin", pe.encode("admin123"), "Alex Admin", Role.ADMIN));
      users.save(new User("dispatch", pe.encode("dispatch123"), "Dana Dispatcher", Role.DISPATCHER));
      users.save(new User("tech1", pe.encode("tech123"), "Tom Technician", Role.TECHNICIAN));
      users.save(new User("customer1", pe.encode("cust123"), "Acme Facilities", Role.CUSTOMER));
    };
  }
}
