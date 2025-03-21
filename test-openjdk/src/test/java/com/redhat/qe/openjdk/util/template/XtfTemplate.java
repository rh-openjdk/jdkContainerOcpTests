package com.redhat.qe.openjdk.util.template;


import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import cz.xtf.core.image.Image;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildSource;
import io.fabric8.openshift.api.model.BuildStrategy;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentTriggerPolicy;
import io.fabric8.openshift.api.model.ImageSource;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.Parameter;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.Template;

public class XtfTemplate {

    // Static helper methods for mappers
    public static String getName(HasMetadata hasMetadata) {
        return hasMetadata.getMetadata().getName();
    }

    public static Map<String, String> getSpecTemplateLabels(DeploymentConfig deploymentConfig) {
        return deploymentConfig.getSpec().getTemplate().getMetadata().getLabels();
    }

    private final Template template;
    private final String path;

    public XtfTemplate(Template template) {
        this(template, null);
    }

    public XtfTemplate(Template template, String path) {
        this.template = template;
        this.path = path;
    }

    public BuildOnlyTemplateDeployer getBuildOnlyDeployer(Map<String, String> params) {
        return BuildOnlyTemplateDeployer.fromTemplate(template, params);
    }

    public TemplateDeployer getDeployer(Map<String, String> params) {
        return TemplateDeployer.fromTemplate(template, params);
    }

    public TemplateDeployer getDeployer(Consumer<Map<String, String>> paramsConsumer) {
        Map<String, String> params = new HashMap<>();
        paramsConsumer.accept(params);
        return TemplateDeployer.fromTemplate(template, params);
    }

    public <R> R parseTemplate(Function<Template, R> function) {
        return function.apply(template);
    }

    public String getFileName() {
        return Paths.get(path).getFileName().toString();
    }

    public String getFilePath() {
        return path;
    }

    public String getTemplateName() {
        return template.getMetadata().getName();
    }

    // Objects
    public List<HasMetadata> getObjects() {
        return template.getObjects();
    }

    public <T> List<T> onObjects(Function<HasMetadata, T> mapper) {
        return template.getObjects().stream().map(mapper).collect(Collectors.toList());
    }

    // Services
    public Service getService(String name) {
        return getServices().stream().filter(x -> x.getMetadata().getName().equals(name)).findFirst().get();
    }

    public List<Service> getServices() {
        return template.getObjects().stream().filter(o -> Service.class.isAssignableFrom(o.getClass())).map(o -> (Service) o).collect(Collectors.toList());
    }

    public <T> List<T> onServices(Function<Service, T> mapper) {
        return this.getServices().stream().map(mapper).collect(Collectors.toList());
    }

    // Routes
    public List<Route> getRoutes() {
        return template.getObjects().stream().filter(o -> Route.class.isAssignableFrom(o.getClass())).map(o -> (Route) o).collect(Collectors.toList());
    }

    public <T> List<T> onRoutes(Function<Route, T> mapper) {
        return this.getRoutes().stream().map(mapper).collect(Collectors.toList());
    }

    // DeploymentConfigs
    public DeploymentConfig getDeploymentConfig(String name) {
        return getDeploymentConfigs().stream().filter(x -> x.getMetadata().getName().equals(name)).findFirst().get();
    }

    public List<DeploymentConfig> getDeploymentConfigs() {
        return template.getObjects().stream().filter(o -> DeploymentConfig.class.isAssignableFrom(o.getClass())).map(o -> (DeploymentConfig) o).collect(Collectors.toList());
    }

    public List<StatefulSet> getStatefulSets() {
        return template.getObjects().stream().filter(o -> StatefulSet.class.isAssignableFrom(o.getClass())).map(o -> (StatefulSet) o).collect(Collectors.toList());
    }

    public <T> List<T> onDeploymentConfigs(Function<DeploymentConfig, T> mapper) {
        return this.getDeploymentConfigs().stream().map(mapper).collect(Collectors.toList());
    }

    public <T> List<T> onStatefulSets(Function<StatefulSet, T> mapper) {
        return this.getStatefulSets().stream().map(mapper).collect(Collectors.toList());
    }

