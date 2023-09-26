package com.redhat.qe.openjdk.swarm.noha.web;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;

@WebServlet("/Counter")
public class CounterServlet extends HttpServlet {
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String servletId = (String) req.getServletContext().getAttribute("servletId");
		Boolean disabled = (Boolean) req.getServletContext().getAttribute("counterDisabled");

		if (disabled != null && disabled.booleanValue()) {
			resp.sendError(503, "Counter servlet temporarily disabled, sorry!");
			return;
		}

		HttpSession session = req.getSession();
		Long counter = (Long) session.getAttribute("counter");
		if (counter == null) {
			counter = 0L;
		}
		counter++;
		session.setAttribute("counter", counter);

		resp.getWriter().println(servletId + " " + counter);
	}
}
