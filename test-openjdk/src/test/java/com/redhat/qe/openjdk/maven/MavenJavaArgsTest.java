package com.redhat.qe.openjdk.maven;

import com.redhat.qe.openjdk.util.annotation.JavaS2IProfile;
import com.redhat.qe.openjdk.util.annotation.MultiArchProfile;
import com.redhat.qe.openjdk.util.annotation.SmokeTest;
import com.redhat.qe.openjdk.util.build.JavaS2IBuild;
import com.redhat.qe.openjdk.util.build.UsesJavaS2IBuilds;
import com.redhat.qe.openjdk.OpenJDKTestConfig;
import com.redhat.qe.openjdk.common.AbstractEnvVarsTest;
import cz.xtf.builder.builders.ApplicationBuilder;
import cz.xtf.junit5.annotations.KnownIssue;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.condition.DisabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@JavaS2IProfile
@MultiArchProfile
@Slf4j
@SmokeTest
@UsesJavaS2IBuilds({JavaS2IBuild.JAVA_APP_DIR_NAME, JavaS2IBuild.MAVEN_CLEAR_REPO, JavaS2IBuild.JAVA_OPTIONS, JavaS2IBuild.JAVA_ARGS, JavaS2IBuild.JAVA_APP_NAME, JavaS2IBuild.JAVA_REGULAR_JAR_WITH_CLASSPATH_FILE})
public class MavenJavaArgsTest extends AbstractEnvVarsTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(MavenJavaArgsTest.class);

	@Test
	public void testMavenArgs() throws IOException {
		// Don't use BuildManager to pre-build this app. We need to make sure logs are accessible for assertion.
		startLocalBuild(JavaS2IBuild.MAVEN_ARGS.getBuildDefinition().getBuildName(), JavaS2IBuild.MAVEN_ARGS.getBuildDefinition()
				.getEnvProperties());
		openshift.waiters().hasBuildCompleted(JavaS2IBuild.MAVEN_ARGS.getBuildDefinition().getBuildName()).waitFor();

		verifyLocalBuildLog(JavaS2IBuild.MAVEN_ARGS, JavaS2IBuild.MAVEN_ARGS.getBuildDefinition()
				.getEnvProperties()
				.get("MAVEN_ARGS"));
	}

	@Test
	public void testMavenArgsAppend() throws IOException {
		startLocalBuild(JavaS2IBuild.MAVEN_ARGS_APPEND.getBuildDefinition().getBuildName(), JavaS2IBuild.MAVEN_ARGS_APPEND.getBuildDefinition()
				.getEnvProperties());
		openshift.waiters().hasBuildCompleted(JavaS2IBuild.MAVEN_ARGS_APPEND.getBuildDefinition().getBuildName()).waitFor();

		verifyLocalBuildLog(JavaS2IBuild.MAVEN_ARGS_APPEND, JavaS2IBuild.MAVEN_ARGS_APPEND.getBuildDefinition()
				.getEnvProperties()
				.get("MAVEN_ARGS_APPEND"));
	}

	@Test
	public void mavenClearRepoTest() {
		deploy(JavaS2IBuild.MAVEN_CLEAR_REPO.getManagedBuild());
		Assertions.assertThat(openshift.podShell(APP_NAME).execute("sh", "-c", "ls /tmp/artifacts/m2").getOutput())
				.isEmpty();
	}
	
	@Test
	public void absolutePathArtifactDirTest() throws IOException {
		// Because of some environment variable changes
		if ( OpenJDKTestConfig.isRHEL9()) {
			boolean buildResult = startLocalBuild(JavaS2IBuild.ABSOLUTE_PATH_ARTIFACT_DIR_NEW.getBuildDefinition().getBuildName(),
					JavaS2IBuild.ABSOLUTE_PATH_ARTIFACT_DIR_NEW.getBuildDefinition().getEnvProperties());
			String buildName = JavaS2IBuild.ABSOLUTE_PATH_ARTIFACT_DIR_NEW.getBuildDefinition().getBuildName();
			LOGGER.info("*********" + buildName + "****************");
			Assertions.assertThat(buildResult).as("Build should fail on absolute path error").isFalse();
			verifyLocalBuildLog(JavaS2IBuild.ABSOLUTE_PATH_ARTIFACT_DIR_NEW,"ERROR Absolute path found in MAVEN_S2I_ARTIFACT_DIRS: /target");
		}else {  // UBI 8 or RHEL 7
			boolean buildResult = startLocalBuild(JavaS2IBuild.ABSOLUTE_PATH_ARTIFACT_DIR.getBuildDefinition().getBuildName(),
					JavaS2IBuild.ABSOLUTE_PATH_ARTIFACT_DIR.getBuildDefinition().getEnvProperties());
			String buildName = JavaS2IBuild.ABSOLUTE_PATH_ARTIFACT_DIR.getBuildDefinition().getBuildName();
			LOGGER.info("*********" + buildName + "****************");
			Assertions.assertThat(buildResult).as("Build should fail on absolute path error").isFalse();
			verifyLocalBuildLog(JavaS2IBuild.ABSOLUTE_PATH_ARTIFACT_DIR,"ERROR Absolute path found in MAVEN_S2I_ARTIFACT_DIRS: /target");
		}
	}

	@Test
	public void javaOptionsTest() {
		deploy(JavaS2IBuild.JAVA_OPTIONS.getManagedBuild());
		Assertions.assertThat(getJavaOpts())
				.containsOnlyOnce(JavaS2IBuild.JAVA_OPTIONS.getBuildDefinition()
						.getEnvProperties()
						.get("JAVA_OPTS"));
	}

	@Test
	public void javaArgsTest() {
		deploy(JavaS2IBuild.JAVA_ARGS.getManagedBuild());
		Assertions.assertThat(getJavaOpts())
				.containsOnlyOnce(JavaS2IBuild.JAVA_ARGS.getBuildDefinition()
						.getEnvProperties()
						.get("JAVA_ARGS"));
	}

	@Test
	public void javaAppNameTest() {
		deploy(JavaS2IBuild.JAVA_APP_NAME.getManagedBuild());
		final String appName = JavaS2IBuild.JAVA_APP_NAME.getBuildDefinition()
				.getEnvProperties()
				.get("JAVA_APP_NAME");
		if (OpenJDKTestConfig.isRHEL7()){  //rhel 7 containers exhibit a strange parsing where the single quotes remain in the string.
			try {
				Assertions.assertThat(getJavaOpts())
						.containsOnlyOnce("'" + appName + "'" ); // "my-app"
			} catch (AssertionError ae) {
				if (ae.getMessage().contains("but some elements were not found:\n" +
						" <[\"" + appName + "\"]>")
						&& getJavaOpts().contains("'" + appName + "'")) { // "'my-app'"
					throw new AssertionError("Old issue: " + ae.getMessage());
				}
				throw ae;
			}
		} else {
			try {
				Assertions.assertThat(getJavaOpts())
						.containsOnlyOnce(appName ); // "my-app"
			} catch (AssertionError ae) {
				if (ae.getMessage().contains("but some elements were not found:\n" +
						" <[\"" + appName + "\"]>")
						&& getJavaOpts().contains(appName)) { // "'my-app'"
					throw new AssertionError("CLOUD-2810 " + ae.getMessage());
				}
				throw ae;
			}
		}

	}

	@Test
	@DisabledIf("customConditionalFunction")
	public void javaAppDirNameTest() {
		deploy(JavaS2IBuild.JAVA_APP_DIR_NAME.getManagedBuild());
		final String appDirName = JavaS2IBuild.JAVA_APP_DIR_NAME.getBuildDefinition()
				.getEnvProperties()
				.get("JAVA_APP_DIR");
		try {
			Assertions.assertThat(getPodEnv())
					.containsOnlyOnce("JAVA_APP_DIR=" + appDirName );
		} catch (AssertionError ae) {
			if (ae.getMessage().contains("but some elements were not found:\n" +
					" <[\"" + appDirName + "\"]>")
					&& getJavaOpts().contains(appDirName)) { // "'my-app'"
				throw new AssertionError("JAVA_APP_DIR " + ae.getMessage());
			}
			throw ae;
		}
	}

	@Test
	public void javaRegularJarTest() {
		deploy(JavaS2IBuild.JAVA_REGULAR_JAR_WITH_CLASSPATH_FILE.getManagedBuild());
		String javaLibDir = String.format(":%s/",
				JavaS2IBuild.JAVA_REGULAR_JAR_WITH_CLASSPATH_FILE.getBuildDefinition()
						.getEnvProperties()
						.get("JAVA_LIB_DIR")); // ":lib/"
		LOGGER.info("*********" + javaLibDir + "****************");

		Assertions.assertThat(getJavaOpts()).haveAtLeastOne(
				new Condition<>(x -> x
						.matches(".*(" + javaLibDir + ").*"), "Has to contain jars in lib dir."));
	}

	@Test
	public void javaClassPathTest() {
		/*-cp jvm option is ignored in container as -jar opt has precedence, thus we only test presence
		//of the JAVA_CLASS_PATH value*/
		deploy(JavaS2IBuild.DOCKER_IMAGE_TEST_APP.getManagedBuild(), Collections.singletonMap("JAVA_CLASSPATH", "test.jar"));
		Assertions.assertThat(getJavaOpts())
				.containsOnlyOnce("test.jar");
	}

	@Test
	public void javaDiagnosticsTest() {
		deploy(JavaS2IBuild.DOCKER_IMAGE_TEST_APP.getManagedBuild(), Collections.singletonMap("JAVA_DIAGNOSTICS", "true"));
		if ( OpenJDKTestConfig.isOpenJDK21() ||OpenJDKTestConfig.isOpenJDK17()) {
			Assertions.assertThat(getJavaOpts())
					.containsOnlyOnce("-XX:NativeMemoryTracking=summary")
					.containsOnlyOnce("-Xlog:gc::utctime");
		} else if (OpenJDKTestConfig.isOpenJDK11()) { // CLOUD-3040
			Assertions.assertThat(getJavaOpts())
					.containsOnlyOnce("-XX:NativeMemoryTracking=summary")
					.containsOnlyOnce("-Xlog:gc::utctime");
		} else {
			Assertions.assertThat(getJavaOpts())
					.containsOnlyOnce("-XX:NativeMemoryTracking=summary")
					.containsOnlyOnce("-XX:+PrintGCDetails")
					.containsOnlyOnce("-XX:+PrintGCDateStamps")
					.containsOnlyOnce("-XX:+PrintGCTimeStamps")
					.containsOnlyOnce("-XX:+UnlockDiagnosticVMOptions");
		}
	}

	/**
	 *
	 * Spawn a build in a current testing namespace rather than relying on BuildManager.
	 * This method should ensure build pod logs are accessible for further test assertions.
	 *
	 */
	private boolean startLocalBuild(String buildName, Map<String, String> envVars) throws IOException {
		ApplicationBuilder appBuilder = appFromBinaryBuild(buildName);
		envVars.forEach( (k,v) -> appBuilder.buildConfig().sti().addEnvVariable(k, v));
		appBuilder.buildApplication(openshift).deploy();

		Path appPath = prepareProjectSources(buildName, findApplicationDirectory("docker-image-test-app", "springboot"));
		startBinaryBuild(appBuilder.buildConfig().getName(), appPath);

		return openshift.waiters().hasBuildCompleted(buildName).waitFor();
	}

	private void verifyLocalBuildLog(final JavaS2IBuild build, final String contain) {
		Assertions.assertThat(openshift.builds().withName(
				String.format("%s-%s", build.getManagedBuild().getId(),
						openshift.buildConfigs().withName(build.getManagedBuild().getId()).get().getStatus().getLastVersion())).getLog())
				.contains(contain);
	}
	private boolean customConditionalFunction() {
		LOGGER.info("Check if running Rhel 7 images.");
		return OpenJDKTestConfig.isRHEL7();
	}
}

