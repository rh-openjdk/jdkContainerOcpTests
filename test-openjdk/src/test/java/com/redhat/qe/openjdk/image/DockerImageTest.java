package com.redhat.qe.openjdk.image;

import com.redhat.qe.openjdk.util.annotation.JavaS2IProfile;
import com.redhat.qe.openjdk.util.annotation.MultiArchProfile;
import com.redhat.qe.openjdk.util.annotation.SmokeTest;
import cz.xtf.core.config.WaitingConfig;
import cz.xtf.core.waiting.SimpleWaiter;
import io.fabric8.kubernetes.api.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.assertj.core.api.Assertions;

import com.redhat.qe.openjdk.OpenJDKTestConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.testhelpers.image.ImageContent;
import cz.xtf.testhelpers.image.ImageMetadata;
import cz.xtf.core.openshift.helpers.ResourceParsers;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@JavaS2IProfile
@MultiArchProfile
@SmokeTest


public class DockerImageTest extends AbstractDockerImageTest {
	private static final OpenShift openShift = OpenShifts.master();

	private static final String project = openShift.getNamespace();

	private static final Map<String, String> EXPECTED_ENVIRONMENTS = getExpectedEnvironments();

	private static final Logger LOGGER = LoggerFactory.getLogger(DockerImageTest.class);

	@BeforeAll
	public static void prepareImage() {
		metadata = ImageMetadata.prepare(openShift, OpenJDKTestConfig.imageUrl());
		// Override default pod command `run-java.sh` to start without jar file present
		// Attempt to replace functionality from the xtf framework.
		String name = "test-pod";
		Container container = new ContainerBuilder().withName(name).withImage(OpenJDKTestConfig.imageUrl()).build();
		SeccompProfile secCom = new SeccompProfile();
		secCom.setType("RuntimeDefault");
		Capabilities cap = new Capabilities();
		cap.setDrop(Arrays.asList("ALL"));
		SecurityContext secContext = container.getSecurityContext();
		container.setCommand(Arrays.asList("/bin/bash", "-c", "sleep infinity"));

		SecurityContext updateSecContext = new SecurityContext();
		updateSecContext.setAllowPrivilegeEscalation(false);
		updateSecContext.setRunAsNonRoot(true);
		updateSecContext.setSeccompProfile(secCom);
		updateSecContext.setCapabilities(cap);
		// Apply the security context to the container object.
		container.setSecurityContext(updateSecContext);
		PodSpec podSpec = new PodSpec();
		podSpec.setContainers(Collections.singletonList(container));
		Pod pod = new Pod();
		pod.setMetadata(new ObjectMetaBuilder().withName(name).build());
		pod.setSpec(podSpec);
		openShift.createPod(pod);
		BooleanSupplier bs = () -> {
			Pod p = openShift.getPod(name);
			return p != null && ResourceParsers.isPodRunning(p) && ResourceParsers.isPodReady(p);
		};
		new SimpleWaiter(bs, "Waiting for '" + name + "' pod to be running and ready").timeout(WaitingConfig.timeout())
				.waitFor();
		content = ImageContent.prepare(openShift, pod);

		SecurityContext correctSecContext = new SecurityContext();

		LOGGER.info("###########  Image Under Test: " + OpenJDKTestConfig.getImageUrl() );
		LOGGER.info("###########  Product version Under Test: " + OpenJDKTestConfig.getProductVersion() );
		LOGGER.info("###########  Repo Under Test: " + OpenJDKTestConfig.getImageRepo() );
		LOGGER.info("###########  Java Version Under Test: " + content.javaVersion());
		LOGGER.info("###########  Java Version Under Test error RAW: " + content.shell().execute(new String[]{"java", "-version"}).getError().replaceAll("\n", "") );
	}

	@Test
	public void testCorrectCommand() {
		Assertions.assertThat(metadata.command()).isEqualTo("/usr/local/s2i/run");
	}

