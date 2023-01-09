package org.nargila.k8s.common;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;


/**
 * POD helper Utility class.
 */
public final class PodHelper {

    /**
     * HideUtilityClassConstructor.
     */
    private PodHelper() {
    }

    /**
     * Get pod label value.
     * @param pod K8S pod
     * @param label lavel name
     * @return label value if found or null
     */
    @Nullable
    public static String getLabel(@NotNull V1Pod pod, @NotNull String label) {
        final Map<String, String> labels = getLabels(pod);

        if (labels == null) {
            return null;
        }

        return labels.get(label);
    }

    /**
     * Get pod labels.
     * @param pod K8S pod
     * @return pod labels if exists or null
     */
    @Nullable
    public static Map<String, String> getLabels(@NotNull V1Pod pod) {
        if (pod.getMetadata() == null) {
            return null;
        }

        return pod.getMetadata().getLabels();
    }

    /**
     * Get pod name.
     * @param pod K8S pod
     * @return pod name if exists or null
     */
    @Nullable
    public static String getName(@NotNull V1Pod pod) {
        if (pod.getMetadata() == null) {
            return null;
        }

        return pod.getMetadata().getName();
    }

    /**
     * Get pod start time.
     * @param pod K8S pod
     * @return pod start time if exists or null
     */
    public static long getStartTime(@NotNull V1Pod pod) {
        if (pod.getStatus() == null) {
            return 0L;
        }

        final OffsetDateTime podStartTime = pod.getStatus().getStartTime();

        return podStartTime == null ? 0L : podStartTime.toEpochSecond();
    }

    /**
     * Get calculate pod age.
     * @param pod K8S pod
     * @return pod age in seconds or 0 if age cannot be calculated
     */
    public static long getPodAge(V1Pod pod) {
        final long startTime = getStartTime(pod);

        if (startTime != 0L) {
            final OffsetDateTime now = OffsetDateTime.now(ZoneId.of("UTC"));
            return now.toEpochSecond() - startTime;
        }

        return 0L;
    }

    /**
     * Get pod deletion time.
     * @param pod K8S pod
     * @return pod deletion time if exists or null
     */
    public static OffsetDateTime getDeletionTimestamp(V1Pod pod) {
        final V1ObjectMeta metadata = pod.getMetadata();
        return metadata == null ? null : metadata.getDeletionTimestamp();
    }
}
