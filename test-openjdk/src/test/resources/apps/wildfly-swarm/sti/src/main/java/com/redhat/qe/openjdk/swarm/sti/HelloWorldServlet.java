package com.redhat.qe.openjdk.swarm.sti;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/HelloWorld")
public class HelloWorldServlet extends HttpServlet {
	@Inject
	private HelloWorldService helloWorldService;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String name = req.getParameter("name");
		resp.getWriter().println(helloWorldService.getHelloMessage(name));
	}
}
