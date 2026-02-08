package com.kinsware.file;

import com.kinsware.file.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@SpringBootApplication
@EnableSwagger2
@EnableAsync
@EnableConfigurationProperties(AppProperties.class)
public class HandwriteApplication {
	public static void main(String[] args) {
		SpringApplication.run(HandwriteApplication.class, args);
	}
}
