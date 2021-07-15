package com.sasha2dx.linkparser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class LinkParser {

    private final ArrayList<String> result, unhandledLinks;
    private Pattern rawLink, domain, baseDomain;
    private boolean autoSave, limitLinks, saveUnhandled, saveSorted;
    private int maxThreads, autoSaveDelay, linksLimitCount, baseDomainLevel;
    private volatile int queueIndex;
    private boolean isRun;
    private static final String ver = "0.3b";


    LinkParser() {
        System.out.println("Tentacle ver. " + ver);
        System.out.println("by Sasha2dx");
        System.out.println();

        rawLink = Pattern.compile("href=['\"]?([^\"']+)");
        domain = Pattern.compile("https?://[^/]+");

        Properties properties = new Properties();
        result = new ArrayList<>();
        unhandledLinks = new ArrayList<>();
        try {
            properties.load(new FileReader("linkParser.properties"));
            maxThreads = Integer.parseInt(properties.getProperty("maxThreads"));
            autoSaveDelay = Integer.parseInt(properties.getProperty("autosaveDelay", "0")) * 1000;
            if (autoSaveDelay > 0) autoSave = true;
            linksLimitCount = Integer.parseInt(properties.getProperty("linkLimit", "0"));
            if (linksLimitCount > 0) limitLinks = true;
            saveUnhandled = properties.getProperty("saveUnhandledElements", "").equals("true");
            saveSorted = properties.getProperty("saveSorted", "true").equals("true");
            baseDomainLevel = Integer.parseInt(properties.getProperty("baseDomainLevel", "2"));
            if (baseDomainLevel < 2) baseDomainLevel = 2;
            String startUrl = properties.getProperty("startUrl");
            result.add(startUrl);
            String startUrlDomain = getDomain(startUrl);
            if (!startUrl.equals(startUrlDomain) && !startUrl.equals(startUrlDomain + "/"))
                result.add(startUrlDomain);
        } catch (IOException e) {
            System.out.println("Error in time loading properties. Error: " + e.toString());
            System.exit(0);
        }

        baseDomain = Pattern.compile(String.format("https?://(?:[^/.]+\\.)*((?:[^./]+\\.){%d,}[^./]+)", baseDomainLevel - 1));

        isRun = true;

    }

    void run() {
        System.out.println("Print 'stop' in console to stop script immediately and save result.");
        System.out.println();

        Thread[] threads = new Thread[maxThreads];
        for (int t = 0; t < maxThreads; t++) {
            threads[t] = new Thread(this::queueCheck);
            threads[t].start();
        }
        initAutoSave();
        initStopListener();

        int unchangedCount = 0;
        int prevIterCount = 0;
        while (isRun) {
            sleep(10000);
            synchronized (result) {
                System.out.println("Progress: " + result.size() + " founded links.");
                if (limitLinks && result.size() >= linksLimitCount) {
                    System.out.println("Links limit reached script will be stopped...");
                    isRun = false;
                }
                if (result.size() != prevIterCount) {
                    prevIterCount = result.size();
                    unchangedCount = 0;
                } else if (unchangedCount == 3) {
                    System.out.println("Looks like all posible links are founded. Script will be stopped...");
                    isRun = false;
                } else unchangedCount++;
            }
        }

        System.out.println("Saving result before closing...");
        saveAll();

        System.exit(0);
    }

    private void initStopListener() {
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (isRun) {
                sleep(1000);
                if (scanner.hasNextLine() && scanner.nextLine().equals("stop")) {
                    System.out.println("Script will be stopped...");
                    isRun = false;
                    scanner.close();
                }
            }
        }).start();

    }

    private void initAutoSave() {
        if (!autoSave) return;
        new Thread(() -> {
            sleep(autoSaveDelay);
            while (isRun) {
                System.out.println();
                System.out.println("Autosave.");
                saveAll();
                System.out.println();
                sleep(autoSaveDelay);
            }
        }).start();
    }

    private void sleep(int delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void saveAll() {
        try (FileWriter fileWriter = new FileWriter(new File("links.txt"))) {
            ArrayList<String> forSaving;

            synchronized (result) {
                forSaving = new ArrayList<>(result);
                if (saveSorted) {
                    forSaving.sort(String::compareToIgnoreCase);

                }
            }

            for (String s : forSaving) {
                fileWriter.write(s + "\n");
            }

            fileWriter.flush();
            System.out.println("Saved " + result.size() + " links to links.txt.");
        } catch (IOException e) {
            System.out.println("Error in time saving progress to links.txt.");
        }
        if (!saveUnhandled) return;

        try (FileWriter fileWriter = new FileWriter(new File("unhandledLinks.txt"))) {
            synchronized (unhandledLinks) {
                for (String s : unhandledLinks) {
                    fileWriter.write(s + "\n");
                }

                fileWriter.flush();
                System.out.println("Saved " + unhandledLinks.size() + " links to unhandledLinks.txt.");
            }
        } catch (IOException e) {
            System.out.println("Error in time saving progress to unhandledLinks.txt.");
        }

    }

    private void queueCheck() {
        while (isRun) {
            String link = null;
            boolean hasNewLinkToCheck = true;

            synchronized (result) {
                if (result.size() == queueIndex || (limitLinks && result.size() >= linksLimitCount)) {
                    hasNewLinkToCheck = false;
                } else {
                    link = result.get(queueIndex);
                    queueIndex++;
                }
            }

            if (!hasNewLinkToCheck) {
                sleep(1000);

            } else {

                getLinks(link);
            }

        }
    }

    private void getLinks(String url) {
        Matcher matcher = rawLink.matcher(ParseUtils.getPage(url));
        while (matcher.find()) {
            String rawLink = matcher.group(1);
            if (rawLink.startsWith("/")) {
                addNewLinkToQueue(getDomain(url) + rawLink);
                continue;
            }
            if (rawLink.startsWith("http") && equalsBaseDomain(url, rawLink)) {
                addNewLinkToQueue(rawLink);
                continue;
            }
            if (saveUnhandled) {
                addNewUnhandledEl(rawLink);
            }
        }
    }

    private void addNewLinkToQueue(String link) {
        synchronized (result) {
            if (result.contains(link)) return;
            result.add(link);
        }
    }

    private void addNewUnhandledEl(String link) {
        synchronized (unhandledLinks) {
            if (unhandledLinks.contains(link)) return;
            unhandledLinks.add(link);
        }
    }

    private String getDomain(String url) {
        Matcher matcher = domain.matcher(url);
        if (matcher.find())
            return matcher.group(0);
        return "";
    }

    private boolean equalsBaseDomain(String url1, String url2) {
        return getBaseDomain(url1).equals(getBaseDomain(url2));
    }

    private String getBaseDomain(String url) {
        Matcher matcher = baseDomain.matcher(url);
        if (matcher.find())
            return matcher.group(1);
        return "";
    }

    public static void main(String[] args) {
//        System.out.println(new LinkParser().getBaseDomain("https://plitkar.com.ua"));

        for (char ch = 'a'; ch <= 'z'; ch++) {
            System.out.println("@replace(\""+ch+"\", \""+(""+ch).toUpperCase()+"\"): $headers//text()");
        }

    }


}
