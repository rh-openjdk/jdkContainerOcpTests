package com.redhat.qe.openjdk.swarm.noha;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import java.util.UUID;
import java.util.logging.Logger;

@WebListener
public class NohaServletContextListener implements ServletContextListener {
	private static Logger logger = Logger.getLogger(NohaServletContextListener.class.getName());

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		sce.getServletContext().setAttribute("servletId", UUID.randomUUID().toString());
		logger.info("ServletContext initialized, servletId: " + sce.getServletContext().getAttribute("servletId"));
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		logger.info("ServletContext destroyed");
	}
}
