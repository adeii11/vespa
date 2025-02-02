// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.google.common.base.Strings;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A YUM/DNF package name.
 *
 * <p>From yum(8): YUM package names are used with install, update, remove, list, info etc
 * with any of the following as well as globs of any of the following, with any of the
 * following as well as globs of any of the following:
 *
 * <ol>
 *     <li>name
 *     <li>name.arch
 *     <li>name-ver
 *     <li>name-ver-rel
 *     <li>name-ver-rel.arch
 *     <li>name-epoch:ver-rel.arch
 *     <li>epoch:name-ver-rel.arch
 * </ol>
 *
 * <p>However this specification is terribly ambiguous. This class allows constructing
 * a package name from its components, which is beneficial because with certain YUM
 * commands that needs to canonicalize names (e.g. versionlock).
 *
 * @author hakonhall
 */
public class YumPackageName {

    private enum Architecture { noarch, x86_64, i386, i586, i686 }

    private static final String ARCHITECTURES_OR =
            Arrays.stream(Architecture.values()).map(Architecture::name).collect(Collectors.joining("|"));
    private static final Pattern ARCHITECTURE_PATTERN = Pattern.compile("\\.(" + ARCHITECTURES_OR + "|\\*)$");
    private static final Pattern EPOCH_PATTERN = Pattern.compile("^((.+)-)?([0-9]+)$");
    private static final Pattern NAME_VER_REL_PATTERN =
            Pattern.compile("^(.+?)?" +                                 // name
                            "-([+()a-z0-9._]*[0-9]+\\.[a-z0-9._]*)" +   // ver: contains at least one digit and dot
                            "(?:-([+()a-z0-9._]*[0-9][a-z0-9._]*))?$"); // rel: contains at least one digit
    private static final Pattern NAME_PATTERN = Pattern.compile("^[+()a-zA-Z0-9._-]+$");

    private final Optional<String> epoch;
    private final String name;
    private final Optional<String> version;
    private final Optional<String> release;
    private final Optional<String> architecture;

    public static class Builder {
        private Optional<String> epoch = Optional.empty();
        private String name;
        private Optional<String> version = Optional.empty();
        private Optional<String> release = Optional.empty();
        private Optional<String> architecture = Optional.empty();

        public Builder() { }

        public Builder(String name) {
            this.name = name;
        }

        public Builder(YumPackageName packageName) {
            epoch = packageName.epoch;
            name = packageName.name;
            version = packageName.version;
            release = packageName.release;
            architecture = packageName.architecture;
        }

        public Builder setEpoch(String epoch) { this.epoch = Optional.of(epoch); return this; }
        public Builder setName(String name) { this.name = name; return this; }
        public Builder setVersion(String version) { this.version = Optional.of(version); return this; }
        public Builder setRelease(String release) { this.release = Optional.of(release); return this; }
        public Builder setArchitecture(String architecture) { this.architecture = Optional.of(architecture); return this; }

        public Optional<String> epoch() { return epoch; }
        public String name() { return name; }
        public Optional<String> version() { return version; }
        public Optional<String> release() { return release; }
        public Optional<String> architecture() { return architecture; }

        public YumPackageName build() { return new YumPackageName(epoch, name, version, release, architecture); }
    }

    /** @see Builder */
    private YumPackageName(Optional<String> epoch,
                           String name,
                           Optional<String> version,
                           Optional<String> release,
                           Optional<String> architecture) {
        if (Strings.isNullOrEmpty(name))
            throw new IllegalArgumentException("name cannot be null or empty");
        this.epoch = epoch;
        this.name = name;
        this.version = version;
        this.release = release;
        this.architecture = architecture;
    }

