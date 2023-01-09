package org.nargila.k8s;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Config;
import org.jetbrains.annotations.NotNull;
import org.nargila.common.ConfVars;
import org.nargila.k8s.common.PodHelper;
import org.nargila.k8s.reaper.LabelMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;


public class PodReaper {
    /**
     * logger.
     */
    private final Logger logger = LoggerFactory.getLogger(getClass().getName());
    /**
     * Kubernetes API.
     */
    private final CoreV1Api api;
    /**
     * reap pod target namespace.
     */
    private final String namespace;
    /**
     * reaped pod allowed max pod age in seconds.
     */
    private final int podMaxAge;
    /**
     * sleep time in seconds between runs.
     */
    private final int sleepTime;
    /**
     * dry run mode.
     */
    private final boolean dryRun;
    /**
     * label matchers type.
     * All pod labels have to match if 'AND', any label may match if 'OR'.
     */
    private final MatchType matchType;

    private enum MatchType {
        /**
         * OR matching mode.
         */
        OR,
        /**
         * AND matching mode.
         */
        AND
    }

    /**
     * List of label matchers.
     */
    private final List<LabelMatcher> labelMatchers;

    /**
     * Full constructor.
     * @param api K8S APIs
     * @param namespace reaper target POD namespace
     * @param labelMatchers label matchers
     * @param podMaxAge pod max age
     * @param sleepTime run sleep time
     * @param matchType label matcher mode
     * @param dryRun dry run
     */
    public PodReaper(
            @NotNull CoreV1Api api,
            @NotNull String namespace,
            @NotNull List<LabelMatcher> labelMatchers,
            int podMaxAge,
            int sleepTime,
            @NotNull String matchType,
            boolean dryRun
    ) {
        this.api = api;
        this.namespace = validateNamespace(namespace);
        this.podMaxAge = validatePositive("podMaxAge", podMaxAge);
        this.sleepTime = validatePositive("sleepTime", sleepTime);
        this.matchType = MatchType.valueOf(matchType);
        this.dryRun = dryRun;

        this.labelMatchers = validateMatchers(labelMatchers);
    }

    private static String validateNamespace(@NotNull String namespace) {
        if (namespace.trim().equals("")) {
            throw new IllegalArgumentException("namespace mast not be empty");
        }
        return namespace;
    }

    private static int validatePositive(String name, int val) {
        if (val <= 0) {
            throw new IllegalArgumentException(
                    String.format("%s mast be larger then zero mast be larger then zero", name)
            );
        }
        return val;
    }

    private static List<LabelMatcher> validateMatchers(
            final List<LabelMatcher> labelMatchers
    ) {
        if (labelMatchers.size() == 0) {
            throw new IllegalArgumentException(
                    "labelMatcher list can not be empty"
            );
        }

        final Set<String> duplicateCheck = new HashSet<>();
        for (LabelMatcher matcher: labelMatchers) {
            if (duplicateCheck.contains(matcher.getLabel())) {
                throw new IllegalArgumentException(
                        String.format(
                                "duplicate matcher found for label '%s'",
                                matcher.getLabel()
                        )
                );
            }
            duplicateCheck.add(matcher.getLabel());
        }

        return labelMatchers;
    }

