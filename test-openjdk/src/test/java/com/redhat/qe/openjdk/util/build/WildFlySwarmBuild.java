package com.redhat.qe.openjdk.util.build;

import java.util.Collections;

import cz.xtf.core.bm.ManagedBuild;
import cz.xtf.junit5.interfaces.BuildDefinition;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum WildFlySwarmBuild implements BuildDefinition {

	SSL(new WildFlySwarmBuildDefinition("ssl", "wfs-ssl", Collections.emptyMap())),
	HELLO_WORLD(new WildFlySwarmBuildDefinition("hello-world"));

	@Getter
	private final BuildDefinition buildDefinition;

	@Override
	public ManagedBuild getManagedBuild() {
		return this.buildDefinition.getManagedBuild();
	}
}
