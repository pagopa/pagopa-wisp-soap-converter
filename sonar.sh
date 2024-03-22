#!/bin/bash



./sbt "project wispsoapconverter" \
-Dsonar.host.url="https://sonarcloud.io/" \
-Dsonar.projectName="pagopa-wisp-soap-converter" \
-Dsonar.projectKey="pagopa_pagopa-wisp-soap-converter" \
-Dsonar.sources=src/main/scala \
-Dsonar.tests=src/test/scala \
-Dsonar.junit.reportPaths=target/test-reports \
-Dsonar.scala.coverage.reportPaths="target/scala-2.13/scoverage-report/scoverage.xml" \
-Dsonar.login=$SONAR_TOKEN \
-Dsonar.pullrequest.key=1 \
-Dsonar.pullrequest.branch=$(git branch --show-current) \
-Dsonar.pullrequest.base=main \
-Dsonar.organization="pagopa" reload clean coverage test coverageReport #sonarScan