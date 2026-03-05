/*
 * Copyright (C) 2021 Radix IoT LLC. All rights reserved.
 */
package com.infiniteautomation.mango.spring.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.http.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.serotonin.db.pair.StringStringPair;
import com.serotonin.json.JsonException;
import com.serotonin.json.type.JsonValue;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.module.ModuleNotificationListener;
import com.serotonin.m2m2.module.ModuleNotificationListener.UpgradeState;

/**
 * Module upgrade management service
 *
 * @author Terry Packer
 */
@Service
public class ModulesService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final List<ModuleNotificationListener> listeners = new CopyOnWriteArrayList<>();

    private UpgradeDownloader upgradeDownloader;
    private final Object upgradeDownloaderLock = new Object();

    private final Environment env;
    private final PermissionService permissionService;

    public ModulesService(Environment env, PermissionService permissionService) {
        this.env = env;
        this.permissionService = permissionService;

        // Add our own listener
        listeners.add(new ModulesServiceListener());
    }

    /**
     * Start downloading modules — DHC: disabled, no outbound store calls
     */
    public String startDownloads(List<StringStringPair> modules, boolean backup, boolean restart) {
        permissionService.ensureAdminRole(Common.getUser());
        log.info("Module downloads disabled — SlateBAS does not contact external stores.");
        return "Module downloads are disabled in SlateBAS.";
    }

    /**
     * Try and Cancel the Upgrade
     *
     * @return true if cancelled, false if not running
     */
    public boolean tryCancelUpgrade() {
        permissionService.ensureAdminRole(Common.getUser());
        synchronized (upgradeDownloaderLock) {
            if (upgradeDownloader == null)
                return false;
            else {
                upgradeDownloader.cancel();
                return true;
            }
        }
    }

    public UpgradeStatus monitorDownloads() {
        permissionService.ensureAdminRole(Common.getUser());
        UpgradeStatus status = new UpgradeStatus();
        synchronized (upgradeDownloaderLock) {
            if (upgradeDownloader == null && stage == UpgradeState.IDLE) {
                status.setStage(stage);
                return status;
            }
            status.setFinished(finished);
            status.setCancelled(cancelled);
            status.setRestart(restart);
            status.setError(error);
            status.setStage(stage);
            status.setResults(getUpgradeResults());

            if (finished)
                stage = UpgradeState.IDLE;
        }

        return status;
    }

    /**
     * How many upgrades are available — DHC: always returns 0, no outbound calls
     */
    public int upgradesAvailable() throws Exception {
        permissionService.ensureAdminRole(Common.getUser());
        return 0;
    }

    /**
     * Get the information for available upgrades — DHC: disabled, returns null (no outbound calls)
     */
    public JsonValue getAvailableUpgrades() throws JsonException, IOException, HttpException {
        permissionService.ensureAdminRole(Common.getUser());
        log.info("Upgrade check disabled — SlateBAS does not contact external stores.");
        return null;
    }

    public void addModuleNotificationListener(ModuleNotificationListener listener) {
        permissionService.ensureAdminRole(Common.getUser());
        listeners.add(listener);
    }

    public void removeModuleNotificationListener(ModuleNotificationListener listener) {
        permissionService.ensureAdminRole(Common.getUser());
        listeners.remove(listener);
    }

    //For status about upgrade state (Preferably use your own listener)
    private volatile UpgradeState stage = UpgradeState.IDLE;
    private volatile boolean cancelled;
    private volatile boolean finished;
    private volatile boolean restart;
    private volatile String error = null;
    private final List<StringStringPair> moduleResults = Collections.synchronizedList(new ArrayList<>());

    protected void resetUpgradeStatus() {
        cancelled = false;
        finished = false;
        restart = false;
        error = null;
        moduleResults.clear();
    }

    public List<StringStringPair> getUpgradeResults() {
        permissionService.ensureAdminRole(Common.getUser());
        return new ArrayList<>(moduleResults);
    }

    private class ModulesServiceListener implements ModuleNotificationListener {

        @Override
        public void moduleDownloaded(String name, String version) {
            moduleResults.add(new StringStringPair(name, Common.translate("modules.downloadComplete")));
        }

        @Override
        public void moduleDownloadFailed(String name, String version, String reason) {
            moduleResults.add(new StringStringPair(name, reason));
        }

        @Override
        public void moduleUpgradeAvailable(String name, String version) {
            //No-op
        }

        @Override
        public void upgradeStateChanged(UpgradeState state) {
            stage = state;
            switch (stage) {
                case CANCELLED:
                    cancelled = true;
                    break;
                case RESTART:
                    restart = true;
                    break;
                default:
                    break;
            }
        }

        @Override
        public void upgradeError(String e) {
            error = e;
        }

        @Override
        public void upgradeTaskFinished() {
            finished = true;
        }

        @Override
        public void newModuleAvailable(String name, String version) {
            //no-op
        }
    }

    public static class UpgradeStatus {
        private UpgradeState stage;
        private boolean finished;
        private boolean cancelled;
        private boolean restart;
        private String error;
        private List<StringStringPair> results = new ArrayList<>();

        public UpgradeState getStage() {
            return stage;
        }

        public void setStage(UpgradeState stage) {
            this.stage = stage;
        }

        public boolean isFinished() {
            return finished;
        }

        public void setFinished(boolean finished) {
            this.finished = finished;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }

        public boolean isRestart() {
            return restart;
        }

        public void setRestart(boolean restart) {
            this.restart = restart;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public List<StringStringPair> getResults() {
            return results;
        }

        public void setResults(List<StringStringPair> results) {
            this.results = results;
        }

    }
}
