package config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves uploaded avatars from disk at /uploads/** — see user.AvatarStorageService. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.uploads.dir:/app/uploads}")
    private String uploadsDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // A file: resource location must end in a slash to be treated as a directory.
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadsDir.replace('\\', '/') + "/");
    }
}
