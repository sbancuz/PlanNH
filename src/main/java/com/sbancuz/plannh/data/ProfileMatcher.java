package com.sbancuz.plannh.data;

@FunctionalInterface
public interface ProfileMatcher {

    String match(String overlayId);

    static ProfileMatcher keyword(final String profileId, final String... keywords) {
        return id -> {
            for (final String kw : keywords) {
                if (id.contains(kw)) return profileId;
            }
            return null;
        };
    }

    static ProfileMatcher exact(final String profileId, final String... exacts) {
        return id -> {
            for (final String ex : exacts) {
                if (id.equals(ex)) return profileId;
            }
            return null;
        };
    }
}
