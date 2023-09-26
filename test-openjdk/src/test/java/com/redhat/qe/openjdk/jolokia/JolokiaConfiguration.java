package com.redhat.qe.openjdk.jolokia;

import lombok.Getter;

@Getter
public class JolokiaConfiguration {
	private final boolean enabled;
	private final String configFile;
	private final boolean randomPassword;
	private final String user;
	private final String password;
	private final String host;
	private final int port;
	private final boolean discoveryEnabled;
	private final String https;
	private final boolean authOpenShift;
	private final String additionalOpts;
	private final String portName;

	public JolokiaConfiguration(final Builder builder) {
		this.enabled = builder.enabled;
		this.configFile = builder.configFile;
		this.randomPassword = builder.randomPassword;
		this.user = builder.user;
		this.password = builder.password;
		this.host = builder.host;
		this.port = builder.port;
		this.discoveryEnabled = builder.discoveryEnabled;
		this.https = builder.https;
		this.authOpenShift = builder.authOpenShift;
		this.additionalOpts = builder.additionalOpts;
		this.portName = builder.portName;
	}

	public static class Builder {
		private boolean enabled = true;
		private String configFile = "";
		private boolean randomPassword = true;
		private String user = "jolokia";
		private String password = "";
		private String host = "*";
		private int port = 8778;
		private boolean discoveryEnabled = false;
		private String https = "";
		private boolean authOpenShift = true;
		private String additionalOpts = "";
		private String portName = "jolokia";

		public Builder enabled(final boolean enabled) {
			this.enabled = enabled;
			return this;
		}

		public Builder configFile(final String configFile) {
			this.configFile = configFile;
			return this;
		}

		public Builder randomPassword(final boolean randomPassword) {
			this.randomPassword = randomPassword;
			return this;
		}

		public Builder user(final String user) {
			this.user = user;
			return this;
		}

		public Builder password(final String password) {
			this.password = password;
			return this;
		}

		public Builder host(final String host) {
			this.host = host;
			return this;
		}

		public Builder port(final int port) {
			this.port = port;
			return this;
		}

		public Builder discoveryEnabled(final boolean discoveryEnabled) {
			this.discoveryEnabled = discoveryEnabled;
			return this;
		}

		public Builder https(final String https) {
			this.https = https;
			return this;
		}

		public Builder authOpenShift(final boolean authOpenShift) {
			this.authOpenShift = authOpenShift;
			return this;
		}

		public Builder additionalOpts(final String additionalOpts) {
			this.additionalOpts = additionalOpts;
			return this;
		}

		public Builder portName(final String portName) {
			this.portName = portName;
			return this;
		}

		public JolokiaConfiguration build() {
			return new JolokiaConfiguration(this);
		}
	}
}
