package com.sharvary.billing.demo;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class DemoStartup {

    private final DemoSeeder seeder;

    public DemoStartup(DemoSeeder seeder) {
        this.seeder = seeder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        seeder.reseed();
    }
}
