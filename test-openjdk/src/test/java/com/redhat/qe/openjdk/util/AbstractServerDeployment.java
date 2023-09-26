package com.redhat.qe.openjdk.util;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import cz.xtf.builder.builders.RouteBuilder;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.openshift.api.model.DeploymentConfig;


public abstract class AbstractServerDeployment {
	private static final int DEFAULT_WAIT_TIMEOUT = 180_000;
	protected final String name;
	protected final String hostName;
	protected final String cluster;
	protected final String secureHostname;

	protected static final OpenShift openshift = OpenShifts.master();


	public AbstractServerDeployment(final String name, final String hostName, final String cluster) {
		this(name, hostName, cluster, null);
	}

	public AbstractServerDeployment(final String name, final String hostName, final String cluster,
			final String secureHostname) {
		this.name = name;
		this.hostName = hostName;
		this.cluster = cluster;
		this.secureHostname = secureHostname;
	}

	public String getHostName() {
		return this.hostName;
	}

	public Collection<Service> getServices() {
		return openshift
				.getServices()
				.stream()
				.filter(getServicePredicate())
				.collect(Collectors.toList());
	}

	public Collection<Pod> getPods() {
		return openshift.getLabeledPods("name", this.name);
	}

	public Pod getRandomPod() {
		return openshift.getAnyPod("name",this.name);
	}

	public ReplicationController getReplicationController() {
		final List<ReplicationController> rcs = openshift
				.replicationControllers().list().getItems()
				.stream()
				.filter(rc -> this.name.equals(rc.getSpec()
						.getSelector()
						.get("name")))
				.collect(Collectors.toList());
		if (rcs.size() == 0) {
			throw new IllegalStateException("No replication controller for name='" + this.name + "' found!");
		}
		if (rcs.size() > 1) {
			throw new IllegalStateException("More than 1 replication controller for name='" + this.name + "' found!");
		}
		return rcs.get(0);
	}

	public DeploymentConfig getDeploymentConfig() {
		return openshift.getDeploymentConfig(this.name);
	}

	public Predicate<Pod> getPodPredicate() {
		return p -> this.name.equals(p.getMetadata()
				.getLabels()
				.get("name"));
	}

	public Predicate<Service> getServicePredicate() {
		return s -> this.name.equals(s.getSpec()
				.getSelector()
				.get("name"));
	}

	// Convenience methods
	public void scale(final int replicas) {
		openshift.scale(this.name, replicas);
	}

	public void scaleAndWait(final int replicas) throws TimeoutException, InterruptedException {
		scaleAndWait(replicas, DEFAULT_WAIT_TIMEOUT);
	}

	public void scaleAndWait(final int replicas,
			final long timeoutInMillis) throws TimeoutException, InterruptedException {
		scale(replicas);

		if (replicas == 0) {
			openshift.waiters().areNoPodsPresent(this.name).waitFor();
		} else {
			openshift.waiters().areExactlyNPodsReady(replicas, this.name).waitFor();
		}
	}

	public void removeRoute() {
		openshift.routes().withName(this.name + "-route").delete();
	}

	public void redirectRouteToOtherDeployment(final AbstractServerDeployment other) {
		openshift.createRoute(new RouteBuilder(this.name + "-route").forService(other.name + "-service")
						.exposedAsHost(this.getHostName())
						.build());
	}

	public String getSecureHostname() {
		return this.secureHostname;
	}
}
