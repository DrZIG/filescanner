package com.drzig.filescanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FileScannerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileScannerApplication.class, args);
    }
}
