package com.redhat.qe.openjdk;

import com.redhat.qe.openjdk.util.maven.PomModifier;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import cz.xtf.builder.builders.ApplicationBuilder;
import cz.xtf.core.openshift.OpenShifts;
import io.fabric8.openshift.api.model.Build;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenJDKTestParent {

	protected static final Path TMP_SOURCES_DIR = Paths.get("tmp").toAbsolutePath().resolve("sources");
	protected static final String SUITE_NAME = "test-openjdk";

	public static Path findProjectRoot() {
		Path dir = Paths.get("").toAbsolutePath();
		for (; dir.getParent().resolve("pom.xml").toFile().exists(); dir = dir.getParent()) ;
		return dir;
	}

	public static Path prepareProjectSources(final String appName, final Path projectDir) throws IOException {
		if (projectDir == null) {
			return null;
		}

		Files.createDirectories(TMP_SOURCES_DIR);
		Path sourcesDir = Files.createTempDirectory(TMP_SOURCES_DIR.toAbsolutePath(), appName);
		FileUtils.copyDirectory(projectDir.toFile(), sourcesDir.toFile());

		new PomModifier().modify(projectDir, sourcesDir);

		return sourcesDir;
	}

	public static ApplicationBuilder appFromBinaryBuild(final String appName) {
		ApplicationBuilder appBuilder = new ApplicationBuilder(appName);
		appBuilder.buildConfig().setOutput(appName).sti().forcePull(true).fromDockerImage(OpenJDKTestConfig.imageUrl());
		appBuilder.imageStream();
		appBuilder.deploymentConfig().onImageChange().onConfigurationChange().podTemplate().container().fromImage(appName);

		appBuilder.buildConfig().withBinaryBuild();
		if (OpenJDKTestConfig.isMavenProxyEnabled()) {
			appBuilder.buildConfig().sti().addEnvVariable("MAVEN_MIRROR_URL", OpenJDKTestConfig.mavenProxyUrl());
		}

		return appBuilder;
	}

	public static Path findApplicationDirectory(String appName) {
		return findApplicationDirectory(SUITE_NAME, appName, null);
	}

	public static Path findApplicationDirectory(String appName, String appModuleName) {
		return findApplicationDirectory(SUITE_NAME, appName, appModuleName);
	}

	public static Path findApplicationDirectory(String moduleName, String appName, String appModuleName) {
		Path path;
		path = FileSystems.getDefault().getPath("src/test/resources/apps", appName);

		/* We only return this path if the absolute path contains the moduleName,
		    e.g. if both  test-eap and test-common contain "foo", but we explicitly want
		    the test-common/src/test/resources/apps/foo
		  */
		if (Files.exists(path) && path.toAbsolutePath().toString().contains(moduleName)) {
			return path;
		}
		log.debug("Path {} does not exist", path.toAbsolutePath());
		if (appModuleName != null) {
			path = FileSystems.getDefault().getPath("src/test/resources/apps/" + appModuleName, appName);
			if (Files.exists(path) && path.toAbsolutePath().toString().contains(moduleName)) {
				return path;
			}
			log.info("Path {} does not exist", path.toAbsolutePath());
		}
		path = FileSystems.getDefault().getPath(moduleName + "/src/test/resources/apps", appName);
		if (Files.exists(path)) {
			return path;
		}
		log.info("Path {} does not exist", path.toAbsolutePath());
		if (appModuleName != null) {
			path = FileSystems.getDefault().getPath(moduleName + "/src/test/resources/apps/" + appModuleName, appName);
			if (Files.exists(path) && path.toAbsolutePath().toString().contains(moduleName)) {
				return path;
			}
			log.info("Path {} does not exist", path.toAbsolutePath());
		}
		path = FileSystems.getDefault().getPath("../" + moduleName + "/src/main/resources/apps", appName);
		if (Files.exists(path)) {
			return path;
		}
		log.info("Path {} does not exist", path.toAbsolutePath());
		path = FileSystems.getDefault().getPath("../" + moduleName + "/src/test/resources/apps", appName);
		if (Files.exists(path)) {
			return path;
		}
		log.info("Path {} does not exist", path.toAbsolutePath());
		throw new IllegalArgumentException("Cannot find directory with STI app sources");
	}

	public static Build startBinaryBuild(final String bcName, Path sources) throws IOException {
		try {
			PipedOutputStream pos = new PipedOutputStream();
			PipedInputStream pis = new PipedInputStream(pos);

			ExecutorService executorService = Executors.newSingleThreadExecutor();
			final Future<?> future = executorService.submit(() -> {
				Collection<File> filesToArchive = FileUtils.listFiles(sources.toFile(), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
				try (TarArchiveOutputStream o = (TarArchiveOutputStream) new ArchiveStreamFactory().createArchiveOutputStream(ArchiveStreamFactory.TAR, pos)) {
					for (File f : filesToArchive) {
						String tarPath = sources.relativize(f.toPath()).toString();
						log.trace("adding file to tar: {}", tarPath);
						ArchiveEntry entry = o.createArchiveEntry(f, tarPath);

						// we force the modTime in the tar, so that the resulting tars are binary equal if their contents are
						TarArchiveEntry tarArchiveEntry = (TarArchiveEntry)entry;
						tarArchiveEntry.setModTime(Date.from(Instant.EPOCH));

						o.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
						o.putArchiveEntry(tarArchiveEntry);
						if (f.isFile()) {
							try (InputStream i = Files.newInputStream(f.toPath())) {
								IOUtils.copy(i, o);
							}
						}
						o.closeArchiveEntry();
					}

					o.finish();
				} catch (ArchiveException e) {
					throw new RuntimeException(e);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});

			Build ret = OpenShifts.master().buildConfigs().withName(bcName).instantiateBinary().fromInputStream(pis);
			future.get();

			return ret;
		} catch (InterruptedException e) {
			log.error("IOException building {}", bcName, e);
			throw new RuntimeException(e);
		} catch (ExecutionException e) {
			log.error("IOException building {}", bcName, e);
			throw new RuntimeException(e);
		}
	}
}
