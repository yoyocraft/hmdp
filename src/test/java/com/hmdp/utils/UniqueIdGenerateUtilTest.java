package com.hmdp.utils;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UniqueIdGenerateUtilTest {

    @Resource
    private UniqueIdGenerateUtil idGenerateUtil;

    private final ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void generateId() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                Long id = idGenerateUtil.generateId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long endTime = System.currentTimeMillis();
        System.out.println("last time = " + (endTime - startTime));
    }
}