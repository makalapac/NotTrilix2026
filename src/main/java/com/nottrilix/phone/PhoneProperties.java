package com.nottrilix.phone;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phone channel (application.yml -> nottrilix.phone.*).
 * demoCaller: a phone number that maps to the first seeded customer, so the presenter's own phone can call the demo
 * without putting a real number into the seed data (set NOTTRILIX_DEMO_CALLER in the environment, never in the repo).
 */
@ConfigurationProperties(prefix = "nottrilix.phone")
public record PhoneProperties(String demoCaller) {
}
