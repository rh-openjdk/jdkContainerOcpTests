package com.redhat.qe.openjdk.util.build;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import cz.xtf.junit5.annotations.UsesBuild;

/**
 * @author Radek Koubsky (rkoubsky@redhat.com)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@UsesBuild
public @interface UsesSpringBootBuilds {
	SpringBootBuild[] value();
}
