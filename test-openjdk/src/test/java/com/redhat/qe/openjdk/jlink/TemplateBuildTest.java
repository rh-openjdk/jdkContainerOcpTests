package com.redhat.qe.openjdk.jlink;

import com.redhat.qe.openjdk.OpenJDKTestConfig;
import com.redhat.qe.openjdk.OpenJDKTestParent;
import com.redhat.qe.openjdk.util.OpenShiftBinaryClient;
import com.redhat.qe.openjdk.util.template.BuildOnlyTemplateDeployer;
import com.redhat.qe.openjdk.util.template.XtfTemplate;
import cz.xtf.core.config.XTFConfig;
import cz.xtf.core.event.helpers.EventHelper;
import cz.xtf.core.http.Https;
import cz.xtf.core.namespace.NamespaceManager;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShiftWaiters;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.core.waiting.SimpleWaiter;
import cz.xtf.core.waiting.failfast.FailFastBuilder;
import cz.xtf.junit5.annotations.CleanBeforeEach;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.openshift.api.model.*;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@CleanBeforeEach
@Slf4j
public class TemplateBuildTest extends OpenJDKTestParent {
    private static final OpenShift openShift = OpenShifts.master();
    private static final OpenShiftBinaryClient oc = OpenShiftBinaryClient.getInstance();

    private static final String project = openShift.getNamespace();
    private static final String BUILD_TEMPLATE_URL = OpenJDKTestConfig.getJlinkTemplateUrl();
    private static final String BUILD_TEMPLATE_NAME ="jlink-app-template";

    private static final String APPNAME = "quarkus-3-test";
    private static final String JDK_VERSION = "21"; //The JLINK tech preview only supports JDK21
    private static final String IMAGE_BUILDER_URL = OpenJDKTestConfig.imageUrl();
    private static final String IMAGE_BUILDER_IMAGESTREAM = "openjdk-21-jlink-tech-preview";
    private static final String IMAGE_BUILDER_TAG = "latest";
    private static String HOSTNAME; // route to the app under test

    private OpenShiftWaiters testWaiters;

    private static final Map<String, String> DEFAULT_TEMPLATE_PARAMS = new HashMap<>();

    // Data structure to hold Objects of the custom resources built by the template definition.
    private static List<Object> templateObjects = new ArrayList<Object>();

    static {
        DEFAULT_TEMPLATE_PARAMS.put("APPNAME", APPNAME);
        DEFAULT_TEMPLATE_PARAMS.put("OPENJDK_IMAGE", IMAGE_BUILDER_IMAGESTREAM + ":" + IMAGE_BUILDER_TAG);
        DEFAULT_TEMPLATE_PARAMS.put("IMAGESTREAM_NAMESPACE", NamespaceManager.getNamespace());
        DEFAULT_TEMPLATE_PARAMS.put("CONTEXT_DIR", "quarkus-quickstarts/getting-started-3.9.2-uberjar");
        DEFAULT_TEMPLATE_PARAMS.put("APP_URI", "https://github.com/rh-openjdk/openjdk-container-test-applications");
        DEFAULT_TEMPLATE_PARAMS.put("REF", "master");
        DEFAULT_TEMPLATE_PARAMS.put("SERVICE_PORT", "8080");
        DEFAULT_TEMPLATE_PARAMS.put("TARGET_PORT", "8080");
        DEFAULT_TEMPLATE_PARAMS.put("BUILDER_IMAGE_TAG", IMAGE_BUILDER_TAG);
    }

    @BeforeEach
    public void prepareForTests() throws IOException, InterruptedException {
        oc.loginDefault();
        oc.project(NamespaceManager.getNamespace());

        //prerequisites - provided images must be different for the tests to work
        Assertions.assertThat(IMAGE_BUILDER_URL).isNotEmpty();

        createImageStream(IMAGE_BUILDER_URL, IMAGE_BUILDER_IMAGESTREAM, IMAGE_BUILDER_TAG);

        ensureBuildTemplateIsInstalled();
    }

