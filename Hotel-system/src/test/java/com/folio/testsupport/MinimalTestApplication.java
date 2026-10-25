package com.folio.testsupport;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

// Context root for @WebMvcTest slices outside com.folio. Carries
// no @ComponentScan on purpose: the real app's base packages mix in @Service/
// @Repository beans (needing a DataSource) and JwtAuthFilter (needing JwtService,
// UserServiceImpl), none of which a controller slice can or should provide.
// Each test instead pulls in exactly the controller + advice classes it needs
// via @ContextConfiguration(classes = ...), bypassing scanning entirely.
@SpringBootConfiguration
@EnableAutoConfiguration
public class MinimalTestApplication {
}
