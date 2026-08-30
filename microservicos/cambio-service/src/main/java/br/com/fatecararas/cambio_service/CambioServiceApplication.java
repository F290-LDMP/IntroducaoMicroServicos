package br.com.fatecararas.cambio_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CambioServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CambioServiceApplication.class, args);
    }
}
