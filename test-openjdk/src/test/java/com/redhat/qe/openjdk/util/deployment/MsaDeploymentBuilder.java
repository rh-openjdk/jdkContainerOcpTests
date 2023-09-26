package com.redhat.qe.openjdk.util.deployment;

import com.redhat.qe.openjdk.util.ProcessKeystoreGenerator;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;

import org.junit.jupiter.api.Assertions;

import com.redhat.qe.openjdk.OpenJDKTestConfig;
import com.redhat.qe.openjdk.jolokia.JolokiaConfiguration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import cz.xtf.builder.builders.ApplicationBuilder;
import cz.xtf.builder.builders.deployment.AbstractProbe;
import cz.xtf.builder.builders.limits.ComputingResource;
import cz.xtf.builder.builders.pod.ContainerBuilder;
import cz.xtf.builder.builders.pod.PersistentVolumeClaim;
import cz.xtf.builder.db.OpenShiftAuxiliary;
import cz.xtf.client.Http;
import cz.xtf.core.bm.BinaryBuild;
import cz.xtf.core.bm.BuildManagers;
import cz.xtf.core.bm.ManagedBuild;
import cz.xtf.core.config.BuildManagerConfig;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Radek Koubsky (rkoubsky@redhat.com)
 */
@Slf4j
public class MsaDeploymentBuilder {
	private final String appName;
	private int replicas;
	private BinaryBuild build;
	private final String hostname;
	private String urlSuffix;
	private final MsaDeploymentType deyplotmentType;
	private boolean ssl = false;
	private boolean deleteCaFromKeyStore = false;
	private String secureHostName;
	private String secureUrlSuffix;
	private Map<String, String> deploymentEnvironmentVariables = Collections.emptyMap();
	private boolean generateMirrorSettings = true;
	private ComputingResource cpuResource;
	private ComputingResource memoryResource;
	private JolokiaConfiguration jolokiaConf = new JolokiaConfiguration.Builder().build();
	private String jolokiaHostname;
	private Consumer<ContainerBuilder> containerModification;
	private OpenShiftAuxiliary database;
	private boolean dbPostDeployment;
	private String dbPostDeploymentName;
	private ApplicationBuilder appBuilder;
	private AbstractProbe readinessProbe;
	private PersistentVolumeClaim persistentVolumeClaim;
	private String pvcMountPath;

	public MsaDeploymentBuilder(final String appName, final MsaDeploymentType deploymentType) {
		this.appName = appName;
		this.replicas = 1;
		this.hostname = OpenShifts.master().generateHostname(appName);
		this.urlSuffix = null;
		this.deyplotmentType = deploymentType;
	}

	public static MsaDeploymentBuilder withNewApp(final String appName, final MsaDeploymentType deploymentType) {
		return new MsaDeploymentBuilder(appName, deploymentType);
	}

	public static MsaDeploymentBuilder withNewSpringBootApp(final String appName) {
		return new MsaDeploymentBuilder(appName, MsaDeploymentType.SPRING_BOOT);
	}

	public static MsaDeploymentBuilder withNewWildFlySwarmApp(final String appName) {
		return new MsaDeploymentBuilder(appName, MsaDeploymentType.WILDFLY_SWARM);
	}

	public static MsaDeploymentBuilder withNewJavaS2IApp(final String appName) {
		return new MsaDeploymentBuilder(appName, MsaDeploymentType.JAVA_S2I);
	}

	public MsaDeploymentBuilder withBuild(final ManagedBuild build) {
		this.build = (BinaryBuild) build;
		return this;
	}

	public MsaDeploymentBuilder replicas(final int replicas) {
		this.replicas = replicas;
		return this;
	}

	public MsaDeploymentBuilder urlCheck(final String urlSuffix) {
		this.urlSuffix = urlSuffix;
		return this;
	}

	public MsaDeploymentBuilder secureUrlCheck(final String secureUrlSuffix) {
		this.secureUrlSuffix = secureUrlSuffix;
		return this;
	}
	
	public MsaDeploymentBuilder withSsl() {
		this.ssl = true;
		this.deleteCaFromKeyStore = false; 
		return this;
	}
	
	public MsaDeploymentBuilder withSsl(boolean deleteCaFromKeyStore) {
		this.ssl = true;
		this.deleteCaFromKeyStore = deleteCaFromKeyStore; 
		return this;
	}

	public MsaDeploymentBuilder withDatabase(final OpenShiftAuxiliary db, final boolean postDeployment) {
		this.database = db;
		this.dbPostDeployment = postDeployment;
		return this;
	}

	public MsaDeploymentBuilder withDeploymentEnvironmentVariables(final Map<String, String> envVars) {
		this.deploymentEnvironmentVariables = envVars;
		return this;
	}

