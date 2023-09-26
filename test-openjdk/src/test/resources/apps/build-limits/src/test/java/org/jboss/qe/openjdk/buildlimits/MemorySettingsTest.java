package org.jboss.qe.openjdk.buildlimits;

import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

public class MemorySettingsTest {

	@Test
	public void printMemoryUsage() {
		System.out.printf("HeapMemoryUsage: %s\n", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage());
		System.out.printf("NonHeapMemoryUsage: %s\n", ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage());

		for (MemoryPoolMXBean mpBean: ManagementFactory.getMemoryPoolMXBeans()) {
			System.out.printf("%s %s: %s\n", mpBean.getType(), mpBean.getName(), mpBean.getUsage());
		}
	}

	@Test
	public void test() {
		String op = System.getenv("COMPARE");
		final String arg = System.getenv("ARG");
		final String arg1 = System.getenv("ARG1");
		final String arg2 = System.getenv("ARG2");
		final long totalMemory = Runtime.getRuntime().totalMemory();
		final long initialHeapMemory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getInit();
		final long maxHeapMemory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();

		System.out.println(String.format("Operation %s, value %s, totalMemory %s initialHeapMemory %s maxHeapMemory %s", op, arg, totalMemory, initialHeapMemory, maxHeapMemory));
		System.out.println(String.format("MAVEN_OPTS=%s", System.getenv("MAVEN_OPTS")));

		if (op != null) {
			switch (op) {
				case "eq":
					assert totalMemory == Long.parseLong(arg);
					break;
				case "gt":
					assert totalMemory >= Long.parseLong(arg);
					break;
				case "lt":
					assert totalMemory <= Long.parseLong(arg);
					break;
				case "rng":
					assert totalMemory >= Long.parseLong(arg1) && totalMemory <= Long.parseLong(arg2);
					break;
				case "heaprng":
					assert initialHeapMemory >= Long.parseLong(arg1) && maxHeapMemory <= Long.parseLong(arg2);
					break;
			}
		}
	}

}