    /**
     * Run loop.
     * @throws ApiException
    */
    @SuppressWarnings("BusyWait")
    public void run() throws ApiException {
        while (true) {
            this.logger.info(
                    "run: {}start: matchers={}, mode={}",
                    this.dryRun ? "(dry-run) " : "",
                    this.labelMatchers,
                    this.matchType
            );

            final int reapedPodCount = this.runOnce();
            this.logger.info(
                    "run: reaped {} pods matching {} ({})",
                    reapedPodCount,
                    this.labelMatchers,
                    this.matchType
            );

            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(this.sleepTime));
            } catch (InterruptedException e) {
                this.logger.warn("run: interrupted - exiting loop", e);
                break;
            }
        }
    }

    /**
     * Run one loop iteration.
     * @return number of pods deleted
     * @throws ApiException if K8S API exception occurred
     */
    public int runOnce() throws ApiException {
        final V1PodList podList = this.api.listNamespacedPod(
                this.namespace,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false
        );
        int reapedPodCount = 0;

        for (final V1Pod pod: podList.getItems()) {
            if (this.processPod(pod)) {
                reapedPodCount++;
            }
        }
        return reapedPodCount;
    }

    private boolean checkPodLabelsMatch(V1Pod pod) {
        final Predicate<LabelMatcher> labelMatcherPredicate = labelMatcher -> {
            final String labelValue = PodHelper.getLabel(
                    pod,
                    labelMatcher.getLabel()
            );
            return labelValue != null
                    && labelMatcher.getPattern().matcher(labelValue).matches();
        };
        final List<LabelMatcher> matches = this.labelMatchers.stream().filter(
                labelMatcherPredicate
        ).collect(Collectors.toList());

        if (this.matchType == MatchType.OR) {
            return matches.size() > 0;
        }

        return matches.size() == this.labelMatchers.size();
    }

    /**
     * Checks and deletes a pod if it matches labels and age eligible.
     * @param pod pod to process
     * @return true if pod was deleted, false otherwise
     */
    private boolean processPod(V1Pod pod) {
        final boolean labelsMatch = this.checkPodLabelsMatch(pod);
        final long podAge = PodHelper.getPodAge(pod);
        final boolean deletePod = labelsMatch && podAge > this.podMaxAge;

        this.logger.info(
                "processPod: {}match={}, pod={}, labels={}, podAge={}/{}",
                this.dryRun ? "(dry-run) " : "",
                labelsMatch,
                PodHelper.getName(pod),
                PodHelper.getLabels(pod),
                podAge,
                this.podMaxAge
        );

        if (deletePod) {
            try {
                return this.deletePod(pod);
            } catch (ApiException e) {
                this.logger.warn("deletePod: {}", e.getResponseBody());
            }
        }

        return false;
    }


    /**
     * delete pod.
     * @param pod pod to delete
     * @return true if deleted, false otherwise
     * @throws ApiException if K8S API throws an error
     */
    private boolean deletePod(V1Pod pod) throws ApiException {
        this.logger.info(
                "deletePod: {}deleting pod={}",
                this.dryRun ? "(dry-run) " : "",
                PodHelper.getName(pod)
        );

        if (this.dryRun) {
            return false;
        }

        if (PodHelper.getDeletionTimestamp(pod) != null) {
            this.logger.info(
                    "deletePod: skipping terminating pod={}",
                    PodHelper.getName(pod)
            );

            return false;
        }

        this.api.deleteNamespacedPod(
                PodHelper.getName(pod),
                this.namespace,
                null,
                null,
                null,
                null,
                null,
                null
        );

        return true;
    }

    /**
     * Run pod reaper in a loop.
     * @param args no arguments are used
     */
    public static void main(String[] args) {
        try {
            final ConfVars confVars = new ConfVars();
            final ApiClient client = (
                    confVars.get("KUBERNETES_SERVICE_PORT", "").equals("")
                            ? Config.defaultClient()
                            : Config.fromCluster()
            );
            Configuration.setDefaultApiClient(client);
            final CoreV1Api api = new CoreV1Api();
            final PodReaper podReaper = new PodReaper(
                    api,
                    confVars.get("REAP_NAMESPACE"),
                    LabelMatcher.buildMatchers(
                            confVars.get("REAP_POD_LABEL_MATCHERS")
                    ),
                    confVars.getInt("REAP_POD_MAX_AGE_SECONDS"),
                    confVars.get("RUN_SLEEP_TIME", 300),
                    confVars.get("REAP_LABEL_MATCH_TYPE", "AND"),
                    confVars.get("DRY_RUN", false)
            );

            podReaper.run();
        } catch (IOException | ApiException e) {
            final String message = getMessage(e);
            System.err.printf("main: error: %s: %s%n", message, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Get exception message.
     * @param e exception instance
     * @return e.getResponseBody() if e is of type ApiException,
     *         else e.getMessage()
     */
    private static String getMessage(Exception e) {
        if (e instanceof ApiException) {
            return ((ApiException) e).getResponseBody();
        }

        return e.getMessage();
    }
}
