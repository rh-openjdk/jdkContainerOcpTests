package com.redhat.qe.openjdk.util;

import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.openshift.OpenShifts;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class OpenShiftBinaryClient {
    private static OpenShiftBinaryClient INSTANCE;
    private static String ocBinaryPath;

    private static final File WORKDIR = Paths.get("tmp").toAbsolutePath().resolve("oc").toFile();
    private static final File CONFIG_FILE = new File(WORKDIR, "oc.config");

    private OpenShiftBinaryClient() {
        ocBinaryPath = OpenShifts.getBinaryPath();
    }

    public static OpenShiftBinaryClient getInstance() {
        if (INSTANCE == null) {
            try {
                INSTANCE = new OpenShiftBinaryClient();
                //call oc login to create ~/.kube/config
                login();
            } catch (MalformedURLException ex) {
                throw new IllegalArgumentException("OpenShift Master URL is malformed", ex);
            } catch (IOException ex) {
                throw new IllegalArgumentException("Can't get oc binary", ex);
            } catch (InterruptedException ex) {
                throw new IllegalArgumentException("Init failed", ex);
            }
        }
        return INSTANCE;
    }

    public void project(String projectName) {
        executeCommand("oc project failed", "project", projectName);
    }

    /**
     * Expose executeCommand with oc binary preset
     *
     * @param error error message on failure
     * @param args command arguments
     */
    public static void executeCommand(String error, String... args) {
        String[] ocArgs = ArrayUtils.addAll(new String[] {ocBinaryPath, "--kubeconfig=" + CONFIG_FILE.getAbsolutePath()}, args);
        try {
            executeLocalCommand(error, ocArgs);
        } catch (IOException | InterruptedException ex) {
            throw new IllegalArgumentException(error, ex);
        }
    }

    /**
     * Executes oc command and returns Process
     *
     * @param args command arguments
     * @return Process encapsulating started oc
     */
    public Process executeCommandNoWait(final String error, String... args) {
        String[] ocArgs = ArrayUtils.addAll(new String[] {ocBinaryPath, "--kubeconfig=" + CONFIG_FILE.getAbsolutePath()}, args);
        try {
            return executeLocalCommand(error, false, false, false, ocArgs);
        } catch (IOException | InterruptedException ex) {
            throw new IllegalArgumentException(error, ex);
        }
    }

    public Process executeCommandNoWaitWithOutputAndError(final String error, String... args) {
        String[] ocArgs = ArrayUtils.addAll(new String[] {ocBinaryPath, "--kubeconfig=" + CONFIG_FILE.getAbsolutePath()}, args);
        try {
            return executeLocalCommand(error, false, true, true, ocArgs);
        } catch (IOException | InterruptedException ex) {
            throw new IllegalArgumentException(error, ex);
        }
    }

    /**
     * Executes oc command and returns a String
     *
     * @param args command arguments
     * @return Process encapsulating started oc
     */
    public String executeCommandWithReturn(final String error, String... args) {
        String[] ocArgs = (String[]) ArrayUtils.addAll(new String[] {ocBinaryPath, "--kubeconfig=" + CONFIG_FILE.getAbsolutePath()}, args);
        try {
            final Process process = executeLocalCommand(error, false, true, false,
                    ocArgs);
            try (final InputStream is = process.getInputStream();
                 final StringWriter sw = new StringWriter()) {
                org.apache.commons.io.IOUtils.copy(is, sw, StandardCharsets.UTF_8);
                return sw.toString();
            }
        } catch (IOException | InterruptedException ex) {
            throw new IllegalArgumentException(error, ex);
        }
    }

    /**
     * Executes oc command and consume output
     */
    public void executeCommandAndConsumeOutput(final String error, CommandResultConsumer consumer, String... args) {
        String[] ocArgs = ArrayUtils.addAll(new String[] {ocBinaryPath, "--kubeconfig=" + CONFIG_FILE.getAbsolutePath()}, args);
        try {
            final Process process = executeLocalCommand(error, false, true, false,
                    ocArgs);
            try (final InputStream is = process.getInputStream()) {
                consumer.consume(is);
            }
        } catch (IOException | InterruptedException ex) {
            throw new IllegalArgumentException(error, ex);
        }
    }

    /**
     * Execute command on local FS
     *
     * @param error error message on failure
     * @param wait wait for process completion
     * @param args command arguments
     */
    private static Process executeLocalCommand(String error, boolean wait, final boolean needOutput, final boolean needError, String... args) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(args);

        if (!needOutput) {
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        }
        if (!needError) {
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        }

        log.debug("executing local command: {}", String.join(" ", args));

        final Process process = pb.start();
        if (wait) {
            if (process.waitFor() != 0) {
                // Lets log as much information as we have
                StringWriter sw = new StringWriter();
                try (final InputStream is = process.getInputStream();) {
                    org.apache.commons.io.IOUtils.copy(is, sw, StandardCharsets.UTF_8);
                }
                try (final InputStream is = process.getErrorStream();) {
                    org.apache.commons.io.IOUtils.copy(is, sw, StandardCharsets.UTF_8);
                }
                log.error(sw.toString());
                // And throw exception
                throw new IllegalStateException(error);
            }
        }
        return process;
    }

    public static Process executeLocalCommand(String error, String... args) throws IOException, InterruptedException {
        return executeLocalCommand(error, true, false, false, args);
    }

    private static void login() throws IOException, InterruptedException {
        String masterIp = OpenShiftConfig.url();
        log.debug("Master IP: {}", masterIp);
        loginStatic(masterIp, OpenShiftConfig.masterUsername(), OpenShiftConfig.masterPassword(), OpenShiftConfig.masterToken());
    }

    public void login(String masterUrl, String masterUserName, String masterPassword, String masterToken) throws IOException, InterruptedException {
        loginStatic(masterUrl, masterUserName, masterPassword, masterToken);
    }

    public void loginDefault() throws IOException, InterruptedException {
        login();
    }

    public void loginAdmin() throws IOException, InterruptedException {
        login(OpenShiftConfig.url(),OpenShiftConfig.adminUsername(), OpenShiftConfig.adminPassword(), OpenShiftConfig.adminToken());
    }

    private static void loginStatic(String masterUrl, String masterUserName, String masterPassword, String masterToken) throws IOException, InterruptedException {

        if (masterToken != null) {
            executeLocalCommand("oc login failed!", ocBinaryPath, "login", masterUrl,
                    "--token=" + masterToken,
                    "--insecure-skip-tls-verify=true",
                    "--kubeconfig=" + CONFIG_FILE);
        } else {
            executeLocalCommand("oc login failed!", ocBinaryPath, "login", masterUrl,
                    "--username=" + masterUserName,
                    "--password=" + masterPassword,
                    "--insecure-skip-tls-verify=true",
                    "--kubeconfig=" + CONFIG_FILE);
        }
    }

    public Path getOcBinaryPath() {
        return Paths.get(ocBinaryPath);
    }

    public Path getOcConfigPath() {
        return Paths.get(CONFIG_FILE.getAbsolutePath());
    }

    @FunctionalInterface
    public interface CommandResultConsumer {
        void consume(InputStream istream) throws IOException;
    }
}
