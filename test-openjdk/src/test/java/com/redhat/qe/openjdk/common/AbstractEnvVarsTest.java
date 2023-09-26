package com.redhat.qe.openjdk.common;

import org.junit.jupiter.api.BeforeEach;
import com.redhat.qe.openjdk.jolokia.JolokiaConfiguration;
import com.redhat.qe.openjdk.util.deployment.MsaDeploymentBuilder;
import org.junit.platform.commons.util.StringUtils;

import com.redhat.qe.openjdk.OpenJDKTestParent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import cz.xtf.builder.builders.limits.ComputingResource;
import cz.xtf.builder.builders.pod.ContainerBuilder;
import cz.xtf.core.bm.ManagedBuild;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.junit5.annotations.CleanBeforeEach;

@CleanBeforeEach
public abstract class AbstractEnvVarsTest extends OpenJDKTestParent {
	public static final String APP_NAME = "test-app";
	protected static final OpenShift openshift = OpenShifts.master();


	protected void deploy(final ManagedBuild build) {
		this.deploy(build, Collections.emptyMap());
	}

	protected void deploy(final ManagedBuild build, final Map<String, String> envVars) {
		deploy(build, envVars, null, null);
	}

	protected void deploy(final ManagedBuild build, final Map<String, String> envVars,
						  final ComputingResource cpuResource, final ComputingResource memoryResource) {
		deploy(build, envVars, cpuResource, memoryResource, new JolokiaConfiguration.Builder().build());
	}

	protected void deploy(final ManagedBuild build, final Map<String, String> envVars,
						  final ComputingResource cpuResource, final ComputingResource memoryResource,
						  final JolokiaConfiguration jolokaConf) {
		deploy(build, envVars, cpuResource, memoryResource, jolokaConf, null);
	}

	protected void deploy(final ManagedBuild build, final Map<String, String> envVars,
						  final ComputingResource cpuResource, final ComputingResource memoryResource,
						  final JolokiaConfiguration jolokaConf, final Consumer<ContainerBuilder> containerModification) {
		MsaDeploymentBuilder.withNewJavaS2IApp(APP_NAME)
				.withBuild(build)
				.generateMirrorSettings(false)
				.withDeploymentEnvironmentVariables(envVars)
				.withCPUResource(cpuResource)
				.withMemoryResource(memoryResource)
				.urlCheck("")
				.withContainerModification(containerModification)
				.configureJolokia(jolokaConf)
				.deploy();
	}

	protected List<String> getJavaOpts() {
		final String cmdline = openshift.podShell(APP_NAME).executeWithBash(String.format("cat /proc/%s/cmdline", getPid(APP_NAME))).getOutput();
		char delimiter = '\u0000'; // = "\\0"
		return Arrays.asList(cmdline.split(String.valueOf(delimiter)));
	}
	protected List<String> getPodEnv() {
		final String cmdline = openshift.podShell(APP_NAME).executeWithBash(String.format("cat /proc/%s/environ", getPid(APP_NAME))).getOutput();
		char delimiter = '\u0000'; // = "\\0"
		return Arrays.asList(cmdline.split(String.valueOf(delimiter)));
	}

	protected String getPid(String appName) {
		String output = openshift.podShell(appName).executeWithBash("ps -C java -o pid | grep [0-9]").getOutput();
		if (StringUtils.isBlank(output)) {
			output = openshift.podShell(appName).executeWithBash("grep java -l /proc/*/exe | cut -d \"/\" -f 3").getOutput();
		}
		return output;
	}
}
