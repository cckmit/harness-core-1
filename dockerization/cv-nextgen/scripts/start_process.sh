#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

mkdir -p /opt/harness/logs
touch /opt/harness/logs/cv-nextgen.log

if [[ -v "{hostname}" ]]; then
   export HOSTNAME=$(hostname)
fi

if [[ -z "$MEMORY" ]]; then
   export MEMORY=2048
fi

echo "Using memory " $MEMORY

if [[ -z "$COMMAND" ]]; then
   export COMMAND=server
fi


if [[ -z "$CAPSULE_JAR" ]]; then
   export CAPSULE_JAR=/opt/harness/cv-nextgen-capsule.jar
fi

export JAVA_OPTS="-Xms${MEMORY}m -Xmx${MEMORY}m -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:mygclogfilename.gc -XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=40 -XX:MaxGCPauseMillis=500 -Dfile.encoding=UTF-8"

if [[ "${ENABLE_APPDYNAMICS}" == "true" ]]; then
    mkdir /opt/harness/AppServerAgent-20.8.0.30686 && unzip AppServerAgent-20.8.0.30686.zip -d /opt/harness/AppServerAgent-20.8.0.30686
    node_name="-Dappdynamics.agent.nodeName=$(hostname)"
    JAVA_OPTS=$JAVA_OPTS" -javaagent:/opt/harness/AppServerAgent-20.8.0.30686/javaagent.jar -Dappdynamics.jvm.shutdown.mark.node.as.historical=true"
    JAVA_OPTS="$JAVA_OPTS $node_name"
    echo "Using Appdynamics java agent"
fi

if [[ "${ENABLE_OPENTELEMETRY}" == "true" ]] ; then
    echo "OpenTelemetry is enabled"
    wget https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v1.13.0/opentelemetry-javaagent.jar -O /opt/harness/opentelemetry-javaagent.jar
    JAVA_OPTS=$JAVA_OPTS" -javaagent:/opt/harness/opentelemetry-javaagent.jar -Dotel.service.name=cv-nextgen"

    if [ -n "$OPENTELEMETRY_COLLECTOR_URL" ]; then
        JAVA_OPTS=$JAVA_OPTS" -Dotel.exporter.otlp.endpoint=$OPENTELEMETRY_COLLECTOR_URL "
    fi
    echo "Using OpenTelemetry Java Agent"
fi

JAVA_OPTS=$JAVA_OPTS" -Xbootclasspath/p:/opt/harness/alpn-boot-8.1.13.v20181017.jar"

if [[ "${DEPLOY_MODE}" == "KUBERNETES" ]] || [[ "${DEPLOY_MODE}" == "KUBERNETES_ONPREM" ]]; then
    java $JAVA_OPTS -jar $CAPSULE_JAR $COMMAND /opt/harness/cv-nextgen-config.yml
else
    java $JAVA_OPTS -jar $CAPSULE_JAR $COMMAND /opt/harness/cv-nextgen-config.yml > /opt/harness/logs/cv-nextgen.log 2>&1
fi
