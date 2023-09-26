package com.redhat.qe.openjdk.swarm.noha.web;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/Enable")
public class EnableServlet extends HttpServlet {
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		req.getServletContext().setAttribute("counterDisabled", new Boolean(false));
		resp.getWriter().println("counter enabled");
	}
}
