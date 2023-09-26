package com.redhat.qe.openjdk.springboot.tomcat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SampleTomcatApplication {
	public static void main(final String[] args) throws Exception {
		SpringApplication.run(SampleTomcatApplication.class, args);
	}
}
