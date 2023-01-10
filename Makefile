DOCKER_IMG_TAG = tshalif/pod-reaper-java
DOCKER_IMG_VERSION = v0.1.1


docker:
	docker build -t $(DOCKER_IMG_TAG):$(DOCKER_IMG_VERSION) .
ifeq ($(DOCKER_PUSH),1)
	docker push $(DOCKER_IMG_TAG):$(DOCKER_IMG_VERSION)
endif

TEMPLATE_SUBST_ARGS = REAP_NAMESPACE \
	REAP_POD_LABEL_MATCHERS \
	REAP_LABEL_MATCH_TYPE \
	REAP_POD_MAX_AGE_SECONDS \
	RUN_SLEEP_TIME \
	DRY_RUN \
	DOCKER_IMG_VERSION \
	K8S_DEPLOYMENT_NAME

ifdef KUBECTL_DRY_RUN
KUBECTL_ARGS = --dry-run=client
endif

K8S_DEPLOYMENT_NAME = pod-reaper

deploy: REAP_NAMESPACE = $(K8S_NAMESPACE)
deploy: DRY_RUN = false
deploy:
ifeq ($(K8S_NAMESPACE),)
	$(error K8S_NAMESPACE for deploying pod-reaper must be specified)
endif
	-kubectl create serviceaccount pod-reaper $(KUBECTL_ARGS) --namespace $(K8S_NAMESPACE)
	-kubectl create role pod-reaper --verb=get,list,watch,delete --resource=pods $(KUBECTL_ARGS) --namespace=$(K8S_NAMESPACE)
	-kubectl create rolebinding pod-reaper --role=pod-reaper --serviceaccount=$(K8S_NAMESPACE):pod-reaper $(KUBECTL_ARGS) --namespace=$(K8S_NAMESPACE)
	sed $(foreach a,$(TEMPLATE_SUBST_ARGS),-e 's/@@$(a)@@/$($(a))/g') etc/deployment.yaml.in | tee /dev/stderr | kubectl apply -f - $(KUBECTL_ARGS) --namespace $(K8S_NAMESPACE)

KIND = kind

ifdef KIND_CLUSTER_NAME
KIND += --name $(KIND_CLUSTER_NAME)
endif

.kind-init: .kind-destroy
	$(KIND) create cluster

.kind-destroy:
	-$(KIND) delete cluster

.kind-load-image:
	$(KIND) load docker-image $(DOCKER_IMG_TAG):$(DOCKER_IMG_VERSION)

test: .kind-init test-image

test-image: .kind-load-image test-and test-or

test-%:
	./tests/test.sh reaper-test-$* $*

build:
	mvn package

clean: .kind-destroy
	mvn clean
	find -name '*~' | xargs rm -f

