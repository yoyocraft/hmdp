package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.DigestUtils;

@SpringBootTest
class HmDianPingApplicationTests {

    @Test
    void testPassword() {
        String password = "12345678";
        String encryptedPassword = DigestUtils.md5DigestAsHex(("codejuzi" + password).getBytes());
        System.out.println("encryptedPassword = " + encryptedPassword);
    }
}
