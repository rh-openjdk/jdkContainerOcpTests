package com.redhat.qe.openjdk.util.build;



import cz.xtf.core.bm.BinaryBuild;
import cz.xtf.junit5.interfaces.BuildDefinition;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Radek Koubsky (rkoubsky@redhat.com)
 */
@AllArgsConstructor
public enum SpringBootBuild implements BuildDefinition {

	SSL(new SpringBootBuildDefinition("ssl")),

	NOHA_SERVLET_COUNTER(new SpringBootBuildDefinition("noha-servlet-counter"));


	@Getter
	private final SpringBootBuildDefinition buildDefinition;

	@Override
	public BinaryBuild getManagedBuild() {
		return buildDefinition.getManagedBuild();
	}

}