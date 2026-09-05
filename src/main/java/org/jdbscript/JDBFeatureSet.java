package org.jdbscript;

import java.util.EnumMap;
import java.util.Map;

import static org.jdbscript.errors.Checks.checkNotNull;
import static org.jdbscript.errors.JDBErrors.FEATURE_IS_NULL;

/**
 * A collection of {@link JDBFeature}s, holding at most one member per {@link JDBFeature.Group}.
 * {@link #add(JDBFeature)} enforces that: adding a feature whose group already has a member
 * replaces it rather than adding alongside it, so callers - {@link JDBEngine.Builder#feature(JDBFeature)}
 * and anything reading the set back, such as DB2's identity-owned-sequence handling - never need
 * to reason about mutually-exclusive alternatives themselves.
 */
public final class JDBFeatureSet {
    private final Map<JDBFeature.Group, JDBFeature> byGroup;

    private JDBFeatureSet(Map<JDBFeature.Group, JDBFeature> byGroup) {
        this.byGroup = byGroup;
    }

    /**
     * @return a new, empty feature set
     */
    public static JDBFeatureSet empty() {
        return new JDBFeatureSet(new EnumMap<>(JDBFeature.Group.class));
    }

    /**
     * @param features the features to seed the set with; a later one replaces an earlier one from
     *                 the same group
     * @return a new feature set containing {@code features}
     */
    public static JDBFeatureSet of(JDBFeature... features) {
        JDBFeatureSet set = empty();
        for (JDBFeature feature : features) {
            set.add(feature);
        }
        return set;
    }

    /**
     * An independent copy of {@code other}, safe to mutate without affecting it.
     *
     * @param other the feature set to copy
     * @return the copy
     */
    public static JDBFeatureSet copyOf(JDBFeatureSet other) {
        return new JDBFeatureSet(new EnumMap<>(other.byGroup));
    }

    /**
     * Adds {@code feature}, replacing whatever feature (if any) already occupies its
     * {@link JDBFeature#getGroup()}.
     *
     * @param feature the feature to enable; must not be null
     * @return this instance, for chaining
     */
    public JDBFeatureSet add(JDBFeature feature) {
        checkNotNull(feature, FEATURE_IS_NULL);
        byGroup.put(feature.getGroup(), feature);
        return this;
    }

    /**
     * The feature active for {@code group}, or {@code fallback} if the group has no active member.
     *
     * @param group    the feature group to look up
     * @param fallback returned when {@code group} has no active member
     * @return the active feature for the group, or {@code fallback}
     */
    public JDBFeature getOrDefault(JDBFeature.Group featureGroup, JDBFeature fallback) {
        JDBFeature result =  byGroup.getOrDefault(featureGroup, fallback);
        if(result.getGroup() != featureGroup){
            throw new RuntimeException("Feature group mismatch. Please fix!");
        }
        return result;
    }
}