	@Test
	public void testDeclaredEnvironmentVariables() {
		Assertions.assertThat(metadata.envs()).containsAllEntriesOf(EXPECTED_ENVIRONMENTS);
	}

	@Test
	public void testEnvironmentVariablesInContainer() {
		Assertions.assertThat(content.runtimeEnvVars()).containsAllEntriesOf(EXPECTED_ENVIRONMENTS);
	}

	@Test
	public void testExposedTcpPorts() {
		// Port 8778 is from Jolokia and this has been pulled from the JDK17 containers.
		// This port has also been removed from the ubi9 images.
		if ( OpenJDKTestConfig.isOpenJDK17() || OpenJDKTestConfig.isOpenJDK21() || OpenJDKTestConfig.isRHEL9()) {
			Assertions.assertThat(metadata.exposedPorts("tcp")).containsOnly(8080, 8443);
		} else {
    		Assertions.assertThat(metadata.exposedPorts("tcp")).containsOnly(8080, 8443, 8778);
		}

	}

	@Test
	public void testExposedUdpPorts() {
		Assertions.assertThat(metadata.exposedPorts("udp")).hasSize(0);
	}

	@Test
	public void testUsageLabel() {

		if (OpenJDKTestConfig.isRHEL7()){
			Assertions.assertThat(metadata.labels().get("usage")).isEqualTo("https://access.redhat.com/documentation/en-us/red_hat_jboss_middleware_for_openshift/3/html/red_hat_java_s2i_for_openshift/");
		} else if( OpenJDKTestConfig.isOpenJDK8() ) {
			Assertions.assertThat(metadata.labels().get("usage")).isEqualTo("https://jboss-container-images.github.io/openjdk/");
		} else if (OpenJDKTestConfig.isOpenJDK11()){
			Assertions.assertThat(metadata.labels().get("usage")).isEqualTo("https://jboss-container-images.github.io/openjdk/");
		} else if (OpenJDKTestConfig.isOpenJDK17()){
			Assertions.assertThat(metadata.labels().get("usage")).isEqualTo("https://jboss-container-images.github.io/openjdk/");
		} else if (OpenJDKTestConfig.isRHEL9() && OpenJDKTestConfig.isOpenJDK21()){  //Temp fix until https://issues.redhat.com/browse/OPENJDK-2595
			Assertions.assertThat(metadata.labels().get("usage")).isEqualTo("https://access.redhat.com/documentation/en-us/red_hat_jboss_middleware_for_openshift/3/html/red_hat_java_s2i_for_openshift/");
		} else if (OpenJDKTestConfig.isOpenJDK21()){
			Assertions.assertThat(metadata.labels().get("usage")).isEqualTo("https://jboss-container-images.github.io/openjdk/");
		}

	}

	@Test
	public void testUrandomJavaSecurity() {
		String result = "none";
		if ( OpenJDKTestConfig.isOpenJDK8() || OpenJDKTestConfig.isOpenJDK8Rhel7() ) {
			result = content.shell().execute("cat", "/usr/lib/jvm/java/jre/lib/security/java.security").getOutput();
		} else if (OpenJDKTestConfig.isOpenJDK11()){
			result = content.shell().execute("cat", "/usr/lib/jvm/jre/conf/security/java.security").getOutput();
		} else if (OpenJDKTestConfig.isOpenJDK17() || OpenJDKTestConfig.isOpenJDK21()) {
			result = content.shell().execute("cat", "/usr/lib/jvm/jre/conf/security/java.security").getOutput();
		}

		Assertions.assertThat(result).containsPattern(
				Pattern.compile("^securerandom.source=file:/dev/urandom$", Pattern.MULTILINE));
	}

