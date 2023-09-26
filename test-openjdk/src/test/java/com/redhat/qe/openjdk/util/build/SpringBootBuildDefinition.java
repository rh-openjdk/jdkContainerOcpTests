package com.redhat.qe.openjdk.util.build;

import com.redhat.qe.openjdk.OpenJDKTestConfig;
import com.redhat.qe.openjdk.OpenJDKTestParent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import cz.xtf.core.bm.BinaryBuild;
import cz.xtf.core.bm.BinarySourceBuild;
import cz.xtf.junit5.interfaces.BuildDefinition;
import lombok.Getter;

/**
 * @author Radek Koubsky (rkoubsky@redhat.com)
 */
@Getter
public class SpringBootBuildDefinition implements BuildDefinition {

	private final static String MODULE_NAME = "springboot";

	private String buildName;
	private Path path;
	private Map<String, String> envProperties;

	private BinarySourceBuild managedBuild = null;

	public SpringBootBuildDefinition(String appName) {
		init(appName, OpenJDKTestParent.findApplicationDirectory(appName, MODULE_NAME), null);
	}

	public SpringBootBuildDefinition(String appName, String buildName, Map<String, String> envProperties) {
		init(buildName, OpenJDKTestParent.findApplicationDirectory(appName, MODULE_NAME), envProperties);
	}

	private void init(String buildName, Path path, Map<String, String> envProperties) {
		this.buildName = buildName;
		this.path = path;
		this.envProperties = envProperties;
	}

	public Map<String, String> getEnvProperties() {
		System.out.println("PRINTOUT THE MAP OF STRINGS.");
		for (String key : envProperties.keySet()){
			System.out.println(key + " = " + envProperties.get(key));
		}
		System.out.println();
		return envProperties;
	}

	@Override
	public BinaryBuild getManagedBuild() {

		if (managedBuild == null) {
			try {
				Path preparedSources = OpenJDKTestParent.prepareProjectSources(buildName, path);

				Map<String, String> properties = new HashMap<>();

				if (envProperties != null) {
					properties.putAll(envProperties);
				}

				if (OpenJDKTestConfig.isMavenProxyEnabled()) {
					properties.put("MAVEN_MIRROR_URL", OpenJDKTestConfig.mavenProxyUrl());
				}

				managedBuild = new BinarySourceBuild(OpenJDKTestConfig.imageUrl(), preparedSources, properties, buildName);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		return managedBuild;
	}
}