	public MsaDeploymentBuilder generateMirrorSettings(final boolean generate) {
		this.generateMirrorSettings = generate;
		return this;
	}

	public MsaDeploymentBuilder withCPUResource(final ComputingResource cpuResource) {
		this.cpuResource = cpuResource;
		return this;
	}

	public MsaDeploymentBuilder withMemoryResource(final ComputingResource memoryResource) {
		this.memoryResource = memoryResource;
		return this;
	}

	public MsaDeploymentBuilder configureJolokia(final JolokiaConfiguration jolokiaConf) {
		this.jolokiaConf = jolokiaConf;
		return this;
	}

	public MsaDeploymentBuilder withContainerModification(final Consumer<ContainerBuilder> containerModification) {
		this.containerModification = containerModification;
		return this;
	}

	public MsaDeploymentBuilder withReadinessProbe(final AbstractProbe readinessProbe) {
		this.readinessProbe = readinessProbe;
		return this;
	}

	public MsaDeploymentBuilder withPersistentVolumeClaim(PersistentVolumeClaim persistentVolumeClaim, String mountPath) {
		this.persistentVolumeClaim = persistentVolumeClaim;
		this.pvcMountPath = mountPath;
		return this;
	}

	private void createMavenProxyConfXml(Path appDirectory) {
		File m2SettingsXml = null;
		if (OpenJDKTestConfig.isMavenProxyEnabled()) {
			// create only if the configuration/settings.xml doesn't exist already
			if (!appDirectory.resolve("configuration").toFile().isDirectory()) {
				appDirectory.resolve("configuration").toFile().mkdirs();
			}
			m2SettingsXml = appDirectory.resolve("configuration").resolve("settings.xml").toFile();
			//clean if exists
			if (m2SettingsXml.isFile()) {
				m2SettingsXml.delete();
			}
			try (FileWriter fw = new FileWriter(m2SettingsXml)) {
				fw.write(String.format("<settings>\n" +
						"  <mirrors>\n" +
						"    <mirror>\n" +
						"      <id>s2i-mirror</id>\n" +
						"      <url>%s</url>\n" +
						"      <mirrorOf>external:*</mirrorOf>\n" +
						"    </mirror>\n" +
						"  </mirrors>\n" +
						"  <profiles>\n" +
						"    <profile>\n" +
						"      <id>springboot-upstream</id>\n" +
						"      <repositories>\n" +
						"        <repository>\n" +
						"          <id>springboot-upstream-repository-snapshots</id>\n" +
						"          <url>https://repository.jboss.org/nexus/content/repositories/snapshots/</url>\n" +
						"          <releases>\n" +
						"            <enabled>true</enabled>\n" +
						"          </releases>\n" +
						"          <snapshots>\n" +
						"            <enabled>true</enabled>\n" +
						"          </snapshots>\n" +
						"        </repository>\n" +
						"      </repositories>\n" +
						"      <pluginRepositories>\n" +
						"        <pluginRepository>\n" +
						"          <id>springboot-upstream-repository-snapshots</id>\n" +
						"          <url>https://repository.jboss.org/nexus/content/repositories/snapshots/</url>\n" +
						"          <releases>\n" +
						"            <enabled>true</enabled>\n" +
						"          </releases>\n" +
						"          <snapshots>\n" +
						"            <enabled>true</enabled>\n" +
						"          </snapshots>\n" +
						"        </pluginRepository>\n" +
						"      </pluginRepositories>\n" +
						"    </profile>\n" +
						"  </profiles>\n" +
						"  <activeProfiles>\n" +
						"    <activeProfile>springboot-upstream</activeProfile>\n" +
						"  </activeProfiles>\n" +
						"</settings>\n", OpenJDKTestConfig.mavenProxyUrl()));
			} catch (IOException e) {
				//no op
			}
		}
	}

	private void createMavenProxyConf() {
		if (this.generateMirrorSettings) {
			createMavenProxyConfXml(this.build.getPath());
		}
	}
	

