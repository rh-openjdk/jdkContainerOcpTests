package com.redhat.qe.openjdk.tomcat.noha.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

/**
 * Simple Servlet-based session-scoped counter
 *
 * @author lfuka
 */
@Controller 
public class EnableServlet {

 	@GetMapping(path="/Enable", produces="text/plain")
	@ResponseBody
	public String enableCounter(HttpServletRequest req) {
		req.getServletContext().setAttribute("counterDisabled", new Boolean(false));
		return "counter enabled";
	}
}
