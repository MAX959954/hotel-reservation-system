package config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import hotels.HotelResponse;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import room.RoomResponse;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * Each cache gets a serializer bound to the exact type it stores, rather than one
     * generic serializer for everything.
     *
     * GenericJackson2JsonRedisSerializer was the obvious choice and it does not work here.
     * It leans on default typing (an "@class" property) to know what to rebuild, and that
     * typing is only emitted for non-final classes — so a value returned by Stream.toList(),
     * whose runtime class is a final immutable list, is written with no type id but read
     * back expecting one. The write succeeds and every subsequent read throws, which means
     * a cached endpoint returns 200 once and then 500 forever.
     *
     * Binding the type per cache removes the guesswork: no "@class" noise in the payload,
     * smaller entries, and the shape is fixed at compile time instead of at runtime.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = new ObjectMapper()
                // RoomResponse.createdAt is a LocalDateTime; without this module Jackson
                // cannot write it at all.
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .disableCachingNullValues();

        JavaType hotel = mapper.getTypeFactory().constructType(HotelResponse.class);
        JavaType hotelList = mapper.getTypeFactory()
                .constructCollectionType(List.class, HotelResponse.class);
        JavaType roomList = mapper.getTypeFactory()
                .constructCollectionType(List.class, RoomResponse.class);

        Map<String, RedisCacheConfiguration> caches = new HashMap<>();
        caches.put("hotelById", base.serializeValuesWith(pair(mapper, hotel)));
        for (String name : List.of("hotelsByCity", "hotelsByCountry", "hotelsByCompany", "hotelsByRating", "hotelsByType")) {
            caches.put(name, base.serializeValuesWith(pair(mapper, hotelList)));
        }
        // Availability turns over on every booking, cancel and check-in, so it gets a much
        // shorter window than hotel data.
        caches.put("roomsAvailability", base
                .entryTtl(Duration.ofSeconds(60))
                .serializeValuesWith(pair(mapper, roomList)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(caches)
                .build();
    }

    private static RedisSerializationContext.SerializationPair<Object> pair(ObjectMapper mapper, JavaType type) {
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> serializer =
                (RedisSerializer<Object>) (RedisSerializer<?>) new Jackson2JsonRedisSerializer<>(mapper, type);
        return RedisSerializationContext.SerializationPair.fromSerializer(serializer);
    }
}
