#!/bin/bash
STEP=$1
# 0 dry run both
# 1 install current version
# 2 upgrade canary 0%
# 3 upgrade canary 30%
# 4 upgrade canary 50%
# 5 upgrade stable to new version
# 6 drop canary

DIR=./helm/nodo
NAME=ndp
NAMESPACE=nodo
CURRENT_VERSION="3.12.0-SNAPSHOT"
NEW_VERSION="3.14.0-SNAPSHOT"
FILE_CONFIG_PATH_LOGBACK=$PWD/helm/config/dev/logback.xml
CURRENT_FILE_CONFIG_PATH_CONFIGAPP=$PWD/helm/config/dev/config-app-current.conf
NEW_FILE_CONFIG_PATH_CONFIGAPP=$PWD/helm/config/dev/config-app.conf

if [ $1 = "0" ]; then
  helm dep update $DIR

  helm upgrade --dry-run --namespace $NAMESPACE --install --values $DIR/values-dev.yaml \
  --set nodo.image.tag=$NEW_VERSION \
  --set nodo.version=$NEW_VERSION \
  --set nodo.appVersion=$NEW_VERSION \
  --set nodo.canaryDelivery.create="true" \
  --set nodo.canaryDelivery.ingress.type="header" \
  --set nodo.canaryDelivery.ingress.weightPercent="0" \
  --set nodo.canaryDelivery.ingress.headerName="test" \
  --set nodo.canaryDelivery.ingress.headerValue="test_value" \
  --set-file nodo.configMapFromFile.logback\\.xml="$FILE_CONFIG_PATH_LOGBACK" \
  --set-file nodo.configMapFromFile.config-app\\.conf="$NEW_FILE_CONFIG_PATH_CONFIGAPP" \
  $NAME-canary $DIR > $DIR/dry-canary.yaml

  helm upgrade --dry-run --namespace $NAMESPACE --install --values $DIR/values-dev.yaml \
  --set nodo.image.tag=$CURRENT_VERSION \
  --set nodo.version=$CURRENT_VERSION \
  --set nodo.appVersion=$CURRENT_VERSION \
  --set-file "nodo.configMapFromFile.logback\\.xml"="$FILE_CONFIG_PATH_LOGBACK" \
  --set-file "nodo.configMapFromFile.config-app\\.conf"="$CURRENT_FILE_CONFIG_PATH_CONFIGAPP" \
  $NAME $DIR > $DIR/dry.yaml
  exit 0
fi

if [ $1 = "1" ]; then
  helm upgrade --namespace $NAMESPACE --install --values $DIR/values-dev.yaml \
  --set nodo.image.tag=$CURRENT_VERSION \
  --set nodo.version=$CURRENT_VERSION \
  --set nodo.appVersion=$CURRENT_VERSION \
  --set-file nodo.fileConfig.logback\\.xml="$FILE_CONFIG_PATH_LOGBACK" \
  --set-file nodo.fileConfig.config-app\\.conf="$CURRENT_FILE_CONFIG_PATH_CONFIGAPP" \
  $NAME $DIR
  exit 0
fi

if [ $1 = "2" ]; then
  helm upgrade --namespace $NAMESPACE --install --values $DIR/values-dev.yaml \
  --set nodo.image.tag=$NEW_VERSION \
  --set nodo.version=$NEW_VERSION \
  --set nodo.appVersion=$NEW_VERSION \
  --set nodo.canaryDelivery.create="true" \
  --set nodo.canaryDelivery.ingress.weightPercent="0" \
  --set nodo.canaryDelivery.ingress.headerName="test" \
  --set nodo.canaryDelivery.ingress.headerValue="test_value" \
  --set-file nodo.fileConfig.logback\\.xml="$FILE_CONFIG_PATH_LOGBACK" \
  --set-file nodo.fileConfig.config-app\\.conf="$NEW_FILE_CONFIG_PATH_CONFIGAPP" \
  $NAME-canary $DIR
  exit 0
fi

if [ $1 = "3" ]; then
  helm upgrade --namespace $NAMESPACE --install --values $DIR/values-dev.yaml \
  --set nodo.image.tag=$NEW_VERSION \
  --set nodo.version=$NEW_VERSION \
  --set nodo.appVersion=$NEW_VERSION \
  --set nodo.canaryDelivery.create="true" \
  --set nodo.canaryDelivery.ingress.weightPercent="30" \
  --set nodo.canaryDelivery.ingress.headerName="test" \
  --set nodo.canaryDelivery.ingress.headerValue="test_value" \
  --set-file nodo.fileConfig.logback\\.xml="$FILE_CONFIG_PATH_LOGBACK" \
  --set-file nodo.fileConfig.config-app\\.conf="$NEW_FILE_CONFIG_PATH_CONFIGAPP" \
  $NAME-canary $DIR
  exit 0
fi

if [ $1 = "4" ]; then
  helm upgrade --namespace $NAMESPACE --install --values $DIR/values-dev.yaml \
  --set nodo.image.tag=$NEW_VERSION \
  --set nodo.version=$NEW_VERSION \
  --set nodo.appVersion=$NEW_VERSION \
  --set nodo.canaryDelivery.create="true" \
  --set nodo.canaryDelivery.ingress.weightPercent="50" \
  --set nodo.canaryDelivery.ingress.headerName="test" \
  --set nodo.canaryDelivery.ingress.headerValue="test_value" \
  --set-file nodo.fileConfig.logback\\.xml="$FILE_CONFIG_PATH_LOGBACK" \
  --set-file nodo.fileConfig.config-app\\.conf="$NEW_FILE_CONFIG_PATH_CONFIGAPP" \
  $NAME-canary $DIR
  exit 0
fi

if [ $1 = "5" ]; then
  helm upgrade --namespace $NAMESPACE --install --values $DIR/values-dev.yaml \
  --set nodo.image.tag=$NEW_VERSION \
  --set nodo.version=$NEW_VERSION \
  --set nodo.appVersion=$NEW_VERSION \
  --set-file nodo.fileConfig.logback\\.xml="$FILE_CONFIG_PATH_LOGBACK" \
  --set-file nodo.fileConfig.config-app\\.conf="$NEW_FILE_CONFIG_PATH_CONFIGAPP" \
  $NAME $DIR
  exit 0
fi

if [ $1 = "6" ]; then
  helm uninstall --namespace $NAMESPACE --wait $NAME-canary
  exit 0
fi