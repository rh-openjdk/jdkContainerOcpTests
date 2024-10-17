package com.redhat.qe.openjdk.image;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.assertj.core.api.Assertions;

import com.redhat.qe.openjdk.OpenJDKTestConfig;
import com.redhat.qe.openjdk.OpenJDKTestParent;

import java.util.List;
import java.util.stream.Collectors;

import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.testhelpers.image.ImageContent;
import cz.xtf.testhelpers.image.ImageMetadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class AbstractDockerImageTest extends OpenJDKTestParent {
	public static final String JBOSS_PRODUCT = "org.jboss.product";
	public static final String JBOSS_PRODUCT_VERSION = "org.jboss.product.version";
	public static final String JBOSS_PRODUCT_JDK_VERSION = "org.jboss.product.openjdk.version";
	private static final Logger LOGGER = LoggerFactory.getLogger(DockerImageTest.class);

	public static final String[] DEFAULT_JAVA_8_UTILITIES = new String[]{
			"alt-java", "appletviewer", "clhsdb", "extcheck",
			"hsdb", "idlj", "jar", "jarsigner", "java",
			"java-rmi.cgi", "javac", "javadoc", "javah",
			"javap", "jcmd", "jconsole", "jdb",
			"jdeps", "jfr", "jhat", "jinfo", "jjs",
			"jmap", "jps", "jrunscript", "jsadebugd",
			"jstack", "jstat", "jstatd", "keytool",
			"native2ascii", "orbd", "pack200", "policytool",
			"rmic", "rmid", "rmiregistry", "schemagen",
			"serialver", "servertool", "tnameserv",
			"unpack200", "wsgen", "wsimport", "xjc"
	};
	public static final String[] DEFAULT_JAVA_11_UTILITIES = new String[]{
			"alt-java", "jaotc", "jar", "jarsigner", "java",
			"javac", "javadoc", "javap", "jcmd", "jconsole",
			"jdb", "jdeprscan", "jdeps", "jfr", "jhsdb",
			"jimage", "jinfo", "jjs", "jlink", "jmap",
			"jmod", "jps", "jrunscript", "jshell", "jstack",
			"jstat", "jstatd", "keytool", "pack200", "rmic",
			"rmid", "rmiregistry", "serialver", "unpack200"
	};
	public static final String[] DEFAULT_IBM_P_Z_JAVA_11_UTILITIES = new String[]{
		"alt-java", "jar", "jarsigner", "java",
		"javac", "javadoc", "javap", "jcmd", "jconsole",
		"jdb", "jdeprscan", "jdeps", "jfr", "jhsdb",
		"jimage", "jinfo", "jjs", "jlink", "jmap",
		"jmod", "jps", "jrunscript", "jshell", "jstack",
		"jstat", "jstatd", "keytool", "pack200", "rmic",
		"rmid", "rmiregistry", "serialver", "unpack200"
  };
	public static final String[] DEFAULT_JAVA_17_UTILITIES = new String[]{
			"alt-java", "jar", "jarsigner", "java", "javac",
			"javadoc", "javap", "jcmd", "jconsole", "jdb",
			"jdeprscan", "jdeps", "jfr", "jhsdb", "jimage",
			"jinfo", "jlink", "jmap", "jmod", "jpackage",
			"jps", "jrunscript", "jshell", "jstack", "jstat",
			"jstatd", "keytool", "rmiregistry", "serialver"
	};
	public static final String[] DEFAULT_JAVA_21_UTILITIES = new String[]{
			"alt-java", "jarsigner", "javac", "javap", "jconsole",
			"jdeprscan", "jfr", "jimage", "jlink", "jmod", "jps",
			"jshell", "jstat", "jwebserver", "rmiregistry", "jar",
			"java", "javadoc", "jcmd", "jdb", "jdeps", "jhsdb",
			"jinfo", "jmap", "jpackage", "jrunscript", "jstack",
			"jstatd", "keytool", "serialver"
	};
	public static final String RED_HAT_RELEASE_KEY_2 = "199e2f91fd431d51";

	protected static final OpenShift openShift = OpenShifts.master();

	protected static ImageMetadata metadata;
	protected static ImageContent content;

	@BeforeAll
	public static void cleanNamespace() {
		openShift.clean().waitFor();
	}

	@Test
	public void packageSigningTest() {
		// https://access.redhat.com/security/team/key
		List<ImageContent.RpmPackage> unsignedRpms = content.rpms().stream().filter(rpm -> !rpm.getSignature().endsWith(RED_HAT_RELEASE_KEY_2)).collect(Collectors.toList());

		if(unsignedRpms.size() > 0) {
			Assertions.assertThat(unsignedRpms).hasSize(2);
			if (OpenJDKTestConfig.isRHEL9()){
				Assertions.assertThat(unsignedRpms.stream().map(x -> x.getName() + "-" + x.getVersion() + "-" + x.getRelease()))
						.containsExactlyInAnyOrder("gpg-pubkey-5a6340b3-6229229e", ImageContent.RED_HAT_RELEASE_KEY_2_RPM);
			}
			else if (OpenJDKTestConfig.isRHEL8()) {
				Assertions.assertThat(unsignedRpms.stream().map(x -> x.getName() + "-" + x.getVersion() + "-" + x.getRelease()))
						.containsExactlyInAnyOrder("gpg-pubkey-d4082792-5b32db75", ImageContent.RED_HAT_RELEASE_KEY_2_RPM);
			} else {
				Assertions.assertThat(unsignedRpms.stream().map(x -> x.getName() + "-" + x.getVersion() + "-" + x.getRelease()))
						.containsExactlyInAnyOrder(ImageContent.RED_HAT_AUXILIARY_KEY_RPM, ImageContent.RED_HAT_RELEASE_KEY_2_RPM);
			}
		}
	}

	@Test
	public void illegalRepositoriesTest() {
		if (OpenJDKTestConfig.isRHEL8() || OpenJDKTestConfig.isRHEL9()) {
			LOGGER.info("DockerImageTest:illegalRepositoriesTest::This test is checking against UBI8 or UBI9 image." );
			Assertions.assertThat(content.listDirContent("/etc/yum.repos.d/")).containsExactly("redhat.repo", "ubi.repo");
		} else {
			LOGGER.info("DockerImageTest:illegalRepositoriesTest::This test is checking against a Rhel7-based image." );
			Assertions.assertThat(content.listDirContent("/etc/yum.repos.d/")).isEmpty();
		}
	}

	@Test
	public void javaUtilitiesTest() {
		// This should only be called for an unsupported JDK. Every other version should be handled in the DockerImageTest file.
		Assertions.assertThat(content.listDirContent("$JAVA_HOME/bin")).contains("NOTSUPPORTED");
	}

	@Test
	public abstract void javaVersionTest();

}
