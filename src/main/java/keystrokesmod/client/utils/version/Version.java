package keystrokesmod.client.utils.version;

import java.util.ArrayList;

public class Version {
    private final String version;
    private final String branchName;
    private final int branchCommit;
    private final ArrayList<Integer> versionNumbers = new ArrayList<>();

    public Version(String version, String branchName, int branchCommit) {
        this.version = (version == null ? "0.0.0" : version).replace("-", ".");
        this.branchName = branchName == null ? "" : branchName;
        this.branchCommit = branchCommit;
        for (String number : this.version.split("\\.")) {
            try {
                this.versionNumbers.add(Integer.parseInt(number.trim()));
            } catch (NumberFormatException ignored) {
                this.versionNumbers.add(0);
            }
        }
        while (this.versionNumbers.size() < 3) {
            this.versionNumbers.add(0);
        }
    }

    public String getVersion() {
        return version;
    }

    public String getBranchName() {
        return branchName;
    }

    public int getBranchCommit() {
        return branchCommit;
    }

    public ArrayList<Integer> getVersionNumbers() {
        return versionNumbers;
    }

    public boolean isNewerThan(Version versionToCompare) {
        if (versionToCompare == null) {
            return true;
        }
        int maxLen = Math.max(this.versionNumbers.size(), versionToCompare.getVersionNumbers().size());
        for (int i = 0; i < maxLen; i++) {
            int ours = i < this.versionNumbers.size() ? this.versionNumbers.get(i) : 0;
            int theirs = i < versionToCompare.getVersionNumbers().size() ? versionToCompare.getVersionNumbers().get(i) : 0;
            if (theirs < ours) {
                return true;
            } else if (theirs > ours) {
                return false;
            }
        }
        return false;
    }

    public boolean equals(Version version) {
        if (version == null) {
            return false;
        }
        int maxLen = Math.max(this.versionNumbers.size(), version.getVersionNumbers().size());
        for (int i = 0; i < maxLen; i++) {
            int ours = i < this.versionNumbers.size() ? this.versionNumbers.get(i) : 0;
            int theirs = i < version.getVersionNumbers().size() ? version.getVersionNumbers().get(i) : 0;
            if (ours != theirs) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Version) && equals((Version) obj);
    }

    @Override
    public int hashCode() {
        return versionNumbers.hashCode();
    }

    @Override
    public String toString() {
        return version + " " + branchName + " " + branchCommit;
    }
}
