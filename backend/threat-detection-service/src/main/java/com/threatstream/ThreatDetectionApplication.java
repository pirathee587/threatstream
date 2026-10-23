package com.threatstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ThreatDetectionApplication {
  public static void main(String[] args) { SpringApplication.run(ThreatDetectionApplication.class, args); }
}
