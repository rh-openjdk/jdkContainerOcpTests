package com.redhat.qe.openjdk.util.deployment;

import com.redhat.qe.openjdk.util.AbstractServerDeployment;

public class WildFlySwarmDeployment extends AbstractServerDeployment {
	public WildFlySwarmDeployment(final String name, final String hostName, final String cluster) {
		this(name, hostName, cluster, null);
	}

	public WildFlySwarmDeployment(final String name, final String hostName, final String cluster,
                                  final String secureHostname) {
		super(name, hostName, cluster, secureHostname);
	}
}
