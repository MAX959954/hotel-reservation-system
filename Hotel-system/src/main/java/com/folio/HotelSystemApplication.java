package com.folio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Backs BookingLifecycleScheduler/PaymentLifecycleScheduler (auto-cancelling abandoned
// PENDING bookings, flagging no-shows, expiring stale PENDING payments) — those beans
// are also individually gated by app.scheduling.enabled, off in the test profile.
@EnableScheduling
// Every feature package lives under com.folio, so the default component, entity and
// repository scanning of @SpringBootApplication finds them all.
@SpringBootApplication
public class HotelSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(HotelSystemApplication.class, args);
	}

}