    @Test
    public void validateRequiredParams() {
        SoftAssertions soft = new SoftAssertions();

        Map<String, Boolean> expectedParamsRequiredMap = new HashMap<>();
        expectedParamsRequiredMap.put("JDK_VERSION", true);
        expectedParamsRequiredMap.put("BUILDER_IMAGE_TAG", false);
        expectedParamsRequiredMap.put("APPNAME", true);
        expectedParamsRequiredMap.put("APP_URI", true);
        expectedParamsRequiredMap.put("REF", true);
        expectedParamsRequiredMap.put("CONTEXT_DIR", true);
        expectedParamsRequiredMap.put("GITHUB_WEBHOOK_SECRET", true);
        expectedParamsRequiredMap.put("TARGET_PORT", true);
        expectedParamsRequiredMap.put("SERVICE_PORT", true);

        Template buildTemplate = openShift.getTemplate(BUILD_TEMPLATE_NAME);
        List<Parameter> templateParameters = buildTemplate.getParameters();
        List<String> paramNames = templateParameters.stream().map(Parameter::getName).collect(Collectors.toList());

        soft.assertThat(paramNames).as("The actual parameters differ from the expected set")
                .containsExactlyInAnyOrderElementsOf(expectedParamsRequiredMap.keySet());
        soft.assertThat(templateParameters).as("The actual set of required parameters differs from the expected set")
                .allMatch(p -> (p.getRequired() == null && !expectedParamsRequiredMap.get(p.getName()))
                        || expectedParamsRequiredMap.get(p.getName()) == p.getRequired());

        soft.assertAll();
    }

    @Test
    public void validateBuildFromTemplate() {
        Map<String, String> params = new HashMap<>(DEFAULT_TEMPLATE_PARAMS);
        useTemplateToCreateResources(params);

        verifyExpectedResourcesCreated();
        // Validate the app is up
        assertTrue(Https.doesUrlReturnCode("http://" + HOSTNAME, 200).waitFor());
        System.out.println("Statement.");

    }
    @BeforeEach
    public void setupFailFast() {
        testWaiters = OpenShiftWaiters.get(OpenShifts.master(), FailFastBuilder
                .ofTestAndBuildNamespace()
                .events()
                .ofNames(APPNAME + ".*")
                .after(EventHelper.timeOfLastEventBMOrTestNamespaceOrEpoch())
                .ofMessages(getFailFastEventMessages())
                .atLeastOneExists()
                .build()
        );
    }

    private void ensureBuildTemplateIsInstalled() {
        oc.executeCommand("Failed to install jlink-app-template template!",
                "replace",
                "--namespace", NamespaceManager.getNamespace(),
                "--force",
                "-f", BUILD_TEMPLATE_URL);
    }

    private void useTemplateToCreateResources(Map<String, String> params) {
        Template buildTemplate = openShift.getTemplate(BUILD_TEMPLATE_NAME);
        XtfTemplate xtfBuildTemplate = new XtfTemplate(buildTemplate);
        BuildOnlyTemplateDeployer buildTemplateDeployer = xtfBuildTemplate.getBuildOnlyDeployer(params);
        buildTemplateDeployer.deploy();
    }

    private String processTemplateObjectName (String nameRaw) {
        nameRaw = nameRaw.replace("${APPNAME}", APPNAME); //Replace the "${APPNAME} with the variable APPNAME
        nameRaw = nameRaw.replace("${JDK_VERSION}", JDK_VERSION); //Replace the "${JDK_VERSION} with the variable JDK_VERSION
        return nameRaw;
    }

