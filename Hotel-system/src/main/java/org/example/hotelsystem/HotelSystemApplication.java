package org.example.hotelsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {
        "org.example.hotelsystem",
        "config",
        "user",
        "companyuser",
        "companies",
        "booking",
        "payment",
        "hotels",
        "room",
        "roomtype",
        "reviews",
        "exception"
})
@EnableJpaRepositories(basePackages = {
        "user",
        "companyuser",
        "companies",
        "booking",
        "payment",
        "hotels",
        "room",
        "roomtype",
        "reviews"
})
@EntityScan(basePackages = {
        "user",
        "companyuser",
        "companies",
        "booking",
        "payment",
        "hotels",
        "room",
        "roomtype",
        "reviews"
})
public class HotelSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(HotelSystemApplication.class, args);
	}

}