	@Override
	@Test
	public void javaUtilitiesTest() {
        //Content should match what is in the java/bin folder. Or the $JAVA_HOME/bin folder
		// JDK 8 for UBI 8 and Rhel 7
		if ( OpenJDKTestConfig.isOpenJDK8() || OpenJDKTestConfig.isOpenJDK8Rhel7() ) {
			LOGGER.info("DockerImageTest:javaUtilitiesTest::Running check for jdk8.");
			Assertions.assertThat(content.listDirContent("$JAVA_HOME/bin")).contains(super.DEFAULT_JAVA_8_UTILITIES);
		// JDK 11 for Rhel 7, UBI 8, UBI 9
		} else if (OpenJDKTestConfig.isOpenJDK11()){
			LOGGER.info("DockerImageTest:javaUtilitiesTest::Running check for jdk11.");
			Assertions.assertThat(content.listDirContent("$JAVA_HOME/bin")).contains(super.DEFAULT_JAVA_11_UTILITIES);
		// JDK 17 for UBI 8 and UBI 9
		} else if (OpenJDKTestConfig.isOpenJDK17()){
			LOGGER.info("DockerImageTest:javaUtilitiesTest::Running check for jdk17");
			Assertions.assertThat(content.listDirContent("$JAVA_HOME/bin")).contains(super.DEFAULT_JAVA_17_UTILITIES);
		}
		// JDK 21 for UBI 8 and UBI 9
		else if (OpenJDKTestConfig.isOpenJDK21()){
			LOGGER.info("DockerImageTest:javaUtilitiesTest::Running check for jdk21");
			Assertions.assertThat(content.listDirContent("$JAVA_HOME/bin")).contains(super.DEFAULT_JAVA_21_UTILITIES);
		}
		else {
			LOGGER.info("DockerImageTest:javaUtilitiesTest::Error, jdk version not supported. Please check jdk container image.");
			super.javaUtilitiesTest();
		}

	}

	@Override
	@Test
	public void javaVersionTest() {
		Assertions.assertThat(content.javaVersion()).contains(OpenJDKTestConfig.product().version());
	}


	private static Map<String, String> getExpectedEnvironments() {
		final Map<String, String> result = new HashMap<>();
		// With the ubi9 (RHEL 9) images the user has been changed from /home/jboss to /home/default/
		if (OpenJDKTestConfig.isRHEL9()) {
			result.put("HOME", "/home/default");
		} else {
			result.put("HOME", "/home/jboss");
		}
		// Supported versions of OpenJDK 8,11, 17, 21
		if (OpenJDKTestConfig.isOpenJDK21()) {
			result.put("JAVA_HOME", "/usr/lib/jvm/java-21");
		} else if (OpenJDKTestConfig.isOpenJDK17()) {
			result.put("JAVA_HOME", "/usr/lib/jvm/java-17");
		} else {
			result.put("JAVA_HOME", OpenJDKTestConfig.isOpenJDK11() ? "/usr/lib/jvm/java-11" : "/usr/lib/jvm/java-1.8.0");
		}
		result.put("JAVA_VENDOR", OpenJDKTestConfig.isOpenJ9() ? "AdoptOpenJDK" : "openjdk");

		if (OpenJDKTestConfig.isOpenJDK21()) {
			result.put("JAVA_VERSION", "21");
		} else if (OpenJDKTestConfig.isOpenJDK17()) {
			result.put("JAVA_VERSION", "17");
		} else {
			result.put("JAVA_VERSION", OpenJDKTestConfig.isOpenJDK11() ? "11" : "1.8.0");
			// With ubi9, Jolokia was removed from all images.
			if (!OpenJDKTestConfig.isRHEL9()) {
				result.put("JOLOKIA_VERSION", "1.6.2");
				result.put("AB_JOLOKIA_PASSWORD_RANDOM", "true");
				result.put("AB_JOLOKIA_AUTH_OPENSHIFT", "true");
			}
		}
		if (OpenJDKTestConfig.isRHEL7()) {
			result.put("MAVEN_VERSION", "3.6");
		} else {
			result.put("MAVEN_VERSION", "3.8");
		}


		result.put("JAVA_DATA_DIR", "/deployments/data");

		return Collections.unmodifiableMap(result);
	}
}
