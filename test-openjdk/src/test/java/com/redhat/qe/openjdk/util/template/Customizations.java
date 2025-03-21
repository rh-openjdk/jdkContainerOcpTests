package com.redhat.qe.openjdk.util.template;


import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.openshift.api.model.BinaryBuildSourceBuilder;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.ImageSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Customizations {

    /**
     * Injects envs to deployment config. Expects that only one deployment config is present between resources.
     *
     * @param envs envs to be injected
     * @return modifier for KubernetesList
     */
    public static Consumer<KubernetesList> injectEnvsToDeploymentConfig(Map<String, String> envs) {
        return x -> {
            DeploymentConfig dc = getExactlyOneOf(DeploymentConfig.class, x);
            List<EnvVar> envVars = dc.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
            List<EnvVar> added = envs.entrySet().stream().map(entry -> new EnvVar(entry.getKey(), entry.getValue(), null)).collect(Collectors.toList());

            envVars.addAll(added);
        };
    }

    /**
     * Injects envs to deployment config with provided name.
     * @param envs envs to be injected
     * @param dcName deploymentConfig name to inject envs for
     * @return modifier for KubernetesList
     */
    public static Consumer<KubernetesList> injectEnvsToDeploymentConfig(Map<String, String> envs, String dcName) {
        return x -> {
            DeploymentConfig dc = getOneOfOrTheOneWithName(DeploymentConfig.class, dcName, x);
            List<EnvVar> envVars = dc.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
            List<EnvVar> added = envs.entrySet().stream().map(entry -> new EnvVar(entry.getKey(), entry.getValue(), null)).collect(Collectors.toList());

            envVars.addAll(added);
        };
    }

    /**
     * Injects envs to build config with name .*-build-artifacts.
     *
     * @param envs envs to be injected
     * @return modifier for KubernetesList
     */
    public static Consumer<KubernetesList> injectEnvsToBuildArtifactsBuildConfig(Map<String, String> envs) {
        Pattern pattern = Pattern.compile("^.*-build-artifacts$");

        return list -> {
            List<BuildConfig> bcs = getAllOfWithName(BuildConfig.class, pattern, list);
            if (bcs.size() > 0) {
                bcs.stream().map((bc) -> bc.getSpec().getStrategy().getSourceStrategy().getEnv()).forEach(
                        (bcEnvs) -> bcEnvs.addAll(envs.entrySet().stream().map(entry -> new EnvVar(entry.getKey(), entry.getValue(), null))
                                .collect(Collectors.toList())));
            }
        };
    }


    /**
     * Modifies build config to use specific image instead image streams. Expects only one buildconfig is present between resources.
     *
     * @param image to be used withing build config.
     * @return modifier for KubernetesList
     */
    public static Consumer<KubernetesList> useSpecificImageWithinBuild(String image) {
        return processedResources -> {
            BuildConfig buildConfig = getExactlyOneOf(BuildConfig.class, processedResources);

            buildConfig.getSpec().getStrategy().getSourceStrategy().getFrom().setKind("DockerImage");
            buildConfig.getSpec().getStrategy().getSourceStrategy().getFrom().setNamespace(null);
            buildConfig.getSpec().getStrategy().getSourceStrategy().getFrom().setName(image);
        };
    }

    public static Consumer<KubernetesList> useBinaryBuild() {
        return processedResources -> {
            BuildConfig buildConfig = getExactlyOneOf(BuildConfig.class, processedResources);
            buildConfig.getSpec().getSource().setBinary(new BinaryBuildSourceBuilder().build());

            // remove the trigger if present
            buildConfig.getSpec().getTriggers().clear();
        };
    }

    /**
     * Modifies client source image to be used withing build config. Expects only one buildconfig is present between resources.
     *
     * @param clientImage to be used with source image build config.
     * @return modifier for KubernetesList
     */
    public static Consumer<KubernetesList> useSpecificClientImage(String clientImage) {
        return processedResources -> {
            BuildConfig buildConfig = getExactlyOneOf(BuildConfig.class, processedResources);
            assertEquals(1, buildConfig.getSpec().getSource().getImages().size());

            ImageSource imageSource = buildConfig.getSpec().getSource().getImages().get(0);
            imageSource.getFrom().setKind("DockerImage");
            imageSource.getFrom().setNamespace(null);
            imageSource.getFrom().setName(clientImage);
        };
    }

    /**
     * Modifies deployment to use specific image within it.
     *
     * @param appName deployment name to be modified
     * @param image to be used for this deployment
     * @return modifier for KubernetesList
     */
    public static Consumer<KubernetesList> useSpecificImageWithinDeployment(String appName, String image) {
        Pattern pattern = Pattern.compile(String.format("^%s$", appName));
        return Customizations.useSpecificImageWithinDeployment(pattern, image);
    }

    /**
     * Modifies deployment to use specific image within it.
     *
     * @param appName deployment name to be modified
     * @param image to be used for this deployment
     * @return modifier for KubernetesList
     */
    public static Consumer<KubernetesList> useSpecificImageWithinDeployment(Pattern appName, String image) {
        return processedResources -> {
            DeploymentConfig dc = getOneOfOrTheOneWithName(DeploymentConfig.class, appName, processedResources);
            dc.getSpec().getTriggers().removeIf(trigger -> "ImageChange".equals(trigger.getType()));
            dc.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
        };
    }

    /**
     * Injects given memory request in containers into deployment config. Expects that only one deployment config is present between resources.
     *
     * @param appName deployment name to be modified
     * @param memoryRequest sets requested memory for the started container/pod, for example "400Mi", "1Gi",...
     * @return modifier for KubernetesList
     */
    public static Consumer<KubernetesList> injectMemoryRequestForContainerWithinDeployment(String appName, String memoryRequest) {
        return processedResources -> {
            DeploymentConfig dc = getOneOfOrTheOneWithName(DeploymentConfig.class, appName, processedResources);
            dc.getSpec().getTemplate().getSpec().getContainers().get(0).getResources().setRequests(
                    new HashMap<String, Quantity>() {{
                        put("memory", Quantity.parse(memoryRequest));
                    }});
        };
    }

    public static Consumer<KubernetesList> useRollingStrategy(String appName) {
        return processedResources -> {
            DeploymentConfig dc = getOneOfOrTheOneWithName(DeploymentConfig.class, appName, processedResources);
            dc.getSpec().getStrategy().setType("Rolling");
        };
    }

    protected static <X> X getExactlyOneOf(Class<X> klass, KubernetesList list) {
        List<X> dcs = list.getItems().stream().filter(res -> res.getClass().isAssignableFrom(klass)).map(res -> (X) res).collect(Collectors.toList());

        if (dcs.size() != 1) {
            throw new RuntimeException("Expecting exactly one " + klass + " in template, has " + dcs.size());
        }

        return dcs.get(0);
    }

    protected static <X extends HasMetadata> X getOneOfOrTheOneWithName(Class<X> klass, String name, KubernetesList list) {
        Pattern patter = Pattern.compile(String.format("^%s$", name));
        return getOneOfOrTheOneWithName(klass, patter, list);
    }

    protected static <X extends HasMetadata> X getOneOfOrTheOneWithName(Class<X> klass, Pattern name, KubernetesList list) {
        final List<X> dcs = list.getItems().stream().filter(res -> res.getClass().isAssignableFrom(klass)).map(res -> (X) res).collect(Collectors.toList());

        if (dcs.size() == 1) {
            return dcs.get(0);
        } else {
            final List<X> dcsWithNameMatching = dcs.stream().filter(res -> name.matcher(res.getMetadata().getName()).matches()).collect(Collectors.toList());

            if (dcsWithNameMatching.size() != 1) {
                throw new RuntimeException("Expecting exactly one " + klass + " in template, has " + dcs.size());
            }

            return dcsWithNameMatching.get(0);
        }
    }

    protected static <X extends HasMetadata> List<X> getAllOfWithName(Class<X> klass, Pattern name, KubernetesList list) {
        final List<X> dcs = list.getItems().stream().filter(res -> res.getClass().isAssignableFrom(klass)).map(res -> (X) res).collect(Collectors.toList());
        return dcs.stream().filter(res -> name.matcher(res.getMetadata().getName()).matches()).collect(Collectors.toList());
    }

    protected static <X> Optional<X> getAtMostOneOf(Class<X> klass, KubernetesList list) {
        List<X> dcs = list.getItems().stream().filter(res -> res.getClass().isAssignableFrom(klass)).map(res -> (X) res).collect(Collectors.toList());

        if (dcs.size() > 1) {
            throw new RuntimeException("Expecting at most one " + klass + " in template, has " + dcs.size());
        } else if (dcs.size() == 1) {
            return Optional.of(dcs.get(0));
        } else {
            return Optional.empty();
        }
    }

    protected Customizations() {}
}