    // BuildConfigs
    public List<BuildConfig> getBuildConfigs() {
        return template.getObjects().stream().filter(o -> BuildConfig.class.isAssignableFrom(o.getClass())).map(o -> (BuildConfig) o).collect(Collectors.toList());
    }

    public <T> List<T> onBuildConfigs(Function<BuildConfig, T> mapper) {
        return this.getBuildConfigs().stream().map(mapper).collect(Collectors.toList());
    }

    // ImageStreams
    public List<ImageStream> getImageStreams() {
        return template.getObjects().stream().filter(o -> ImageStream.class.isAssignableFrom(o.getClass())).map(o -> (ImageStream) o).collect(Collectors.toList());
    }

    public <T> List<T> onImageStreams(Function<ImageStream, T> mapper) {
        return this.getImageStreams().stream().map(mapper).collect(Collectors.toList());
    }

    // Secrets
    public List<Secret> getSecrets() {
        return template.getObjects().stream().filter(o -> Secret.class.isAssignableFrom(o.getClass())).map(o -> (Secret) o).collect(Collectors.toList());
    }

    // Parameters
    public List<Parameter> getParameters() {
        return template.getParameters();
    }

    public Map<String, String> getParametersAsMap() {
        Map<String, String> parameters = new HashMap<>();
        template.getParameters().forEach(param -> parameters.put(param.getName(), param.getValue()));
        return parameters;
    }

    public List<String> getParametersList() {
        return template.getParameters().stream().map(Parameter::getName).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return getTemplateName();
    }

    public Map<String, String> onAnnotations() {
        return template.getMetadata().getAnnotations();
    }

    /**
     * Retrieves required image name from template and creates image stream with the same name from TestConfiguration image.
     * Note: image stream will be in master namespace.
     *
     * @param imageUrl TestConfiguration image
     */
    public XtfTemplate createTemplateImageStream(String imageUrl) {
        String templateImageName = this.getBuildConfigs().get(0)
                .getSpec().getStrategy().getSourceStrategy().getFrom().getName();

        OpenShift openshift = OpenShifts.master();

//		// delete if exists - causing exception otherwise
        ImageStream imageStream = openshift.getImageStream(templateImageName.split(":")[0]);
        if (imageStream != null) openshift.deleteImageStream(imageStream);

        openshift.createImageStream(Image.from(imageUrl)
                .getImageStream(templateImageName.split(":")[0], templateImageName.split(":")[1]));

        return this;
    }

    public List<ObjectReference> getImageStreamsUsedByTemplate() {
        final Function<DeploymentTriggerPolicy, ObjectReference> policyToReference = t -> t.getImageChangeParams().getFrom();
        final Function<BuildStrategy, ObjectReference> strategyToReference = t -> t.getSourceStrategy().getFrom();

        final Predicate<DeploymentTriggerPolicy> triggerFilter = t -> t.getType().equals("ImageChange");
        final Predicate<BuildStrategy> strategyFilter = t -> t.getType().equals("Source");

        // Get all object references and filter those that reference self namespace and those that reference image stream not created by us
        final List<ObjectReference> refs = new ArrayList<>();

        // Deployment "ImageChange" triggers
        this.onDeploymentConfigs(x -> x.getSpec().getTriggers().get(0)).stream().filter(triggerFilter).map(policyToReference).forEach(refs::add);

        // Build configs "Source" strategies
        this.onBuildConfigs(x -> x.getSpec().getStrategy()).stream().filter(strategyFilter).map(strategyToReference).forEach(refs::add);

        // Build configs source images
        this.onBuildConfigs(x -> x.getSpec().getSource()).stream().filter(s -> s.getImages() != null).map(BuildSource::getImages).forEach(
                x -> x.stream().map(ImageSource::getFrom).forEach(refs::add)
        );

        // Build configs "ImageChange" triggers
        this.onBuildConfigs(x -> x.getSpec().getTriggers()).forEach(
                x -> x.stream().filter(o -> o.getImageChange() != null && o.getImageChange().getFrom() != null).map(o -> o.getImageChange().getFrom()).forEach(refs::add)
        );

        return refs;
    }
}
