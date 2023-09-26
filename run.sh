#!/bin/bash

###############################################################################
##  run.sh
##  Entry point for running the Red Hat OpenJDK Container smoketest
##  against a Red Hat OpenShift Container Platform
##
###############################################################################

set -ex
set -o pipefail
## resolve folder of this script, following all symlinks,
## http://stackoverflow.com/questions/59895/can-a-bash-script-tell-what-directory-its-stored-in
SCRIPT_SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SCRIPT_SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  SCRIPT_DIR="$( cd -P "$( dirname "$SCRIPT_SOURCE" )" && pwd )"
  SCRIPT_SOURCE="$(readlink "$SCRIPT_SOURCE")"
  # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
  [[ $SCRIPT_SOURCE != /* ]] && SCRIPT_SOURCE="$SCRIPT_DIR/$SCRIPT_SOURCE"
done
readonly SCRIPT_DIR="$( cd -P "$( dirname "$SCRIPT_SOURCE" )" && pwd )"

# Help function
function print_help() {
  echo "Name: run.sh
       Description: Red Hat OpenJDK testsuite for Red Hat OpenShift Container Platform.
       Command-line arguements:
           --jdk-version
               Red Hat OpenJDK version under test. The suite supports versions 8, 11, 17.
       Example: run.sh --jdk-version=8"
  exit 1
}

# OPENJDK_VERSION=<>#Version of OpenJDK under test
# The commandline should be  `run.sh --jdk-version=8`
for a in "$@"
do
  case $a in
      --jdk-version=*)
        OPENJDK_VERSION="${a#*=}"
      ;;
      *)
         echo "Unrecognized argument: '$a'" >&2
         print_help >&2
      ;;
  esac
done

# Check the OpenJDK Version to make sure the tests supports it.
if [ $OPENJDK_VERSION -eq 8 ] || [ $OPENJDK_VERSION -eq 11 ] || [ $OPENJDK_VERSION -eq 17 ] ; then
  echo "OpenJDK version under test is: $OPENJDK_VERSION"
  else
    echo "Unsupported OpenJDK version detected."
    echo "OpenJDK version is: $OPENJDK_VERSION"
    exit 1
fi

###############################################################################
## Create the namespace (project)
## In OpenShift a project is similar to a k8s namespace. For the point of
## this test framework's dependencies we will use namespace.
echo "Print the contents of the limit_range yaml file."
cat limit_range.yaml

NAMESPACE=`cat global-test.properties | grep -Po 'xtf.openshift.namespace=\K[^ ]+'`
BUILD_NAMESPACE=`cat global-test.properties | grep -Po 'xtf.bm.namespace=\K[^ ]+'`
LIMIT_RANGE_NAME=`cat limit_range.yaml | grep -Po 'name:\s\K[^ ]+'| tr -d '"'`

echo "Namespace check $NAMESPACE And $BUILD_NAMESPACE"

for PROJECTNAME in "$NAMESPACE" "$BUILD_NAMESPACE"
do
   echo "Projectname: $PROJECTNAME"
   set +e
   oc get project "$PROJECTNAME" > /dev/null 2>&1
   if [ "$?" -eq 0 ] ; then
     echo "Project: $PROJECTNAME already exists proceed."
     # Check that the limitranges resource exists.
     oc get limitranges -A | grep -P "${PROJECTNAME}\s*${LIMIT_RANGE_NAME}"
     if [ "$?" -eq 0 ] ; then
       echo " "
       echo "Namespace $PROJECTNAME already has a limitranges applied named $LIMIT_RANGE_NAME"
       echo " "
     else
       oc create -f limit_range.yaml -n $PROJECTNAME
     fi
   else
     # Create the project
     oc new-project $PROJECTNAME
     # Create the limitranges resource for this project
     oc create -f limit_range.yaml -n $PROJECTNAME
   fi
   set -e
done

echo "Now output the projects under test and any other resource."
oc get limitranges -A

## Run the testsuite

MAVEN_HOME=/usr/share/maven mvn clean test -P $OPENJDK_VERSION -P smoke -Dmaven.home=/usr/share/maven

##############################################################################

#OPENJDK_VERSION
# MAVEN_HOME=/usr/share/maven mvn clean test -P 8 -P smoke -Dmaven.home=/usr/share/maven
# MAVEN_HOME=/usr/share/maven mvn clean test -P 11 -P smoke -Dmaven.home=/usr/share/maven
# MAVEN_HOME=/usr/share/maven mvn clean test -P 17 -P smoke -Dmaven.home=/usr/share/maven
