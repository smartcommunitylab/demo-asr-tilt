package it.smartcommunity.speech.speechbuilder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpeechBuilderApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpeechBuilderApplication.class, args);
	}
}