	public void deploy() {
		createMavenProxyConf();
		final String checkUrl = this.urlSuffix != null ? "http://" + this.hostname + "/" + this.urlSuffix : null;
		checkUrlUnavailable(checkUrl);


		this.appBuilder = ApplicationBuilder.fromManagedBuild(this.appName, BuildManagers.get().getBuildReference(build));

		if (this.containerModification != null) {
			this.containerModification.accept(this.appBuilder.deploymentConfig(this.appName)
					.podTemplate()
					.container());
		}

		log.info("Creating deployment config for {} with {} replicas.", this.appName, this.replicas);
		this.appBuilder.deploymentConfig(this.appName)
				.setReplicas(this.replicas)
				.podTemplate()
				.container()
				.envVars(this.deploymentEnvironmentVariables);
		this.appBuilder.service().port("http", 8080, 80);
		this.appBuilder.route();

		configureJolokia(this.appBuilder);

		if (this.persistentVolumeClaim != null) {
			this.appBuilder.deploymentConfig().podTemplate()
					.addPersistenVolumeClaim(this.persistentVolumeClaim.getName(), this.persistentVolumeClaim.getClaimName());
			this.appBuilder.deploymentConfig().podTemplate().container()
					.addVolumeMount(this.persistentVolumeClaim.getName(), this.pvcMountPath, false);
		}

		addComputingResources(this.appBuilder);
		addProbes();
		configureSsl(this.appBuilder);
		createDatabase(this.appBuilder, this.dbPostDeployment);
		final String secureCheckUrl = this.secureUrlSuffix != null ? "https://" + this.secureHostName + "/" + this.secureUrlSuffix : null;

		// wait for build to finish
		if (build != null) {
			// fallback if build is not started via `@UsesBuild` annotation
			if (!build.isPresent(OpenShifts.master(BuildManagerConfig.namespace()))) {
				BuildManagers.get().deploy(build);
			}
			BuildManagers.get().hasBuildCompleted(build).waitFor();
		}

		this.appBuilder.buildApplication().deploy();
		OpenShifts.master().waiters().areExactlyNPodsReady(this.replicas, this.appName).waitFor();
		waitForCheckUrl(checkUrl);
		waitForCheckUrl(secureCheckUrl);

		//final Map<String, AbstractServerDeployment> deploymentOutputMap = new HashMap<>();
		//insertDeployment(deploymentOutputMap);
		deleteMavenProxyConf();
//		if (this.database != null) {
//			return new Deployment(null, new HashMap<String, GitProject>(), deploymentOutputMap, "db");
//		} else {
//			return new Deployment(null, new HashMap<String, GitProject>(), deploymentOutputMap, "no-db");
//		}
		
	}

	private void addProbes() {
		if (this.readinessProbe != null) {
			this.appBuilder.deploymentConfig()
					.podTemplate()
					.container()
					.addReadinessProbe(this.readinessProbe);
		}
	}

	private void configureJolokia(final ApplicationBuilder appBuilder) {
		if (isJolokiaEnabled()) {
			appBuilder.deploymentConfig()
					.podTemplate()
					.container()
					.envVar("AB_JOLOKIA_CONFIG", this.jolokiaConf.getConfigFile())
					.envVar("AB_JOLOKIA_AUTH_OPENSHIFT", String.valueOf(this.jolokiaConf.isAuthOpenShift()))
					.envVar("AB_JOLOKIA_HOST", this.jolokiaConf.getHost())
					.envVar("AB_JOLOKIA_PORT", String.valueOf(this.jolokiaConf.getPort()))
					.envVar("AB_JOLOKIA_DISCOVERY_ENABLED", String.valueOf(this.jolokiaConf.isDiscoveryEnabled()))
					.envVar("AB_JOLOKIA_USER", this.jolokiaConf.getUser())
					.envVar("AB_JOLOKIA_HTTPS", String.valueOf(this.jolokiaConf.getHttps()))
					.envVar("AB_JOLOKIA_PASSWORD", this.jolokiaConf.getPassword())
					.envVar("AB_JOLOKIA_PASSWORD_RANDOM", String.valueOf(this.jolokiaConf.isRandomPassword()))
					.envVar("AB_JOLOKIA_OPTS", this.jolokiaConf.getAdditionalOpts());
			//default Jolokia access through OSE auth "Connect"
			appBuilder.deploymentConfig()
					.podTemplate()
					.container()
					.port(this.jolokiaConf.getPort(), this.jolokiaConf.getPortName());
			if (!this.jolokiaConf.isAuthOpenShift()) {
				//expose Jolokia API if it's insecure
				final String serviceRouteLabel = this.appName + "-jk";
				appBuilder.service(serviceRouteLabel)
						.port(this.jolokiaConf.getPort());
				this.jolokiaHostname = OpenShifts.master().generateHostname(serviceRouteLabel);
				appBuilder.route(serviceRouteLabel)
						.forService(serviceRouteLabel)
						.exposedAsHost(this.jolokiaHostname);
			}
		} else {
			appBuilder.deploymentConfig(this.appName)
					.podTemplate()
					.container()
					.envVar("AB_JOLOKIA_OFF", "true");
		}
	}

	private void addComputingResources(final ApplicationBuilder appBuilder) {
		if (this.cpuResource != null) {
			final ComputingResource resource = appBuilder.deploymentConfig(this.appName)
					.podTemplate()
					.container()
					.addCPUResource();
			resource.setRequests(this.cpuResource.getRequests());
			resource.setLimits(this.cpuResource.getLimits());
		}

		if (this.memoryResource != null) {
			final ComputingResource resource = appBuilder.deploymentConfig(this.appName)
					.podTemplate()
					.container()
					.addMemoryResource();
			resource.setRequests(this.memoryResource.getRequests());
			resource.setLimits(this.memoryResource.getLimits());
		}
	}

