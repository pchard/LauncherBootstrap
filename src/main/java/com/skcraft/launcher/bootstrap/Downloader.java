/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.bootstrap;

import com.skcraft.launcher.Bootstrap;
import lombok.extern.java.Log;

import javax.swing.*;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static com.skcraft.launcher.bootstrap.BootstrapUtils.checkInterrupted;

@Log
public class Downloader implements Runnable, ProgressObservable {

    private final Bootstrap bootstrap;
    private DownloadFrame dialog;
    private HttpRequest httpRequest;
    private Thread thread;

    public Downloader(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public void run() {
        this.thread = Thread.currentThread();

        try {
            execute();
        } catch (InterruptedException e) {
            log.log(Level.WARNING, "Interrupted");
            System.exit(0);
        } catch (Throwable t) {
            log.log(Level.WARNING, "Failed to download launcher", t);
            SwingHelper.showErrorDialog(null, "The SKCraft launcher could not be downloaded.", "Error", t);
            System.exit(0);
        }
    }

    private void execute() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                Bootstrap.setSwingLookAndFeel();
                dialog = new DownloadFrame(Downloader.this);
                dialog.setVisible(true);
                dialog.setDownloader(Downloader.this);
            }
        });

        File finalFile = new File(bootstrap.getBinariesDir(), System.currentTimeMillis() + ".jar.pack");
        File tempFile = new File(finalFile.getParentFile(), finalFile.getName() + ".tmp");

        try {
            String rawUrl = HttpRequest
                    .get(HttpRequest.url(bootstrap.getProperties().getProperty("latestUrl")))
                    .execute()
                    .expectResponseCode(200)
                    .returnContent()
                    .asString("UTF-8");

            URL url = HttpRequest.url(rawUrl.trim());

            checkInterrupted();

            log.info("Downloading " + url + " to " + tempFile.getAbsolutePath());

            httpRequest = HttpRequest.get(url);
            httpRequest
                    .execute()
                    .expectResponseCode(200)
                    .saveContent(tempFile);

            finalFile.delete();
            tempFile.renameTo(finalFile);
        } finally {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    dialog.setDownloader(null);
                    dialog.dispose();
                }
            });
        }

        LauncherBinary binary = new LauncherBinary(finalFile);
        List<LauncherBinary> binaries = new ArrayList<LauncherBinary>();
        binaries.add(binary);
        bootstrap.launchExisting(binaries, false);
    }

    public void cancel() {
        thread.interrupt();
    }

    public String getStatus() {
        HttpRequest httpRequest = this.httpRequest;
        if (httpRequest != null) {
            double progress = httpRequest.getProgress();
            if (progress >= 0) {
                return String.format("Downloading latest SKCraft Launcher (%.2f%%)...", progress * 100);
            }
        }

        return "Downloading latest SKCraft Launcher...";
    }

    @Override
    public double getProgress() {
        HttpRequest httpRequest = this.httpRequest;
        return httpRequest != null ? httpRequest.getProgress() : -1;
    }
}