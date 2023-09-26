package com.redhat.qe.openjdk.tomcat.noha.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Simple Servlet-based session-scoped counter
 *
 * @author lfuka
 */
@Controller
public class ServletCounter {

 	@GetMapping(path="/Counter", produces="text/plain")
	@ResponseBody
	public String counter(HttpServletRequest req) {
		String servletId = (String) req.getServletContext().getAttribute("servletId");
		Boolean disabled = (Boolean) req.getServletContext().getAttribute("counterDisabled");

		if (disabled != null && disabled.booleanValue()) {
			throw new IllegalArgumentException("Counter servlet temporarily disabled, sorry!");
		}

		HttpSession session = req.getSession();
		Long counter = (Long) session.getAttribute("counter");
		if (counter == null) {
			counter = 0L;
		}
		counter++;
		session.setAttribute("counter", counter);
		
		return servletId + " " + counter;
	}
	
	@ExceptionHandler
	private void handleIllegalArgumentException(IllegalArgumentException e, HttpServletResponse response) throws IOException {
		response.sendError(503, "Counter servlet temporarily disabled, sorry!");
	}	

}
