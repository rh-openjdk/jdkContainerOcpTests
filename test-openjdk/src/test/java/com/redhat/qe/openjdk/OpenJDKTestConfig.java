package com.redhat.qe.openjdk;

import org.apache.commons.lang3.StringUtils;

import cz.xtf.core.config.XTFConfig;
import cz.xtf.core.image.Image;
import cz.xtf.core.image.Product;
import cz.xtf.core.image.Products;

public class OpenJDKTestConfig {


	public static Product product() {
		return Products.resolve("openjdk");
	}

	public static Image image() {
		return product().image();
	}

	public static String getProductVersion() {
		return product().version();
	}

	public static String getImageUrl(){
		return image().getUrl();
	}

	public static String imageUrl() {
		return image().getUrl();
	}

	public static String getImageRepo() {
		return image().getRepo();
	}

	public static boolean isOpenJDK8() {
		return image().getRepo().contains("openjdk-8");
	}

	public static boolean isOpenJDK11() {
		return image().getRepo().contains("openjdk-11");
	}
	public static boolean isOpenJDK17() {
		return image().getRepo().contains("openjdk-17");
	}

	public static boolean isOpenJDK21() {
		return image().getRepo().contains("openjdk-21");
	}

	// Need to spell out the Rhel 7-based builds because they do not follow the newer
	// naming conventions.
	public static boolean isOpenJDK8Rhel7() {
		return image().getRepo().contains("openjdk18-openshift");
	}
	public static boolean isOpenJDK11Rhel7() {
		return image().getRepo().contains("openjdk-11-rhel7");
	}
	public static boolean isRHEL7() {
		boolean check = false;
		if (getImageUrl().contains("openjdk-11-rhel7")) {
			check = true;
		}
		if (getImageUrl().contains("openjdk18-openshift")) {
			check = true;
		}
		return check;
	}

	public static boolean isRHEL8() {
		return getImageUrl().contains("ubi8");
	}

	public static boolean isRHEL9() {
		return checkImageOsVersion("9");
	}

	private static boolean checkImageOsVersion(String target) {
		String ubiVer = "ubi";
		String elsVer = "rhel";
		boolean osCheck = false;
		if (getImageUrl().contains(ubiVer.concat(target)) || getImageUrl().contains(elsVer.concat(target))) {
			osCheck = true;
		}
		else {
			osCheck = false;
		}
		return osCheck;
	}

	public static boolean isMavenProxyEnabled() {
		return mavenProxyUrl() != null;
	}

	public static String mavenProxyUrl() {
		final String url = XTFConfig.get("xtf.maven.proxy.url");
		if (!StringUtils.isBlank(url)) {
			return url.trim();
		}
		return null;
	}

}
