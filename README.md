# k8s-pod-reaper-java
Provides Kubernetes workload for deleting stale POD instances.

Note: Normally such a service should consume very little memroy and CPU, so
usging this Java implemention is not ideal for low resource clusters.

## Configuration
The configuration comes from environment variables. There are some defaults in [.env](.env), which will set
the [PodReaper](src/main/java/org/nargila/k8s/PodReaper.java) instance to try and delete Gitlab runner pipeline job POD
instances older then 3700 seconds old (1 hour + 2 minute grace period).

### REAP_NAMESPACE
default: `gitlab-managed-apps`

Target namespace, in which POD instances are to be monitored and deleted
### REAP_POD_LABEL_MATCHERS
default: `pod=^runner-.*-project-[0-9]+-concurrent-[0-9]+$`

POD instances matching these labels will be considered for deletion. A label matcher has the format `<label-name>=<regular-expression>`. Multiple label matchers can be
provided separated with a comman `,`. See [REAP_LABEL_MATCH_TYPE](#REAP_LABEL_MATCH_TYPE)

### REAP_LABEL_MATCH_TYPE
default: `AND`

Type of matching to perform when `REAP_POD_LABEL_MATCHERS` has multiple matchers. Accepted values are:
* AND: all matchers must match  
* OR: at least one matcher must match

### REAP_POD_MAX_AGE_SECONDS
default: `3700`

POD age in seconds before it becomes eligible for deletion

### RUN_SLEEP_TIME
default: `300`

Sleep time in between runs

### DRY_RUN
default: `false`

Run in dry run mode

## Deployment
In this section we deploy Pod Reaper into namespace `gitlab-managed-apps` as well as configure it to monitor POD instance also in `gitlab-managed-apps`.

We need to use the following `pod-reaper` service account, role and rolebinding to grant the Pod Reaper deployment access to the K8S cluster resources:

```bash
kubectl create serviceaccount pod-reaper --namespace gitlab-managed-apps
kubectl create role pod-reaper --verb=get,list,watch,delete --resource=pods --namespace=gitlab-managed-apps
kubectl create rolebinding pod-reaper --role=pod-reaper --serviceaccount=gitlab-managed-apps:pod-reaper --namespace=gitlab-managed-apps
```

Then deploy the following yaml:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    app: pod-reaper
  name: pod-reaper
  namespace: gitlab-managed-apps
spec:
  selector:
    matchLabels:
      app: pod-reaper
  template:
    metadata:
      labels:
        app: pod-reaper
    spec:
      serviceAccountName: pod-reaper
      containers:
      - image: tshalif/pod-reaper-java:v0.1.0
        name: pod-reaper
        env:
        - name: REAP_POD_LABEL_MATCHERS
          value: "pod=^runner-.*-project-[0-9]+-concurrent-[0-9]+$"
        - name: REAP_POD_MAX_AGE_SECONDS
          value: "3700"
        - name: RUN_SLEEP_TIME
          value: "300"
        - name: REAP_NAMESPACE
          value: gitlab-managed-apps
        - name: REAP_LABEL_MATCH_TYPE
          value: AND
```
