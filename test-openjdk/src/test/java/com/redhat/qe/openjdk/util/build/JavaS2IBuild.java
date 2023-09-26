package com.redhat.qe.openjdk.util.build;

import com.google.common.collect.ImmutableMap;

import com.redhat.qe.openjdk.OpenJDKTestConfig;

import cz.xtf.core.bm.BinaryBuild;
import cz.xtf.junit5.interfaces.BuildDefinition;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * These builds use Spring Boot, but Spring Boot isn't actually subject of the respective tests.
 * It is only used to create a meaningful deployment. No other variants (Vert.x, WildFly Swarm) are necessary.
 *
 * @author Radek Koubsky (rkoubsky@redhat.com)
 */
@AllArgsConstructor
public enum JavaS2IBuild implements BuildDefinition {
	DOCKER_IMAGE_TEST_APP(new SpringBootBuildDefinition("docker-image-test-app")),
	MAVEN_ARGS(
			new SpringBootBuildDefinition("docker-image-test-app", "maven-args",
					ImmutableMap.of(
							"MAVEN_ARGS", "clean validate package -Dcom.redhat.qe.openjdk.repo.redhatga"))),
	MAVEN_ARGS_APPEND(
			new SpringBootBuildDefinition("docker-image-test-app", "maven-args-append",
					ImmutableMap.of(
							"MAVEN_ARGS_APPEND", "-Dmy.property=hello"))),
	MAVEN_CLEAR_REPO(
			new SpringBootBuildDefinition("docker-image-test-app", "maven-clear-repo",
					ImmutableMap.of(
							"MAVEN_CLEAR_REPO", "true"))),
	JAVA_OPTIONS(
			new SpringBootBuildDefinition("docker-image-test-app", "java-options",
					ImmutableMap.of(
							"JAVA_OPTS", "-Dhttp.nonProxyHosts=example.com"))),
	JAVA_ARGS(
			new SpringBootBuildDefinition("docker-image-test-app", "java-args",
					ImmutableMap.of(
							"JAVA_ARGS", "hello-world"))),
	JAVA_APP_NAME(
			new SpringBootBuildDefinition("docker-image-test-app", "java-app-name",
					ImmutableMap.of(
							"JAVA_APP_NAME", "my-app"))),

	JAVA_APP_DIR_NAME(getSpringBootBuildDefinition()),
	JAVA_REGULAR_JAR_WITH_CLASSPATH_FILE(
			new SpringBootBuildDefinition("docker-image-test-app", "java-regular-jar-with-classpath-file",
					ImmutableMap.<String, String>builder()
							.put("JAVA_MAIN_CLASS", "com.redhat.qe.openjdk.springboot.tomcat.SampleTomcatApplication")
							.put("MAVEN_S2I_GOALS", "clean package -P regular-jar")
							.put("S2I_SOURCE_DEPLOYMENTS_FILTER", "-r lib *.jar")
							.put("JAVA_LIB_DIR", "lib")
							.put("JAVA_APP_JAR", "docker-image-test-app.jar")
							.put("JAVA_APP_DIR", "/deployments")
							.put("MAVEN_CLEAR_REPO", "true")
							.build())),
	NON_EXISTING_ARTIFACT_DIR(
			new SpringBootBuildDefinition("docker-image-test-app", "non-existing-artifact-dir",
					ImmutableMap.of(
							"ARTIFACT_DIR", "non_existing_dir"))),
	ABSOLUTE_PATH_ARTIFACT_DIR(
			new SpringBootBuildDefinition("docker-image-test-app", "absolute-path-artifact-dir",
					ImmutableMap.of("ARTIFACT_DIR", "/target"))),
	ABSOLUTE_PATH_ARTIFACT_DIR_NEW(
			new SpringBootBuildDefinition("docker-image-test-app", "absolute-path-artifact-dir",
					ImmutableMap.of("MAVEN_S2I_ARTIFACT_DIRS", "/target"))),
	HTTP_PROXY_CONF_LOWERCASE_MULTIPLE_HOSTS(
			new SpringBootBuildDefinition("docker-image-test-app", "http-proxy-lowercase-multiple-hosts",
					ImmutableMap.of(
							"no_proxy", ".foo.com,.bar.com")));

	@Getter
	private final SpringBootBuildDefinition buildDefinition;

	@Override
	public BinaryBuild getManagedBuild() {
		return buildDefinition.getManagedBuild();
	}

	private static SpringBootBuildDefinition getSpringBootBuildDefinition() {
		String homeFolderName;

		if (OpenJDKTestConfig.isRHEL9()) {
			//Rhel 9s home folder is /home/default
			homeFolderName = "/home/default";
		}else {
			homeFolderName = "/home/jboss";
		}

		return new SpringBootBuildDefinition("docker-image-test-app", "java-app-dir-name",
						ImmutableMap.<String, String>builder()
								.put("JAVA_APP_NAME", "my-app")
								.put("JAVA_APP_DIR", homeFolderName)
								.put("S2I_TARGET_DEPLOYMENTS_DIR", homeFolderName)
								.put("MAVEN_CLEAR_REPO", "true")
								.build()
					);
	}



}
