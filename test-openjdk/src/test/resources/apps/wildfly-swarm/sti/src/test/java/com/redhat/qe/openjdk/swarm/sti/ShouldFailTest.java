package com.redhat.qe.openjdk.swarm.sti;

import org.junit.Assert;
import org.junit.Test;

public class ShouldFailTest {

	@Test
	public void shouldFailTest() {
		if ("true".equals(System.getProperty("qe.openjdk.test.fail", "false"))) {
			Assert.fail("Failed upon request");
		}
	}
}
