package com.redhat.qe.openjdk.opts;

import com.redhat.qe.openjdk.OpenJDKTestConfig;
import org.junit.jupiter.api.Test;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;

import com.google.common.collect.ImmutableMap;
import com.redhat.qe.openjdk.common.AbstractEnvVarsTest;
import com.redhat.qe.openjdk.util.annotation.SmokeTest;
import com.redhat.qe.openjdk.util.build.JavaS2IBuild;
import com.redhat.qe.openjdk.util.annotation.JavaS2IProfile;
import com.redhat.qe.openjdk.util.annotation.MultiArchProfile;
import com.redhat.qe.openjdk.util.build.UsesJavaS2IBuild;

import java.util.Collections;

import cz.xtf.builder.builders.limits.CPUResource;
import cz.xtf.builder.builders.limits.ComputingResource;
import cz.xtf.builder.builders.limits.MemoryResource;
import cz.xtf.core.http.Https;
import cz.xtf.junit5.annotations.SinceVersion;

@JavaS2IProfile
@MultiArchProfile
@SmokeTest
@UsesJavaS2IBuild(JavaS2IBuild.DOCKER_IMAGE_TEST_APP)
public class JavaMemoryOptionsTest extends AbstractEnvVarsTest {

	private static final Condition<String> isXmx = new Condition<>(x -> x.contains("-Xmx") ,"-Xmx");
	private static final Condition<String> isXms = new Condition<>(x -> x.contains("-Xms") ,"-Xms");

	@Test
	public void javaMaxMemoryRatioTest() {
		deploy(JavaS2IBuild.DOCKER_IMAGE_TEST_APP.getManagedBuild(),
				ImmutableMap.of("JAVA_MAX_MEM_RATIO", "25.0"), createCpuResource("100m", "2"),
				createMemoryResource("1024Mi", "1024Mi"));
		if (OpenJDKTestConfig.isRHEL7()) {
			Assertions.assertThat(getJavaOpts()).containsOnlyOnce("-Xmx256m");
		} else {
			Assertions.assertThat(getJavaOpts()).containsOnlyOnce("-XX:MaxRAMPercentage=25.0")
  												.doNotHave(isXmx);
		}
	}

	@Test
	public void javaNativeContainerLimitsTest() {
		deploy(JavaS2IBuild.DOCKER_IMAGE_TEST_APP.getManagedBuild(),
				ImmutableMap.<String, String>builder()
						// This makes the script to not set explicit mx/ms
						.put("JAVA_MAX_MEM_RATIO", "0")
						.put("JAVA_INITIAL_MEM_RATIO", "0").build(),
				createCpuResource("100m", "2"),
				createMemoryResource("1024Mi", "1024Mi"));

		Assertions.assertThat(getJavaOpts())
				.doNotHave(isXmx)
				.doNotHave(isXms);

		int availableProcessors = Integer.parseInt(Https.getContent("http://" + openshift.generateHostname(APP_NAME) + "/availableProcessors"));
		long maxMemory = Long.parseLong(Https.getContent("http://" + openshift.generateHostname(APP_NAME) + "/maxMemory"));

		Assertions.assertThat(availableProcessors).as("availableProcessors").isEqualTo(2);
		Assertions.assertThat(maxMemory).as("maxMemory").isLessThanOrEqualTo(1024 * 1024 * 1024 / 4); // we expect to be less or equal the quarter of the pod memory
	}


	@Test
	public void containerMaxMemoryTest() {
		deploy(JavaS2IBuild.DOCKER_IMAGE_TEST_APP.getManagedBuild(),
				ImmutableMap.of("CONTAINER_MAX_MEMORY", "536870912"), createCpuResource("100m", "2"),
				null);
		if ( OpenJDKTestConfig.isRHEL9()) {
			Assertions.assertThat(getJavaOpts()).containsOnlyOnce("-XX:MaxRAMPercentage=80.0");
		} else if (OpenJDKTestConfig.isRHEL8()) {
			// Only set the MaxRAMPercentage to 50. This will be raised to 80 later this year.
			Assertions.assertThat(getJavaOpts()).containsOnlyOnce("-XX:MaxRAMPercentage=80.0");
			Assertions.assertThat(getJavaOpts()).doesNotContain("-Xmx256m");
		} else {  //Rhel 7
			Assertions.assertThat(getJavaOpts()).containsOnlyOnce("-Xmx256m");
		}
	}

	@Test
	public void gcOptionsTest() {
		deploy(JavaS2IBuild.DOCKER_IMAGE_TEST_APP.getManagedBuild(),
				ImmutableMap.<String, String>builder().put("GC_MIN_HEAP_FREE_RATIO", "30")
						.put("GC_MAX_HEAP_FREE_RATIO", "50")
						.put("GC_TIME_RATIO", "5")
						.put("GC_ADAPTIVE_SIZE_POLICY_WEIGHT", "80")
						.put("GC_MAX_METASPACE_SIZE", "95")
						.build(), null, null);
		Assertions.assertThat(getJavaOpts())
				.containsOnlyOnce("-XX:+UseParallelGC")
				.containsOnlyOnce("-XX:MinHeapFreeRatio=30")
				.containsOnlyOnce("-XX:MaxHeapFreeRatio=50")
				.containsOnlyOnce("-XX:GCTimeRatio=5")
				.containsOnlyOnce("-XX:AdaptiveSizePolicyWeight=80")
				.containsOnlyOnce("-XX:MaxMetaspaceSize=95m");
	}

	@Test
	public void gcOptionsCustomTest() {
		deploy(JavaS2IBuild.DOCKER_IMAGE_TEST_APP.getManagedBuild(),
				ImmutableMap.<String, String>builder()
						.put("GC_CONTAINER_OPTIONS", "-XX:+UseG1GC -XX:MinHeapFreeRatio=30")
						.build(), null, null);
		Assertions.assertThat(getJavaOpts())
				.containsOnlyOnce("-XX:+UseG1GC")
				.containsOnlyOnce("-XX:MinHeapFreeRatio=30")
				.doesNotContain("-XX:+UseG1GC -XX:MinHeapFreeRatio=30");
	}

	@Test
	public void javaOptsAppendTest() {
		deploy(JavaS2IBuild.DOCKER_IMAGE_TEST_APP.getManagedBuild(),
				ImmutableMap.<String, String>builder()
						.put("JAVA_OPTS_APPEND", "-XX:GCTimeRatio=5 -XX:MinHeapFreeRatio=20")
						.build(), null, null);
		Assertions.assertThat(getJavaOpts())
				.containsOnlyOnce("-XX:GCTimeRatio=5")
				.containsOnlyOnce("-XX:MinHeapFreeRatio=20")
				.doesNotContain("-XX:GCTimeRatio=5 -XX:MinHeapFreeRatio=20");
	}

	private ComputingResource createMemoryResource(final String requests, final String limits) {
		final ComputingResource resource = new MemoryResource();
		resource.setRequests(requests);
		resource.setLimits(limits);
		return resource;
	}

	private ComputingResource createCpuResource(final String requests, final String limits) {
		final ComputingResource resource = new CPUResource();
		resource.setRequests(requests);
		resource.setLimits(limits);
		return resource;
	}

	@Test
	public void testDefaultMaxHeapSettings() {
		deploy(JavaS2IBuild.DOCKER_IMAGE_TEST_APP.getManagedBuild(),
				Collections.emptyMap(),
				null,
				null);
		Assertions.assertThat(getJavaOpts())
				.doNotHave(isXms)
				.doNotHave(isXmx);
	}
}
