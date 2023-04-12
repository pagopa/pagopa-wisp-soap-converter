#!/bin/bash

DIR=.
NAME=ndp-cron
NAMESPACE=nodo-cron
FILE_CONFIG_PATH_LOGBACK=../config/dev/logback.xml
FILE_CONFIG_PATH_CONFIGAPP=../config/dev/config-app.conf
location=weu
suspend=false

usage() {
  echo "Usage: $0 [--update] [--install] [--uninstall] [--version <version>]" 1>&2;
  echo ""
  echo "Options:"
  echo "--update                Run helm dep update before installing."
  echo "--install               Install the application."
  echo "--uninstall             Uninstall the application."
  echo "--version <version>     Specify a version for the deploy."
  exit 1;
}

while [[ "$#" -gt 0 ]]; do
    case $1 in
        --update)
            update=1
            ;;
        --install)
            install=1
            ;;
        --uninstall)
            uninstall=1
            ;;
        --weu)
            location="weu"
            ;;
        --neu)
            location="neu"
            suspend=true
            ;;
        --version)
            shift
            version="$1"
            ;;
        -h|--help)
            usage
            exit 0
            ;;

        *)
            echo "Unknown parameter passed: $1" >&2
            exit 1
            ;;
    esac
    shift
done

if [ "$install" == 1 ]; then
  if [ ! -n "$version" ]; then
    echo "Error: Version parameter required with --install." >&2
    exit 1
  fi
fi

if [ -n "$update" ]; then
  echo "Updating dependencies"
  helm dep update $DIR
fi

if [ "$location" == "weu" ]; then
  valuesFile=$location-dev/values-dev.yaml
  context="pagopa-d-$location-dev-aks"
else
  valuesFile=$location-dev/values-dev.yaml
  context="pagopa-d-$location-dev-aks"
fi
echo "Using
context      | $context
valuesFile   | $valuesFile
"

kubectl config use-context $context

if [ "$install" == 1 ]; then
  echo "Installing stable version $version"

  declare -a JOBS=(cj-annullamento-rpt cj-ftp-upload cj-genera-rend-bollo cj-mod3-cancel-v1 cj-mod3-cancel-v2 cj-pa-invia-rt cj-pa-invia-rt-recovery cj-pa-retry-invia-rt-neg cj-pa-send-rt cj-pos-retry-send-payment-res cj-psp-chiedi-avanz-rpt cj-psp-chiedi-lista-rt cj-psp-retry-ack-negative cj-retry-pa-attiva-rpt cj-rt-pull-recovery-push)
      SETIMAGES="--set cj-refresh-configuration.suspend=$suspend"
      SETFILES=""
      for i in "${JOBS[@]}" ; do :
         SETIMAGES="$SETIMAGES --set $i.image.tag=$version --set $i.suspend=$suspend"
         SETFILES="$SETFILES --set-file $i.configMapFromFile.logback\\.xml=$FILE_CONFIG_PATH_LOGBACK"
         SETFILES="$SETFILES --set-file $i.configMapFromFile.config-app\\.conf=$FILE_CONFIG_PATH_CONFIGAPP"
      done
  helm upgrade --namespace $NAMESPACE --install --values $valuesFile \
    $SETIMAGES \
    $SETFILES \
    $NAME $DIR
  exit 0
fi

if [ "$uninstall" == 1 ]; then
  echo "Uninstalling stable"
  helm uninstall --namespace $NAMESPACE --wait $NAME
fi

exit 0