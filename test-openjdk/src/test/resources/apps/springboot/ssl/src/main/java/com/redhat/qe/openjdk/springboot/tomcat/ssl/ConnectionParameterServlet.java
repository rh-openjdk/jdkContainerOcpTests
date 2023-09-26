package com.redhat.qe.openjdk.springboot.tomcat.ssl;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * @author Radek Koubsky (radekkoubsky@gmail.com)
 */
public class ConnectionParameterServlet extends HttpServlet {
   private static final long serialVersionUID = 1L;

   public ConnectionParameterServlet() {
      super();
   }

   protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
         throws ServletException, IOException {
      final String paramName = request.getParameter("param");
      String returnValue = "";
      if ("secure".equals(paramName)) {
         returnValue = Boolean.toString(request.isSecure());
      }
      response.getOutputStream().println(returnValue);
   }
}
