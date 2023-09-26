package com.redhat.qe.openjdk.swarm.ssl;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/connectionInfo")
public class ConnectionParameterServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		final String paramName = req.getParameter("param");
		String returnValue = "";
		if ("secure".equals(paramName)) {
			returnValue = Boolean.toString(req.isSecure());
		}
		resp.getOutputStream().println(returnValue);
	}
}
