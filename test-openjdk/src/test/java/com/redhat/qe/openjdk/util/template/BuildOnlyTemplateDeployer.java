package com.redhat.qe.openjdk.util.template;

import com.redhat.qe.openjdk.OpenJDKTestParent;
import cz.xtf.core.event.helpers.EventHelper;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShiftWaiters;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.core.waiting.Waiters;
import cz.xtf.core.waiting.failfast.FailFastBuilder;
import cz.xtf.core.waiting.failfast.FailFastCheck;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.Template;
import lombok.extern.slf4j.Slf4j;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * This is a special variant of template controller handling templates without DeploymentConfig resources.
 * The other speciality is that it uses template's param APPLICATION_IMAGE as an application name.
 */
@Slf4j
public class BuildOnlyTemplateDeployer {
    private final OpenShift openshift = OpenShifts.master();
    private ZonedDateTime deployStart;

    static BuildOnlyTemplateDeployer fromTemplate(Template template, Map<String, String> params) {
        return new BuildOnlyTemplateDeployer(template, params);
    }

    private final String appName;
    private final KubernetesList kubernetesList;

    private BuildOnlyTemplateDeployer(Template template, Map<String, String> params) {
        template.setApiVersion("template.openshift.io/v1");

        synchronized (BuildOnlyTemplateDeployer.class) {
            if (openshift.getTemplate(template.getMetadata().getName()) == null) {
                openshift.createTemplate(template);
            }
        }

        this.kubernetesList = openshift.processTemplate(template.getMetadata().getName(), params);

        this.appName = params.get("APPLICATION_NAME");
    }

    public BuildOnlyTemplateDeployer deploy() {
        // it can happen that client call times out - in this case retry operation
        deployStart = EventHelper.timeOfLastEventBMOrTestNamespaceOrEpoch();
        for (int i = 0; i < 3; i++) {
            try {
                openshift.createResources(kubernetesList);
                break;
            } catch (Exception ex) {
                // in case that some resources from previous
                log.error("Exception was thrown when deploying template. Trying again(" + i + ").", ex);
                try {
                    openshift.deleteResources(kubernetesList);
                } catch (Exception ignore) {
                    // ignore
                }
                Waiters.sleep(TimeUnit.SECONDS, 2);
            }
        }

        Waiters.sleep(TimeUnit.SECONDS, 2);
        return this;
    }

    public BuildOnlyTemplateDeployer waitForBuildCompletion() {
        FailFastCheck failFast = FailFastBuilder.ofOpenShifts(openshift)
                .events()
                .after(deployStart)
                .ofMessages(OpenJDKTestParent.getFailFastEventMessages())
                .ofNames(appName.concat(".*"))
                .atLeastOneExists()
                .build();
        OpenShiftWaiters.get(openshift, failFast).isLatestBuildPresent(appName).waitFor();
        OpenShiftWaiters.get(openshift, failFast).hasBuildCompleted(appName).timeout(TimeUnit.MINUTES, 20).waitFor();
        return this;
    }

    public BuildOnlyTemplateDeployer postProcess(Consumer<KubernetesList> customization) {
        customization.accept(kubernetesList);
        return this;
    }

    public BuildOnlyTemplateDeployer postProcessEnvs(Consumer<Map<String, String>> postProcessEnvConsumer) {
        Map<String, String> postProcessEnvs = new HashMap<>();
        postProcessEnvConsumer.accept(postProcessEnvs);
        return this;
    }

    /**
     * Calls waitForBuildCompletion.
     *
     * @return self instance
     */
    public BuildOnlyTemplateDeployer waitTillBuildReady() throws TimeoutException, InterruptedException {
        if (kubernetesList.getItems().stream().anyMatch(x -> BuildConfig.class.isAssignableFrom(x.getClass()))) {
            waitForBuildCompletion();
        }
        return this;
    }
}
