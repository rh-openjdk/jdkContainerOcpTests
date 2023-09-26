package com.redhat.qe.openjdk.s2i.springboot;

import com.redhat.qe.openjdk.s2i.AbstractBuildTest;
import com.redhat.qe.openjdk.util.annotation.MultiArchProfile;
import com.redhat.qe.openjdk.util.annotation.SmokeTest;
import org.junit.jupiter.api.BeforeAll;

@SmokeTest
@MultiArchProfile
public class SpringBootBuildTest extends AbstractBuildTest {

	@BeforeAll
	public static void init() {
		appName = "sti";
		appModule = "springboot";
	}
	
}