    private void verifyExpectedResourcesCreated() {
        // we are waiting for the final outcome of the template to appear
        // This checks if the main Image Stream is present and ready with the 'latest' image.
        // There are five designed ImageStreams so we want to make sure these are all ready before checking the
        // resources
        new SimpleWaiter(() -> openShift.getImageStreamTag(IMAGE_BUILDER_IMAGESTREAM, "latest") != null)
                .reason("Waiting for application ImagestreamTag (" + IMAGE_BUILDER_IMAGESTREAM + ":latest) to be created.")
                .timeout(TimeUnit.MINUTES, Long.valueOf(XTFConfig.get("xtf.long.action.timeout","20"))).interval(TimeUnit.SECONDS, 10)
                .waitFor();
        Template buildTemplate = openShift.getTemplate(BUILD_TEMPLATE_NAME);
        List <HasMetadata> templateObjects = buildTemplate.getObjects();
        List <Object> resourcesToCheck = new ArrayList<Object>();
        for (int i = 0; i < templateObjects.size(); i++) {
            System.out.println(templateObjects.get(i));
            Object tmpVal = templateObjects.get(i).getClass();
            // Iterate over the data structure and pull out the different objects.
            // The are ImageStream, BuildConfig, Deployment, Service and Route
            //openShift.getRoute("quarkus-3-test-jlinked-app-route").getSpec().getHost()
            // templateObjects
            String name;
            String processedName;
            if (templateObjects.get(i).getClass() == ImageStream.class) {
                System.out.println("We have an ImageStream ");
                name = templateObjects.get(i).getMetadata().getName();
                processedName = processTemplateObjectName(name);
                new SimpleWaiter(() -> openShift.getImageStreamTag(processedName, "latest") != null)
                        .reason("Waiting for application ImagestreamTag (" + processedName + ":latest) to be created.")
                        .timeout(TimeUnit.MINUTES, Long.valueOf(XTFConfig.get("xtf.long.action.timeout","20"))).interval(TimeUnit.SECONDS, 10)
                        .waitFor();
                ImageStream tmpStream = openShift.getImageStream(processedName);
                resourcesToCheck.add(i, tmpStream);
                ImageStreamTag imageStreamTagBuildArtifacts = openShift.getImageStreamTag(processedName, "latest");
                assertNotNull(imageStreamTagBuildArtifacts, "The expected ImagestreamTag (" + processedName + ") is not created!");
            } else if (templateObjects.get(i).getClass() == BuildConfig.class) {
                System.out.println("We have an BuildConfig ");
                name = templateObjects.get(i).getMetadata().getName();
                processedName = processTemplateObjectName(name);
                BuildConfig tmpBC = openShift.getBuildConfig(processedName);
                resourcesToCheck.add(i, tmpBC);
                assertNotNull(tmpBC, "The expected BuildConfig (" + processedName + ") is not created!");
            } else if (templateObjects.get(i).getClass() == Deployment.class) {
                System.out.println("We have an Deployment ");
                name = templateObjects.get(i).getMetadata().getName();
                processedName = processTemplateObjectName(name);
                // Cannot get a Deployment object because for some reason the OpenShift object does not support this.
                resourcesToCheck.add(i, processedName);
            } else if (templateObjects.get(i).getClass() == Service.class) {
                System.out.println("We have a Service ");
                name = templateObjects.get(i).getMetadata().getName();
                processedName = processTemplateObjectName(name);
                Service tmpService = openShift.getService(processedName);
                resourcesToCheck.add(i, tmpService);
                assertNotNull(tmpService, "The expected Service (" + processedName + ") is not created!");
            } else if (templateObjects.get(i).getClass() == Route.class) {
                System.out.println("We have a Route ");
                name = templateObjects.get(i).getMetadata().getName();
                processedName = processTemplateObjectName(name);
                Route tmpRoute = openShift.getRoute(processedName);
                resourcesToCheck.add(i, tmpRoute);
                assertNotNull(tmpRoute, "The expected Route (" + processedName + ") is not created!");
                HOSTNAME = tmpRoute.getSpec().getHost();
            }
        }
// Now resourcesToCheck and templateObjects should be the same size.
        assertEquals(templateObjects.size(), resourcesToCheck.size());
    }

}
