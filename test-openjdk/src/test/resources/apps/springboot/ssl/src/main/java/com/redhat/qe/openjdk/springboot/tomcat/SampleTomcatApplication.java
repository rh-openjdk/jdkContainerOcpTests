package com.redhat.qe.openjdk.springboot.tomcat;

import org.apache.catalina.connector.Connector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;

import com.redhat.qe.openjdk.springboot.tomcat.ssl.ConnectionParameterServlet;

/**
 * @author Radek Koubsky (radekkoubsky@gmail.com)
 */
@SpringBootApplication
public class SampleTomcatApplication {
	public static void main(final String[] args) throws Exception {
		SpringApplication.run(SampleTomcatApplication.class, args);
	}

	@Bean
	public Integer port() {
		return 8080;
	}

	@Bean
	public ServletWebServerFactory servletContainer() {
		final TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
		tomcat.addAdditionalTomcatConnectors(createStandardConnector());
		return tomcat;
	}


	private Connector createStandardConnector() {
		final Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
		connector.setPort(port());
		return connector;
	}

	@Bean
	public ServletRegistrationBean delegateServiceExporterServlet() {
		return new ServletRegistrationBean(new ConnectionParameterServlet(), "/connectionInfo");
	}
}
