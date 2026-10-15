package keystrokesmod.client.utils.version;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;

public class VersionManager {
	private final String versionFilePath = "/assets/keystrokesmod/version";
	private final String branchFilePath = "/assets/keystrokesmod/branch";
    private final String versionUrl = "https://raw.githubusercontent.com/K-ov/Raven-bPLUS/stable/src/main/resources/assets/keystrokesmod/version";
    private final String branchUrl = "https://raw.githubusercontent.com/K-ov/Raven-bPLUS/stable/src/main/resources/assets/keystrokesmod/branch";

    private Version latestVersion;
    private Version clientVersion;

    public VersionManager() {
        createClientVersion();
        createLatestVersion();
    }

    private void createLatestVersion() {
        String version = "1.3.0";
        String branch = "";
        int branchCommit = 0;

        try {
            URL url = new URL(versionUrl);
            java.net.URLConnection conn = url.openConnection();
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                 Scanner scanner = new Scanner(bufferedReader)) {
                if (scanner.hasNextLine()) {
                    version = scanner.nextLine().trim();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            URL url = new URL(branchUrl);
            java.net.URLConnection conn = url.openConnection();
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                 Scanner scanner = new Scanner(bufferedReader)) {
                if (scanner.hasNextLine()) {
                    String[] line = scanner.nextLine().trim().split("-");
                    branch = line[0];
                    if (line.length > 1) {
                        branchCommit = Integer.parseInt(line[1]);
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }

        this.latestVersion = new Version(version, branch, branchCommit);
    }

    private void createClientVersion() {
        String version = "1.3.0";
        String branch = "";
        int branchCommit = 0;

        try (InputStream input = VersionManager.class.getResourceAsStream(versionFilePath)) {
            if (input != null) {
                try (Scanner scanner = new Scanner(input)) {
                    if (scanner.hasNextLine()) {
                        version = scanner.nextLine().trim();
                    }
                }
            }
        } catch (IOException ignored) {
        }

        try (InputStream input = VersionManager.class.getResourceAsStream(branchFilePath)) {
            if (input != null) {
                try (Scanner scanner = new Scanner(input)) {
                    if (scanner.hasNextLine()) {
                        String[] line = scanner.nextLine().trim().split("-");
                        branch = line[0];
                        if (line.length > 1) {
                            branchCommit = Integer.parseInt(line[1]);
                        }
                    }
                }
            }
        } catch (IOException | NumberFormatException ignored) {
        }

        this.clientVersion = new Version(version, branch, branchCommit);
    }

    public Version getClientVersion() {
        return clientVersion;
    }

    public Version getLatestVersion() {
        return latestVersion;
    }
}
