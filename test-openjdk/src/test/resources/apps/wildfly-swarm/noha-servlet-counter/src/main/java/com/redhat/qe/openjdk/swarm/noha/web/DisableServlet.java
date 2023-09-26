package com.redhat.qe.openjdk.swarm.noha.web;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/Disable")
public class DisableServlet extends HttpServlet {
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		req.getServletContext().setAttribute("counterDisabled", new Boolean(true));
		resp.getWriter().println("counter disabled");
	}
}
