package com.redhat.qe.openjdk.swarm.sti;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class HelloWorldService {

	public String getHelloMessage(String name) {
		return "Initial " + name;
	}

}
