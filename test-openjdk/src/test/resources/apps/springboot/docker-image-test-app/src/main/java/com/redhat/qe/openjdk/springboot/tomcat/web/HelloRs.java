package com.redhat.qe.openjdk.springboot.tomcat.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HelloRs {
	@GetMapping("/")
	@ResponseBody
	public String helloWorld() {
		return "Hello world!";
	}
}
