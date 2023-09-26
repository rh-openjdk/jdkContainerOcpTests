package com.redhat.qe.openjdk.s2i;

import com.redhat.qe.openjdk.OpenJDKTestParent;
import org.apache.commons.io.FileUtils;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.assertj.core.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import cz.xtf.builder.builders.ApplicationBuilder;
import cz.xtf.client.Http;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.core.waiting.WaiterException;
import io.fabric8.openshift.api.model.Build;

public abstract class AbstractBuildTest extends OpenJDKTestParent {
	protected static String appName;
	protected static String appModule;
	private static final OpenShift openShift = OpenShifts.master();
	private Path preparedSources;
	private ApplicationBuilder builder;

	private void setupBuildEnv(Path preparedSources, String mavenArgsAppend, boolean clearRepo) throws IOException {
		StringBuilder sb = new StringBuilder();
		if (mavenArgsAppend != null) {
			sb.append("MAVEN_ARGS_APPEND=").append(mavenArgsAppend).append("\n");
		}
		if (clearRepo) {
			sb.append("MAVEN_CLEAR_REPO=true\n");
		}
		Files.createDirectories(preparedSources.resolve(".s2i"));
		FileUtils.writeStringToFile(preparedSources.resolve(".s2i").resolve("environment").toFile(), sb.toString(), "UTF-8");
	}

	@BeforeEach
	public void prepareBuild() throws IOException {
		openShift.clean().waitFor();
		Path originalSources = findApplicationDirectory(appName, appModule);
		preparedSources = prepareProjectSources(appName, originalSources);
		builder = OpenJDKTestParent.appFromBinaryBuild(appName);
	}

	@Test
	public void binaryBuildFromDir() throws IOException {
		builder.buildApplication(openShift).deploy();

		Build build = OpenJDKTestParent.startBinaryBuild(appName, preparedSources);
		openShift.waiters().hasBuildCompleted(build).waitFor();
		Build buildResult = openShift.getBuild(build.getMetadata().getName());
		Assertions.assertThat(buildResult.getStatus().getPhase()).isEqualTo("Complete");
	}

	@Test
	public void incrementalRebuild() throws IOException {
		builder.buildConfig().sti().incremental();
		builder.buildApplication(openShift).deploy();
		// first build should succeed
		Build build = OpenJDKTestParent.startBinaryBuild(appName, preparedSources);
		openShift.waiters().hasBuildCompleted(build).waitFor();
		Build buildResult = openShift.getBuild(build.getMetadata().getName());
		Assertions.assertThat(buildResult.getStatus().getPhase()).isEqualTo("Complete");

		// an subsequent offline incremental build should succeed (with no downloads needed)
		setupBuildEnv(preparedSources, "-o", true);

		build = OpenJDKTestParent.startBinaryBuild(appName, preparedSources);
		openShift.waiters().hasBuildCompleted(build).waitFor();
		buildResult = openShift.getBuild(build.getMetadata().getName());
		Assertions.assertThat(buildResult.getStatus().getPhase()).isEqualTo("Complete");
		Assertions.assertThat(openShift.getBuildLog(buildResult)).as("No downloads from Internet").doesNotContainPattern(Pattern.compile("(Downloading|Downloaded)"));

		// with repo cleaned, the subsequent offline build should fail
		build = OpenJDKTestParent.startBinaryBuild(appName, preparedSources);
		openShift.waiters().hasBuildCompleted(build).waitFor();
		buildResult = openShift.getBuild(build.getMetadata().getName());
		Assertions.assertThat(buildResult.getStatus().getPhase()).isEqualTo("Failed");
		Assertions.assertThat(openShift.getBuildLog(buildResult)).as("Build log should contain").contains("[ERROR]");
		Assertions.assertThat(openShift.getBuildLog(buildResult)).as("Build log should contain").contains("Non-resolvable import");

		// a subsequent on-line build should work
		setupBuildEnv(preparedSources, null, true);

		build = OpenJDKTestParent.startBinaryBuild(appName, preparedSources);
		openShift.waiters().hasBuildCompleted(build).waitFor();
		buildResult = openShift.getBuild(build.getMetadata().getName());
		Assertions.assertThat(buildResult.getStatus().getPhase()).isEqualTo("Complete");
		Assertions.assertThat(openShift.getBuildLog(buildResult)).as("It should download from Internet").containsPattern(Pattern.compile("(Downloading|Downloaded)"));
	}

	@Test
	public void failedUnitTestBuild() throws IOException {
		builder.buildApplication(openShift).deploy();
		setupBuildEnv(preparedSources, "-P compile-error --threads 1", true);

		Build build = OpenJDKTestParent.startBinaryBuild(appName, preparedSources);
		openShift.waiters().hasBuildCompleted(build).waitFor();
		Build buildResult = openShift.getBuild(build.getMetadata().getName());
		Assertions.assertThat(buildResult.getStatus().getPhase()).isEqualTo("Failed");
		Assertions.assertThat(openShift.getBuildLog(buildResult)).as("Build log should contain")
				.contains("BUILD FAILURE", "missing return statement");
	}

	@Test
	public void binaryBuildFromFile() throws IOException {
		Assertions.assertThat(System.getenv("MAVEN_HOME")).as("${MAVEN_HOME} or -Dmaven.home is required").isNotNull();
		
		builder.deploymentConfig().podTemplate().container().addReadinessProbe().createHttpProbe("/ping", "8080").setInitialDelaySeconds(30);
		builder.service().port("http", 8080);
		builder.route();
		if (appModule.equals("wildfly-swarm")) {
			builder.deploymentConfig().podTemplate().container().envVar("AB_JOLOKIA_OFF", "true");
		}
		builder.buildApplication(openShift).deploy();

		InvocationRequest mvnRequest = new DefaultInvocationRequest();
		mvnRequest.setBatchMode(true);
		mvnRequest.setBaseDirectory(preparedSources.toFile());
		mvnRequest.setUserSettingsFile(findProjectRoot().resolve("settings.xml").toFile());
		mvnRequest.setGoals(Arrays.asList("clean", "package", "-D skipTests"));

		Invoker mvnInvoker = new DefaultInvoker();
		mvnInvoker.setMavenHome(Paths.get(System.getenv("MAVEN_HOME")).toFile());
		try {
			InvocationResult mvnResult = mvnInvoker.execute(mvnRequest);
			Assertions.assertThat(mvnResult.getExitCode()).isEqualTo(0);
		} catch (MavenInvocationException e) {
			e.printStackTrace();
			Assertions.fail("Local Maven build failed", e);
		}
		String fileName = appName + (appModule.equals("springboot") ? "" : "-thorntail") + ".jar";
		openShift.buildConfigs().withName(appName).instantiateBinary().asFile(fileName).fromFile(preparedSources.resolve("target/" + fileName).toFile());
		openShift.waiters().hasBuildCompleted(appName).waitFor();
		Build buildResult = openShift.getBuild(openShift.getLatestBuild(appName).getMetadata().getName());
		Assertions.assertThat(buildResult.getStatus().getPhase()).isEqualTo("Complete");

		try {
			openShift.waiters().isDcReady(appName).timeout(TimeUnit.MINUTES, 3).waitFor();
			Http.get("http://" + openShift.generateHostname(appName) + "/ping").waiters().ok().timeout(TimeUnit.MINUTES, 2).waitFor();
		} catch (WaiterException e) {
			Assertions.fail("CLOUD-3095: application haven't started in time", e);
		}
	}

}
