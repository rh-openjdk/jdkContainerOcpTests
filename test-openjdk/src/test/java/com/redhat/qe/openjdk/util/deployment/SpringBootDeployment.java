package com.redhat.qe.openjdk.util.deployment;

import com.redhat.qe.openjdk.util.AbstractServerDeployment;

/**
 * @author Radek Koubsky (rkoubsky@redhat.com)
 */
public class SpringBootDeployment extends AbstractServerDeployment {

	public SpringBootDeployment(final String name, final String hostName, final String cluster) {
		this(name, hostName, cluster, null);
	}

	public SpringBootDeployment(final String name, final String hostName, final String cluster,
			final String secureHostname) {
		super(name, hostName, cluster, secureHostname);

	}
}
