package org.lixiyun.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class ServerApplicationTests {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void testPasswordEncoder() {
        String password = "123456";
        String encodedPassword = passwordEncoder.encode(password);
        System.out.println(encodedPassword);
    }


    @Test
    void contextLoads() {
    }

}
