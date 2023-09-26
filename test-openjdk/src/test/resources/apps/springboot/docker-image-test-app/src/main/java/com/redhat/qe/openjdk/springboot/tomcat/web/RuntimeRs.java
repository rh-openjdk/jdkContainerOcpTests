package com.redhat.qe.openjdk.springboot.tomcat.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class RuntimeRs {
	@GetMapping("/availableProcessors")
	@ResponseBody
	public int availableProcessors() {
		return Runtime.getRuntime().availableProcessors();
	}

	@GetMapping("/maxMemory")
	@ResponseBody
	public long maxMemory() {
		return Runtime.getRuntime().maxMemory();
	}
}
