package com.shortlink;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.shortlink.mapper")
@SpringBootApplication
public class ShortlinkApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShortlinkApplication.class, args);
	}

}
