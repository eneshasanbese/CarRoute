package com.eneshasanbese.starter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = { "com.eneshasanbese" })
@EntityScan(basePackages = { "com.eneshasanbese.entity" })
@EnableJpaRepositories(basePackages = { "com.eneshasanbese.repository" })
public class CarRouteApplication {

	public static void main(String[] args) {
		SpringApplication.run(CarRouteApplication.class, args);
	}

}
