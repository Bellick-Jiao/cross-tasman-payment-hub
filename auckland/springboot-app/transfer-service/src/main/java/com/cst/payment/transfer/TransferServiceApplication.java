package com.cst.payment.transfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jms.annotation.EnableJms;

@SpringBootApplication
@EnableJms
public class TransferServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransferServiceApplication.class, args);
    }
}