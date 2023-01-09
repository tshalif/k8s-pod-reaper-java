package org.nargila.k8s.reaper;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Label matcher.
 */
public class LabelMatcher {
    /**
     * label name.
     */
    private final String label;

    /**
     * match pattern.
     */
    private final Pattern pattern;

    /**
     * create matcher for label name and match pattern.
     * @param label label name
     * @param pattern match regex pattern
     */
    public LabelMatcher(String label, String pattern) {
        this.label = label;
        this.pattern = Pattern.compile(pattern);
    }

    /**
     * @return label name
     */
    public String getLabel() {
        return this.label;
    }

    /**
     * @return match pattern
     */
    public Pattern getPattern() {
        return this.pattern;
    }

    /**
     * @return string representation
     */
    @Override
    public String toString() {
        return "LabelMatcher{"
                + "label='"
                + this.label
                + '\''
                + ", pattern="
                + this.pattern
                + '}';
    }

    /**
     * @param specs label matcher specs
     * @return LabelMatcher list
     */
    public static List<LabelMatcher> buildMatchers(@NotNull String specs) {
        return Arrays.stream(specs.split(",")).map(
                LabelMatcher::makeLabelMatcher
        ).collect(Collectors.toList());
    }

    @NotNull
    @Contract("_ -> new")
    private static LabelMatcher makeLabelMatcher(@NotNull String s) {
        final String[] vars = s.split("=", 2);
        if (vars.length != 2) {
            throw new IllegalArgumentException(
                    String.format(
                            "malformed LabelMatcher spec token '%s'",
                            s
                    )
            );
        }
        return new LabelMatcher(vars[0], vars[1]);
    }
}
