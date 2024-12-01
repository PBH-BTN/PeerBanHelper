package io.ipfs.api;

public class Version implements Comparable<Version> {

    public final int major, minor, patch;
    public final String suffix;

    public Version(int major, int minor, int patch, String suffix) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.suffix = suffix;
    }

    public String toString() {
        return major + "." + minor + "." + patch + (suffix.length() > 0 ? "-" + suffix : "");
    }

    public boolean isBefore(Version other) {
        return this.compareTo(other) < 0;
    }

    @Override
    public int compareTo(Version other) {
        int major = Integer.compare(this.major, other.major);
        if (major != 0)
            return major;
        int minor = Integer.compare(this.minor, other.minor);
        if (minor != 0)
            return minor;
        int patch = Integer.compare(this.patch, other.patch);
        if (patch != 0)
            return patch;
        if (suffix.length() == 0)
            return 1;
        if (other.suffix.length() == 0)
            return -1;
        return suffix.compareTo(other.suffix);
    }

    public static Version parse(String version) {
        int first = version.indexOf(".");
        int second = version.indexOf(".", first + 1);
        int third = version.contains("-") ? version.indexOf("-") : version.length();

        int major = Integer.parseInt(version.substring(0, first));
        int minor = Integer.parseInt(version.substring(first + 1, second));
        int patch = Integer.parseInt(version.substring(second + 1, third));
        String suffix = third < version.length() ? version.substring(third + 1) : "";
        return new Version(major, minor, patch, suffix);
    }
}