	private void deleteMavenProxyConf() {
		if (this.generateMirrorSettings) {
			log.info("Deleting configuration dir with settings.xml");
			try {
				Files.deleteIfExists(this.build.getPath().resolve("configuration/settings.xml"));
				Files.deleteIfExists((this.build.getPath()).resolve("configuration"));
			} catch (final IOException e) {
				log.debug("Cannot delete configuration dir.", e);
			}
		}
	}

	private void createDatabase(final ApplicationBuilder appBuilder, final boolean postDeployment) {
		if (this.database != null) {
			if (postDeployment) {
				this.database.configureApplicationDeployment(appBuilder.deploymentConfig());
				this.dbPostDeploymentName = this.database.configureDeployment(appBuilder, false).setReplicas(0).getName();
			} else {
				appBuilder.addDatabase(this.database);
			}
		}
	}
	
	public void postDbDeployment() {
		if (this.database != null && this.appBuilder != null && this.dbPostDeploymentName != null) {
			log.info("Database post deployment");
			OpenShifts.master().scale(this.dbPostDeploymentName, 1);
			OpenShifts.master().deployLatest(this.dbPostDeploymentName);
		}
	}
	
	private void configureSsl(final ApplicationBuilder appBuilder) {
		if (this.ssl) {
			OpenShift openShift = OpenShifts.master();
			this.secureHostName = openShift.generateHostname(this.appName + "-secure");
			final Path keystorePath = ProcessKeystoreGenerator.generateKeystore(this.secureHostName, this.appName, this.deleteCaFromKeyStore);

			final SecretBuilder sb = new SecretBuilder();
			final String secretName = this.appName + "-ssl";
			sb.withNewMetadata()
					.withName(secretName)
					.endMetadata();
			try {
				sb.addToData("keystore",
						Base64.encodeBase64String(FileUtils.readFileToByteArray(keystorePath.toFile())));
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}

			openShift.createSecret(sb.build());

			appBuilder.deploymentConfig(this.appName)
					.podTemplate()
					.container()
					.addVolumeMount("ssl", "/ssl", true);
			appBuilder.deploymentConfig(this.appName)
					.podTemplate()
					.container()
					.port(8443);
			appBuilder.deploymentConfig(this.appName)
					.podTemplate()
					.addSecretVolume("ssl", secretName);
			appBuilder.service(this.appName + "-https")
					.port("https" ,8443, 443);
			appBuilder.route(this.appName + "-secure")
					.exposedAsHost(this.secureHostName)
					.passthrough()
					.forService(
							this.appName + "-https");
		}
	}

	private void waitForCheckUrl(final String checkUrl) {
		if (checkUrl != null) {
			try {
				Http.get(checkUrl).trustAll().waiters().ok().waitFor();
			} catch (MalformedURLException e) {
				log.error(e.getMessage());
			}
		}
	}

//	private void insertDeployment(final Map<String, AbstractServerDeployment> deploymentOutputMap) {
//		switch (this.deyplotmentType) {
//			case SPRING_BOOT:
//				deploymentOutputMap.put(this.appName, new SpringBootDeployment(this.appName, this.hostname, null,
//						this.secureHostName));
//				break;
//			case JAVA_S2I:
//				deploymentOutputMap.put(this.appName,
//						new JavaS2IDeployment(this.appName, this.hostname, null, this.jolokiaHostname));
//				break;
//			case WILDFLY_SWARM:
//				deploymentOutputMap.put(this.appName,
//						new WildFlySwarmDeployment(this.appName, this.hostname, null, this.secureHostName));
//		}
//	}

	private void checkUrlUnavailable(final String checkUrl) {
		if (checkUrl != null) {
			try {
				Assertions.assertEquals(503, Http.get(checkUrl).execute().code());
			} catch (final Exception x) {
				// ignore
			}
		}
	}

//	private void reportRestartFailure(final String appName) {
//		OpenShiftBinaryClient.getInstance()
//				.project(OpenshiftUtil.getInstance()
//						.getContext()
//						.getNamespace());
//		final String previousLogs = OpenShiftBinaryClient.getInstance()
//				.executeCommandWithReturn(
//						"Pod " + appName + " has restarted, presumably failing. Cannot retreive previous pod logs, sorry.",
//						"logs", OpenshiftUtil.getInstance()
//								.findNamedPod(appName)
//								.getMetadata()
//								.getName(), "-p");
//
//		Assertions.fail("Pod " + appName + " has restarted, presumably failing. Previous pod logs:\n" + previousLogs);
//	}

	public boolean isJolokiaEnabled() {
		return this.jolokiaConf != null && this.jolokiaConf.isEnabled();
	}
}
