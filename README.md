# Red Hat OpenJDK OpenShift Container Platform(OCP) Test Suite
***
This test suite is designed to be executed against current versions of Red Hat's OpenJDK containers. 

| Operating System version | OpenJDK Version | Link to Container                                                                                                                    |
|--------------------------|-----------------|--------------------------------------------------------------------------------------------------------------------------------------|
| Rhel 7                   | 8               | [openjdk18-openshift](https://catalog.redhat.com/software/containers/redhat-openjdk-18/openjdk18-openshift/58ada5701fbe981673cd6b10) |
| Rhel 7                   | 11              | [openjdk-11-rhel7](https://catalog.redhat.com/software/containers/openjdk/openjdk-11-rhel7/5bf57185dd19c775cddc4ce5)                 |
| UBI 8                    | 8               | [openjdk-8](https://catalog.redhat.com/software/containers/ubi8/openjdk-8/5dd6a48dbed8bd164a09589a)                                  |
| UBI 8                    | 11              | [openjdk-11](https://catalog.redhat.com/software/containers/ubi8/openjdk-11/5dd6a4b45a13461646f677f4)                                |
| UBI 8                    | 17              | [openjdk-17](https://catalog.redhat.com/software/containers/ubi8/openjdk-17/618bdbf34ae3739687568813)                                |
| UBI 9                    | 11              | [openjdk-11](https://catalog.redhat.com/software/containers/ubi9/openjdk-11/61ee7bafed74b2ffb22b07ab)                                |
| UBI 9                    | 17              | [openjdk-17](https://catalog.redhat.com/software/containers/ubi9/openjdk-17/61ee7c26ed74b2ffb22b07f6)                                |
**Note**: `OpenJDK runtime images are not currently supported by this test suite.` 

***


This test suite is based on the XTF project [https://github.com/xtf-cz/xtf] that leverages JUnit 5, Fabric8 Kubernetes Client [https://github.com/fabric8io/kubernetes-client/] and other useful libraries to unify and simplify interactions with OpenShift.


****

## Test Execution

Use `mvn clean` to remove existing test artifacts from your sandbox.

To execute the UBI 8 or UBI 9 images you must create a (Limit Range) [https://docs.openshift.com/container-platform/4.13/nodes/clusters/nodes-cluster-limit-ranges.html]]
To execute the test suite by hand against a defined version of Red Hat's OpenJDK containers issue the following command.

* `MAVEN_HOME=/usr/bin/ mvn clean test -P 8 -P smoke -Dmaven.home=/usr/bin/`
* `MAVEN_HOME=/usr/bin/ mvn clean test -P 11 -P smoke -Dmaven.home=/usr/bin/`
* `MAVEN_HOME=/usr/bin/ mvn clean test -P 17 -P smoke -Dmaven.home=/usr/bin/`

Notice this will execute the test for OpenJDK version 8, 11, or 17. These are the only versions that are supported for testing. To simplify the test execution a `run.sh` has been added. This is the preferred approach to executing tests against the UBI 8 and UBI 9 images. 

For UBI 8 and UBI 9 images it is recommended to use the bash script for the execution of the testsuite. The bash script will create the OCP projects as defined in the `global-test.properties` and then create a limit-range resource as defined by the `limit_range.yaml` file.
```bash
bash run.sh --jdk-version=8
bash run.sh --jdk-version=11
bash run.sh --jdk-version=17
```

## Configuration
To configure the test suite for execution against the OpenJDK Image Under Test (IUT) you must have the following.
1. An execution node that can execute a Maven-based Java application.
2. A running instance of Red Hat's OpenShift Container Platform(OCP) with admin access.

Configure the `global-test.properties` to describe your environment as well as two OCP projects that the test suite runs against. 

```yaml
#mocked settings
xtf.openshift.namespace=<project_name>
xtf.bm.namespace=<build_project_name>
xtf.openshift.url=<result from 'oc whoami --show-server'>
xtf.openshift.token=<result from 'oc whoami -t'>
xtf.openshift.admin.token=<result from 'oc whoami -t'>
```

#### Token auth
``` yaml
#past example 
xtf.openshift.url=https://api.servername:6443
xtf.openshift.token=sha256~lfGBHTDiL1PxVZrM3wMvnU_bvgZyJhZLsb_iU_Zrhwk
xtf.openshift.admin.token=sha256~lfGBHTDiL1PxVZrM3wMvnU_bvgZyJhZLsb_iU_Zrhwk
xtf.openshift.namespace=alpha
xtf.bm.namespace=alpha-builds

```



