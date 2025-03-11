package com.redhat.qe.openjdk.util.template;

import com.redhat.qe.openjdk.OpenJDKTestParent;
import cz.xtf.core.config.XTFConfig;
import cz.xtf.core.event.helpers.EventHelper;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShiftWaiters;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.core.waiting.Waiters;
import cz.xtf.core.waiting.failfast.FailFastBuilder;
import cz.xtf.core.waiting.failfast.FailFastCheck;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.Template;
import lombok.extern.slf4j.Slf4j;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@Slf4j
public class TemplateDeployer {
    private final OpenShift openshift = OpenShifts.master();
    private ZonedDateTime deployStart;

    static TemplateDeployer fromTemplate(Template template, Map<String, String> params) {
        return new TemplateDeployer(template, params);
    }

    private final String appName;
    private final int deploymentConfigsCount;
    private final KubernetesList kubernetesList;

    private TemplateDeployer(Template template, Map<String, String> params) {
        template.setApiVersion("template.openshift.io/v1");

        synchronized (TemplateDeployer.class) {
            if (openshift.getTemplate(template.getMetadata().getName()) == null) {
                openshift.createTemplate(template);
            }
        }

        this.kubernetesList = openshift.processTemplate(template.getMetadata().getName(), params);

        this.appName = params.get("APPLICATION_NAME");
        this.deploymentConfigsCount = (int) kubernetesList.getItems().stream().filter(item -> item instanceof DeploymentConfig).count();
    }

    public TemplateDeployer deploy() {
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

    public TemplateDeployer waitForBuildCompletion() {
        FailFastCheck failFast = FailFastBuilder.ofOpenShifts(openshift)
                .events()
                .after(deployStart)
                .ofMessages(OpenJDKTestParent.getFailFastEventMessages())
                .ofNames(appName.concat(".*"))
                .atLeastOneExists()
                .build();
        OpenShiftWaiters.get(openshift, failFast).isLatestBuildPresent(appName).waitFor();
        OpenShiftWaiters.get(openshift, failFast).hasBuildCompleted(appName).timeout(TimeUnit.MINUTES, Long.valueOf(XTFConfig.get("xtf.long.action.timeout","20"))).waitFor();
        return this;
    }

    public TemplateDeployer postProcess(Consumer<KubernetesList> customization) {
        customization.accept(kubernetesList);
        return this;
    }

    public TemplateDeployer postProcessEnvs(Consumer<Map<String, String>> postProcessEnvConsumer) {
        Map<String, String> postProcessEnvs = new HashMap<>();
        postProcessEnvConsumer.accept(postProcessEnvs);

        this.postProcess(Customizations.injectEnvsToDeploymentConfig(postProcessEnvs));

        return this;
    }

    /**
     * Waits for exactly n pods ready with application=${APPLICATION_NAME}
     * where n is number of deployment configs in template.
     *
     * @return self instance
     */
    public TemplateDeployer waitForReadyDeployments() throws TimeoutException, InterruptedException {

        openshift.waiters().areExactlyNPodsReady(deploymentConfigsCount, "application", appName).waitFor();
        return this;
    }

    /**
     * Calls waitForBuildCompletion followed by waitForReadyDeployments.
     *
     * @return self instance
     */
    public TemplateDeployer waitTillReady() throws TimeoutException, InterruptedException {
        if (kubernetesList.getItems().stream().filter(x -> BuildConfig.class.isAssignableFrom(x.getClass())).count() > 0) {
            waitForBuildCompletion();
        }
        waitForReadyDeployments();
        return this;
    }
}
