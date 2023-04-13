#!/bin/bash

ENV=uat
IMAGE_VERSION="1.0.0-SNAPSHOT"
CANARY_IMAGE_VERSION="1.0.0-SNAPSHOT"

FILE_CONFIG_PATH_LOGBACK=helm/config/${ENV}/logback.xml
FILE_CONFIG_PATH_CONFIG=helm/config/${ENV}/config-app.conf

clean (){
  #$> clean "helm/fdr"
  CHART_PATH=$1
  rm -rf ${CHART_PATH}/charts
  rm -f ${CHART_PATH}/Chart.lock
}

fixVersion () {
  #$> fixAppVersion "helm/fdr/Chart.yaml"
  CHART_FILE=$1
  if [[ -f "$CHART_FILE" ]]; then
    yq -i ".appVersion = \"${IMAGE_VERSION}\"" "$CHART_FILE"
  fi
}

########## APP ############
#helm uninstall --namespace fdr pagopafdrnodo
clean "helm/fdr"

helm repo add microservice-chart https://pagopa.github.io/aks-microservice-chart-blueprint
helm dep build helm/fdr

fixVersion "helm/fdr/Chart.yaml"

helm upgrade --install --namespace nodo \
    --values helm/fdr/values-${ENV}.yaml \
    --set 'fdr.image.tag'="${CANARY_IMAGE_VERSION}" \
    --set 'fdr.canaryDelivery.create'="false" \
    --set 'fdr.canaryDelivery.deployment.image.tag'="${CANARY_IMAGE_VERSION}" \
    --set 'fdr.canaryDelivery.ingress.canary.weightPercent'="50" \
    --set-file 'fdr.fileConfig.logback\.xml'="${FILE_CONFIG_PATH_LOGBACK}" \
    --set-file 'fdr.fileConfig.config-app\.conf'="${FILE_CONFIG_PATH_CONFIG}" \
    ndp helm/fdr --dry-run > test.yml


########## CRON ############
helm uninstall --namespace fdr-cron fdr-cron
clean "helm/nodo-cron"

helm repo add fdr-chart https://pagopa.github.io/aks-cron-chart-blueprint
helm dep build helm/fdr-cron

fixVersion "helm/fdr-cron/Chart.yaml"

helm upgrade --install --namespace fdr-cron \
    --values helm/fdr-cron/values-${ENV}.yaml \
    --set 'cj-ftp-upload.image.tag'="${IMAGE_VERSION}" \
    --set-file 'cj-ftp-upload.fileConfig.files.logback\.xml'="${FILE_CONFIG_PATH_LOGBACK}" \
    --set-file 'cj-ftp-upload.fileConfig.files.config-app\.conf'="${FILE_CONFIG_PATH_CONFIG}" \
    ndp-cron helm/fdr-cron --dry-run > test-cron.yml
