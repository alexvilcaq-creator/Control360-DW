package com.control360;

import java.time.Clock;
import java.time.ZoneId;
import com.control360.application.Commerce;
import com.control360.application.Operations;
import com.control360.application.Store;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Control360Application {
    public static void main(String[] args) {
        SpringApplication.run(Control360Application.class, args);
    }

    @Bean
    Clock businessClock(@Value("${app.zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    @Bean
    Operations operations(Store store, Clock clock) {
        return new Operations(store, clock);
    }
    @Bean
    Commerce commerce(Store store, Clock clock) {
        return new Commerce(store, clock);
    }
}
