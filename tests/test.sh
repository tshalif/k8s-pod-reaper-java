#!/bin/bash

usage="$0 <namespace> <match-mode>"

test $# -eq 2 || {
    echo 1>&2 $usage
    exit 1
}

set -Eeu -o pipefail

d=$(dirname $0)

namespace=$1
match_mode=$(echo $2 | tr '[a-z]' '[A-Z]')

waitresource() {
    mode=$1
    resource=$2
    path=$3
    timeout=$4
    
    if test $# -eq 5; then
        labels="-l $5"
    fi

    i=0
    while test ${i} -lt ${timeout}; do
        case "$(kubectl -n ${namespace} get ${resource} ${labels:-} --output=json | jq -r "$path")" in
            null|"") test $mode = no && return 0 || true;;
            *) test $mode = yes && return 0 || true;;
        esac
        i=$[$i + 1]
        sleep 1
    done

    return 1
}

waitpod() {
    labels=$1
    timeout=$2

    waitresource yes pod ".items[0].metadata.name" ${timeout} "${labels}"
}

waitns() {
    timeout=$1

    waitresource yes serviceaccount/default .metadata.name ${timeout}
}
  

kubectl create namespace ${namespace}

waitns 30

deploydummy() {
    kubectl apply -f - --namespace ${namespace} <<EOF 
apiVersion: v1
kind: Pod
metadata:
  name: $(uuid)
  labels: ${1}
spec:
  containers:
    - name: pause
      image: tshalif/pause:1.0
EOF
}
    
deploydummies() {
    deploydummy '{"l1":"l1", "l2":"l2", "dead_or":"yes", "dead_and":"no"}'
    deploydummy '{"l3":"l3", "l1":"l1", "dead_or":"no", "dead_and":"no"}'
    deploydummy '{"l1":"l1", "l4":"l4", "dead_or":"yes", "dead_and":"no"}'
    deploydummy '{"l2":"l2", "l4":"l4", "dead_or":"yes", "dead_and":"yes"}' 
    deploydummy '{"l3":"l3", "dead_or":"no", "dead_and":"no"}'
}

deployreaper() {
    make deploy K8S_NAMESPACE=${namespace} REAP_POD_LABEL_MATCHERS="l2=l2,l4=l4" REAP_LABEL_MATCH_TYPE=${match_mode} REAP_POD_MAX_AGE_SECONDS=20 RUN_SLEEP_TIME=5 K8S_DEPLOYMENT_NAME=pod-reaper-test
    waitpod app=pod-reaper-test 10
    kubectl -n ${namespace} wait --for=condition=ready pod -l app=pod-reaper-test --timeout=240s
}

deploydummies
deployreaper

waitnopod() {
    labels=$1
    timeout=$2

    waitresource no pod ".items[0].metadata.name" ${timeout} "${labels}"
}

case $match_mode in
    OR) dead_pods="dead_or=yes"; live_pods="dead_or=no";;
    AND) dead_pods="dead_and=yes"; live_pods="dead_and=no";;
    *) echo unrecognized mode "$match_mode"; exit 1;;
esac

for i in $dead_pods; do
    waitnopod $i 80
done

for i in $live_pods; do
    waitpod $i 1
done