    /**
     * Parse the string specification of a YUM package.
     *
     * <p>The following formats are supported:
     *
     * <ol>
     *     <li>name
     *     <li>name.arch
     *     <li>name-ver
     *     <li>name-ver-rel
     *     <li>name-ver-rel.arch
     *     <li>name-epoch:ver-rel.arch
     *     <li>epoch:name-ver-rel.arch
     * </ol>
     *
     * @throws IllegalArgumentException if spec does not specify a package name.
     * @see #parseString(String)
     */
    public static YumPackageName fromString(String packageSpec) {
        String spec = packageSpec;
        Optional<String> epoch = Optional.empty();
        String name = null;

        // Parse epoch and remove it from spec
        int epochColon = spec.indexOf(':');
        if (epochColon >= 0) {
            Matcher epochMatcher = EPOCH_PATTERN.matcher(spec.substring(0, epochColon));
            if (!epochMatcher.find()) {
                throw new IllegalArgumentException("Unexpected epoch format: " + packageSpec);
            }

            name = epochMatcher.group(2);
            epoch = Optional.of(epochMatcher.group(3));

            spec = epochColon == 0
                    ? spec.substring(epochColon + 1)
                    : spec.substring(0, epochColon - 1) + spec.substring(epochColon + 1);
        }

        // Parse architecture and remove it from spec
        Optional<String> architecture = Optional.empty();
        Matcher architectureMatcher = ARCHITECTURE_PATTERN.matcher(spec);
        if (architectureMatcher.find()) {
            architecture = Optional.of(architectureMatcher.group(1));
            spec = spec.substring(0, architectureMatcher.start());
        }

        // Parse name, version and release and remove the latter two from spec
        Optional<String> version = Optional.empty();
        Optional<String> release = Optional.empty();
        Matcher matcher = NAME_VER_REL_PATTERN.matcher(spec);
        if (matcher.find()) {
            spec = matcher.group(1);
            if (spec == null) {
                if (name == null) {
                    throw new IllegalArgumentException("No package name was found: " + packageSpec);
                }
                spec = name; // makes spec hold the package name in all cases below.
            }

            version = Optional.of(matcher.group(2));
            release = Optional.ofNullable(matcher.group(3));
        }

        if (!NAME_PATTERN.matcher(spec).find()) {
            throw new IllegalArgumentException("Bad package name in " + packageSpec + ": '" + spec + "'");
        }
        name = spec;

        return new YumPackageName(epoch, name, version, release, architecture);
    }

    /** See {@link #fromString(String)}. */
    public static Optional<YumPackageName> parseString(final String packageSpec) {
        try {
            return Optional.of(fromString(packageSpec));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Optional<String> getEpoch() { return epoch; }
    public String getName() { return name; }
    public Optional<String> getVersion() { return version; }
    public Optional<String> getRelease() { return release; }
    public Optional<String> getArchitecture() { return architecture; }

    /** Return package name, omitting components that are not specified. */
    public String toName() {
        StringBuilder builder = new StringBuilder();
        char nextDelimiter;
        builder.append(name);
        // Fully versioned package names must always include epoch in Yum 4
        epoch.or(() -> Optional.of("0").filter(v -> version.isPresent()))
             .ifPresent(ep -> builder.append('-').append(ep));
        nextDelimiter = ':';
        version.ifPresent(s -> builder.append(nextDelimiter).append(s));
        release.ifPresent(s -> builder.append('-').append(s));
        architecture.ifPresent(arch -> builder.append('.').append(arch));
        return builder.toString();
    }

    /**
     * The package name output by 'yum versionlock list'. Can also be used with 'add' and 'delete'.
     *
     * @throws IllegalStateException if any field required for the version lock spec is missing
     */
    public String toVersionLockName() {
        YumPackageName lockSpec = new Builder(this).setArchitecture("*").build();
        if (lockSpec.getVersion().isEmpty()) throw new IllegalStateException("Version is missing for YUM package " + name);
        if (lockSpec.getRelease().isEmpty()) throw new IllegalStateException("Release is missing for YUM package " + name);
        return lockSpec.toName();
    }

    public boolean isSubsetOf(YumPackageName other) {
        return Objects.equals(name, other.name) &&
                (epoch.isEmpty() || Objects.equals(epoch, other.epoch)) &&
                (version.isEmpty() || Objects.equals(version, other.version)) &&
                (release.isEmpty() || Objects.equals(release, other.release)) &&
                (architecture.isEmpty() || Objects.equals(architecture, other.architecture));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        YumPackageName that = (YumPackageName) o;
        return Objects.equals(epoch, that.epoch) &&
                Objects.equals(name, that.name) &&
                Objects.equals(version, that.version) &&
                Objects.equals(release, that.release) &&
                Objects.equals(architecture, that.architecture);
    }

    @Override
    public int hashCode() {
        return Objects.hash(epoch, name, version, release, architecture);
    }
}
