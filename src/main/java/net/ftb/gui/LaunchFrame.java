/*
 * This file is part of FTB Launcher.
 *
 * Copyright © 2012-2014, FTB Launcher Contributors <https://github.com/Slowpoke101/FTBLaunch/>
 * FTB Launcher is licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ftb.gui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.google.common.eventbus.Subscribe;

import lombok.Getter;
import lombok.Setter;
import net.ftb.data.Constants;
import net.ftb.data.LauncherStyle;
import net.ftb.data.LoginResponse;
import net.ftb.data.Map;
import net.ftb.data.ModPack;
import net.ftb.data.Settings;
import net.ftb.data.UserManager;
import net.ftb.download.Locations;
import net.ftb.events.EnableObjectsEvent;
import net.ftb.gui.dialogs.LoadingDialog;
import net.ftb.gui.dialogs.ModPackVersionChangeDialog;
import net.ftb.gui.dialogs.PasswordDialog;
import net.ftb.gui.dialogs.PlayOfflineDialog;
import net.ftb.gui.panes.*;
import net.ftb.locale.I18N;
import net.ftb.locale.I18N.Locale;
import net.ftb.log.Logger;
import net.ftb.main.Main;
import net.ftb.minecraft.MCInstaller;
import net.ftb.tools.MapManager;
import net.ftb.tools.ModManager;
import net.ftb.tools.ProcessMonitor;
import net.ftb.tools.TextureManager;
import net.ftb.util.*;
import net.ftb.util.OSUtils.OS;
import net.ftb.util.winreg.JavaInfo;
import net.ftb.workers.LoginWorker;
import net.ftb.workers.UnreadNewsWorker;

@SuppressWarnings("serial")
public class LaunchFrame extends JFrame {
    public static boolean bypasslaunch;
    public static boolean forcedl;

    private LoginResponse RESPONSE;
    private NewsPane newsPane;
    public static JPanel panel;
    private JPanel footer = new JPanel();
    private JLabel footerLogo = new JLabel(new ImageIcon(this.getClass().getResource(Locations.FTBLOGO)));
    private JLabel footerCreeper = new JLabel(new ImageIcon(this.getClass().getResource(Locations.CHLOGO)));
    private JLabel footerCurse = new JLabel(new ImageIcon(this.getClass().getResource(Locations.CURSELOGO)));
    private JLabel tpInstallLocLbl = new JLabel();
    @Getter
    private final JButton launch = new JButton(), mapInstall = new JButton(), serverMap = new JButton(),
            tpInstall = new JButton();

    private static String[] dropdown_ = { "Select Profile", "Create Profile" };
    private static JComboBox tpInstallLocation, mapInstallLocation;
    /**
     * @return - Outputs LaunchFrame instance
     */
    @Getter
    @Setter
    private static LaunchFrame instance = null;
    
    public static boolean canUseAuthlib;
    public static int minUsable = -1;
    public final JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);

    public FTBPacksPane modPacksPane;
    public ThirdPartyPane thirdPartyPane;
    public MapUtils mapsPane;
    public TexturepackPane tpPane;
    public OptionsPane optionsPane;
    
    public static TrayMenu trayMenu;

    public static boolean allowVersionChange = false;
    public static boolean doVersionBackup = false;
    public static boolean MCRunning = false;
    public static LauncherConsole con;
    public static String tempPass = "";
    public static Panes currentPane = Panes.MODPACK;
    public static LoadingDialog loader;


    @Getter
    @Setter
    private static ProcessMonitor procMonitor;

    public enum Panes {
        NEWS, OPTIONS, MODPACK, THIRDPARTY, TEXTURE
    }

    private boolean tpEnabled = true;

    /**
     * Create the frame.
     */
    public LaunchFrame(final int tab) {
        setFont(new Font("a_FuturaOrto", Font.PLAIN, 12));
        setResizable(false);
        setTitle(Constants.name + " v" + Constants.version);
        setIconImage(Toolkit.getDefaultToolkit().getImage(this.getClass().getResource("/image/logo_ftb.png")));

        panel = new JPanel();

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        if (OSUtils.getCurrentOS() == OS.WINDOWS) {
            setBounds(100, 100, 842, 480);
        } else {
            setBounds(100, 100, 850, 480);
        }
        panel.setBounds(0, 0, 850, 480);
        panel.setLayout(null);
        footer.setBounds(0, 380, 850, 100);
        footer.setLayout(null);
        footer.setBackground(LauncherStyle.getCurrentStyle().footerColor);
        tabbedPane.setBounds(0, 0, 850, 380);
        panel.add(tabbedPane);
        panel.add(footer);
        setContentPane(panel);

        //Footer
        footerLogo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        footerLogo.setBounds(20, 20, 42, 42);
        footerLogo.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked (MouseEvent event) {
                OSUtils.browse(Locations.FTBSITE);
            }
        });

        footerCreeper.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        footerCreeper.setBounds(72, 20, 132, 42);
        footerCreeper.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked (MouseEvent event) {
                OSUtils.browse("http://billing.creeperhost.net/link.php?id=2");
            }
        });

        dropdown_[0] = I18N.getLocaleString("PROFILE_SELECT");
        dropdown_[1] = I18N.getLocaleString("PROFILE_CREATE");

        ArrayList<String> var = UserManager.getUsernames();
        String[] dropdown = ObjectUtils.concatenateArrays(dropdown_, var.toArray(new String[var.size()]));

        launch.setText(I18N.getLocaleString("LAUNCH_BUTTON"));
        launch.setEnabled(false);
        launch.setBounds(711, 20, 100, 30);
        launch.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed (ActionEvent arg0) {
                doLaunch();
            }
        });

        mapInstall.setBounds(650, 20, 160, 30);
        mapInstall.setText(I18N.getLocaleString("INSTALL_MAP"));
        mapInstall.setVisible(false);
        mapInstall.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed (ActionEvent arg0) {
                if (mapsPane.mapPanels.size() > 0 && getSelectedMapIndex() >= 0) {
                    MapManager man = new MapManager(new JFrame(), true);
                    man.setVisible(true);
                    MapManager.cleanUp();
                }
            }
        });

        mapInstallLocation = new JComboBox();
        mapInstallLocation.setBounds(480, 20, 160, 30);
        mapInstallLocation.setToolTipText("Install to...");
        mapInstallLocation.setVisible(false);

        serverMap.setBounds(480, 20, 330, 30);
        serverMap.setText(I18N.getLocaleString("DOWNLOAD_MAP_SERVER"));
        serverMap.setVisible(false);
        serverMap.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed (ActionEvent event) {
                if (mapsPane.mapPanels.size() > 0 && getSelectedMapIndex() >= 0) {
                    OSUtils.browse(DownloadUtils.getCreeperhostLink("maps%5E" + Map.getMap(LaunchFrame.getSelectedMapIndex()).getMapName() + "%5E"
                            + Map.getMap(LaunchFrame.getSelectedMapIndex()).getVersion() + "%5E" + Map.getMap(LaunchFrame.getSelectedMapIndex()).getUrl()));
                }
            }
        });

        tpInstall.setBounds(650, 20, 160, 30);
        tpInstall.setText(I18N.getLocaleString("INSTALL_TEXTUREPACK"));
        tpInstall.setVisible(false);
        tpInstall.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed (ActionEvent arg0) {
                if (tpPane.texturePackPanels.size() > 0 && getSelectedTexturePackIndex() >= 0) {
                    TextureManager man = new TextureManager(new JFrame(), true);
                    man.setVisible(true);
                }
            }
        });

        tpInstallLocation = new JComboBox();
        tpInstallLocation.setBounds(480, 20, 160, 30);
        tpInstallLocation.setToolTipText("Install to...");
        tpInstallLocation.setVisible(false);

        tpInstallLocLbl.setText("Install to...");
        tpInstallLocLbl.setBounds(480, 20, 80, 30);
        tpInstallLocLbl.setVisible(false);

        footer.add(footerLogo);
        footer.add(footerCreeper);
        footer.add(footerCurse);
        footer.add(launch);
        footer.add(mapInstall);
        footer.add(mapInstallLocation);
        footer.add(serverMap);
        footer.add(tpInstall);
        footer.add(tpInstallLocation);

        newsPane = new NewsPane();
        modPacksPane = new FTBPacksPane();
        thirdPartyPane = new ThirdPartyPane();
        mapsPane = new MapUtils();
        tpPane = new TexturepackPane();
        optionsPane = new OptionsPane(Settings.getSettings());

        getRootPane().setDefaultButton(launch);
        updateLocale();

        tabbedPane.add(newsPane, 0);
        tabbedPane.add(optionsPane, 1);
        tabbedPane.add(modPacksPane, 2);
        tabbedPane.add(thirdPartyPane, 3);
        tabbedPane.add(tpPane, 4);
        /*
         * TODO: This will block. Network.
         */
        setNewsIcon();
        try {
            tabbedPane.setIconAt(1, LauncherStyle.getCurrentStyle().filterHeaderIcon(this.getClass().getResource("/image/tabs/options.png")));
            tabbedPane.setIconAt(2, LauncherStyle.getCurrentStyle().filterHeaderIcon(this.getClass().getResource("/image/tabs/ftbpacks.png")));
            tabbedPane.setIconAt(3, LauncherStyle.getCurrentStyle().filterHeaderIcon(this.getClass().getResource("/image/tabs/thirdpartypacks.png")));
            tabbedPane.setIconAt(4, LauncherStyle.getCurrentStyle().filterHeaderIcon(this.getClass().getResource("/image/tabs/mapstextures.png")));
        } catch (Exception e1) {
            Logger.logError("error changing colors", e1);
        }
        // this will be fired when
        // * tab is clicked
        // * swapTabs(): tabbedPane.setSelectedIndex() is invoked and when tab is clicked
        // Is that true?!
        tabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged (ChangeEvent event) {
                if (tabbedPane.getSelectedComponent() instanceof ILauncherPane) {
                    ((ILauncherPane) tabbedPane.getSelectedComponent()).onVisible();
                    // When called by swapTabs currentPane will be THIRDPARTY until this ActionListener is called again
                    currentPane = Panes.values()[tabbedPane.getSelectedIndex()];
                    updateFooter();
                }
            }
        });
        tabbedPane.setSelectedIndex(tab);
    }

    public static void checkDoneLoading () {
        SwingUtilities.invokeLater(new Runnable() {
            public void run () {
                if (FTBPacksPane.getInstance().loaded) {
                    LoadingDialog.setProgress(190);
                    if (MapUtils.loaded) {
                        LoadingDialog.setProgress(200);
                        if (TexturepackPane.loaded) {
                            loader.setVisible(false);
                            instance.setVisible(true);
                            instance.toFront();
                            Benchmark.logBenchAs("main", "Launcher Startup");
                            if (LaunchFrame.forcedl)
                                forcedl();
                        }
                    }
                }
            }
        });
    }

    public void setNewsIcon () {
        /* Call unreadNews swingworker
         * done() will set news tab icon
         */
        UnreadNewsWorker unreadNews = new UnreadNewsWorker() {
            @Override
            protected void done () {
                try {
                    int i = get();
                    if (i > 0 && i < 100) {
                        ImageAndTextIcon iti = new ImageAndTextIcon(this.getClass().getResource("/image/tabs/news_unread_" + Integer.toString(i).length() + ".png"), Integer.toString(i));
                        iti.setImage(LauncherStyle.getCurrentStyle().filterHeaderIcon(this.getClass().getResource("/image/tabs/news_unread_" + Integer.toString(i).length() + ".png")).getImage());
                        LaunchFrame.getInstance().tabbedPane.setIconAt(0, iti);
                    } else {
                        LaunchFrame.getInstance().tabbedPane.setIconAt(0, LauncherStyle.getCurrentStyle().filterHeaderIcon(this.getClass().getResource("/image/tabs/news.png")));
                    }
                } catch (InterruptedException e) {
                } catch (ExecutionException e) {
                }
            }
        };
        unreadNews.execute();
    }

    /**
     * call this to login
     */
    private void doLogin (final String username, String password, String mojangData, String selectedProfile) {
        if (ModPack.getSelectedPack().getDisclaimer() != null && !ModPack.getSelectedPack().getDisclaimer().isEmpty()) {
            ErrorUtils.tossError(ModPack.getSelectedPack().getDisclaimer());
        }
        if ((mojangData == null || mojangData.isEmpty()) && password.isEmpty()) {
            PasswordDialog p = new PasswordDialog(this, true);
            p.setVisible(true);
            if (tempPass.isEmpty()) {
                enableObjects();
                return;
            }
            password = tempPass;
        }

        Logger.logInfo("Logging in...");

        tabbedPane.setEnabledAt(0, false);
        tabbedPane.setIconAt(0, LauncherStyle.getCurrentStyle().filterHeaderIcon(this.getClass().getResource("/image/tabs/news.png")));
        tabbedPane.setEnabledAt(1, false);
        tabbedPane.setEnabledAt(2, false);
        tabbedPane.setEnabledAt(3, false);
        tabbedPane.setEnabledAt(4, false);
        tabbedPane.getSelectedComponent().setEnabled(false);

        launch.setEnabled(false);
        mapInstall.setEnabled(false);
        mapInstallLocation.setEnabled(false);
        serverMap.setEnabled(false);
        tpInstall.setEnabled(false);
        tpInstallLocation.setEnabled(false);

        LoginWorker loginWorker = new LoginWorker(username, password, mojangData, selectedProfile) {
            @Override
            public void done () {
                String responseStr;
                try {
                    responseStr = get();
                } catch (InterruptedException err) {
                    Logger.logError("User cancelled login process", err);
                    enableObjects();
                    return;
                } catch (ExecutionException err) {
                    // Worker should not leak ExecutionExceptions to caller: all Exceptions are handled internally twice
                    if (err.getCause() instanceof IOException) {
                        Logger.logError("Error while logging in", err);
                        PlayOfflineDialog d = new PlayOfflineDialog("mcDown", username, UserManager.getUUID(username), getResp());
                        d.setVisible(true);
                    }
                    enableObjects();
                    return;
                }

                RESPONSE = getResp();
                Logger.logDebug("responseStr: " + responseStr);
                String uuid = UserManager.getUUID(username);
                if (responseStr.equals("good")) {
                    Logger.logInfo("Login complete.");
                    try {
                        // save userdata, including new mojangData
                        Main.getUserManager().write();
                        Logger.logDebug("user data saved");
                    } catch (IOException e) {
                        Logger.logError("logindata saving failed!");
                    }
                    runGameUpdater();
                } else if (uuid != null && !uuid.isEmpty() && RESPONSE != null && responseStr.equals("offline")) {
                    Logger.logDebug("Asking user for offline mode");
                    PlayOfflineDialog d = new PlayOfflineDialog("mcDown", username, uuid, RESPONSE);
                    d.setVisible(true);
                } else {
                    Logger.logDebug("Bad responseStr, not starting MC");
                    enableObjects();
                    return;
                }//if user doesn't want offline mode
                enableObjects();
            }
        };
        loginWorker.execute();
    }

    private Boolean checkVersion (File verFile, ModPack pack) {
        String storedVersion = pack.getStoredVersion(verFile);
        String onlineVersion = (Settings.getSettings().getPackVer().equalsIgnoreCase("recommended version") || Settings.getSettings().getPackVer().equalsIgnoreCase("newest version")) ? pack
                .getVersion() : Settings.getSettings().getPackVer();

        if (storedVersion.isEmpty()) {
            // Always allow updates from a version that isn't installed at all
            allowVersionChange = true;
            return true;
        } else if (Integer.parseInt(storedVersion.replace(".", "")) != Integer.parseInt(onlineVersion.replace(".", ""))) {
            ModPackVersionChangeDialog verDialog = new ModPackVersionChangeDialog(this, true, storedVersion, onlineVersion);
            verDialog.setVisible(true);
        }
        return allowVersionChange & (!storedVersion.equals(onlineVersion));
    }

    /**
     * checks whether an update is needed, and then starts the update process off
     */
    private void runGameUpdater () {

        final String installPath = Settings.getSettings().getInstallPath();
        final ModPack pack = ModPack.getSelectedPack();

        Logger.logDebug("ForceUpdate: " + Settings.getSettings().isForceUpdateEnabled());
        Logger.logDebug("installPath: " + installPath);
        Logger.logDebug("pack dir: " + pack.getDir());
        Logger.logDebug("pack check path: " + pack.getDir() + File.separator + "version");

        File verFile = new File(installPath, pack.getDir() + File.separator + "version");

        if (Settings.getSettings().isForceUpdateEnabled() && verFile.exists()) {
            verFile.delete();
            Logger.logDebug("Pack found and delete attempted");
        }

        if (Settings.getSettings().isForceUpdateEnabled() || !verFile.exists() || checkVersion(verFile, pack)) {
            if (doVersionBackup) {
                try {
                    File destination = new File(OSUtils.getCacheStorageLocation(), "backups" + File.separator + pack.getDir() + File.separator + "config_backup");
                    if (destination.exists()) {
                        FTBFileUtils.delete(destination);
                    }
                    FTBFileUtils.copyFolder(new File(Settings.getSettings().getInstallPath(), pack.getDir() + File.separator + "minecraft" + File.separator + "config"), destination);
                } catch (IOException e) {
                    Logger.logError("Error while doing backups", e);
                }
            }

            if (!initializeMods()) {
                Logger.logDebug("initializeMods: Failed to Init mods! Aborting to menu.");
                enableObjects();
                return;
            }
        }

        try {
            TextureManager.updateTextures();
        } catch (Exception e1) {
        }
        boolean isLegacy = true;
        if (pack.getMcVersion().startsWith("1.6") || pack.getMcVersion().startsWith("1.7") || pack.getMcVersion().startsWith("1.8") || pack.getMcVersion().startsWith("14w")) {
            isLegacy = false;
        }
        MCInstaller.setupNewStyle(installPath, pack, isLegacy, RESPONSE);
    }


    /**
     * "Saves" the settings from the GUI controls into the settings class.
     */
    public void saveSettings () {
        instance.optionsPane.saveSettingsInto(Settings.getSettings());
    }

    /**
     * @param user - user added/edited
     */
    public static void writeUsers (String user) {
    }

    /**
     * updates the tpInstall to the available ones
     * @param locations - the available locations to install the tp to
     */
    public static void updateTpInstallLocs (List<String> locations) {
        tpInstallLocation.removeAllItems();
        for (String location : locations) {
            if (location != null && !location.isEmpty()) {
                tpInstallLocation.addItem(ModPack.getPack(location.trim()).getName());
            }
        }
        //TODO:
        // Decide later if we want to do this? How to handle selection from two modpack panes?
        tpInstallLocation.setSelectedItem(ModPack.getSelectedPack(true).getName());
    }

    /**
     * updates the mapInstall to the available ones
     * @param locations - the available locations to install the map to
     */
    public static void updateMapInstallLocs (String[] locations) {
        mapInstallLocation.removeAllItems();
        for (String location : locations) {
            if (location != null && !location.isEmpty()) {
                mapInstallLocation.addItem(ModPack.getPack(location.trim()).getName());
            }
        }
    }

    /**
     * @return - Outputs selected map index
     */
    public static int getSelectedMapIndex () {
        return instance.mapsPane.getSelectedMapIndex();
    }

    /**
     * @return - Outputs selected texturepack index
     */
    public static int getSelectedTexturePackIndex () {
        return instance.tpPane.getSelectedTexturePackIndex();
    }

    /**
     * @return - Outputs selected map install index
     */
    public static int getSelectedMapInstallIndex () {
        return mapInstallLocation.getSelectedIndex();
    }

    /**
     * @return - Outputs selected texturepack install index
     */
    public static int getSelectedTPInstallIndex () {
        return tpInstallLocation.getSelectedIndex();
    }

    /**
     * Enables all items that are disabled upon launching
     */
    private void enableObjects () {
        tabbedPane.setEnabledAt(0, true);
        setNewsIcon();
        tabbedPane.setEnabledAt(1, true);
        tabbedPane.setEnabledAt(2, true);
        tabbedPane.setEnabledAt(3, true);
        tabbedPane.setEnabledAt(4, true);
        tabbedPane.getSelectedComponent().setEnabled(true);
        updateFooter();
        mapInstall.setEnabled(true);
        mapInstallLocation.setEnabled(true);
        serverMap.setEnabled(true);
        tpInstall.setEnabled(true);
        launch.setEnabled(true);
        tpInstallLocation.setEnabled(true);
        TextureManager.updating = false;
    }
    @Subscribe
    private void handleEnableObjectsEvent(EnableObjectsEvent e){
        enableObjects();
    }
    /**
     * Download and install mods
     * @return boolean - represents whether it was successful in initializing mods
     */
    private boolean initializeMods () {
        Logger.logDebug("pack dir...");
        Logger.logInfo(ModPack.getSelectedPack().getDir());
        ModManager man = new ModManager(new JFrame(), true);
        man.setVisible(true);
        while (man == null) {
        }
        while (!man.worker.isDone()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) { }
        }
        if (man.erroneous) {
            return false;
        }
        try {
            MCInstaller.installMods(ModPack.getSelectedPack().getDir());
            man.cleanUp();
        } catch (IOException e) {
            Logger.logDebug("Exception: ", e);
        }
        return true;
    }

    /**
     * disables the buttons that are usually active on the footer
     */
    public void disableMainButtons () {
        launch.setVisible(false);
    }

    /**
     * disables the footer buttons active when the modpack tab is selected
     */
    public void disableMapButtons () {
        mapInstall.setVisible(false);
        mapInstallLocation.setVisible(false);
        serverMap.setVisible(false);
    }

    /**
     * disables the footer buttons active when the texture pack tab is selected
     */
    public void disableTextureButtons () {
        tpInstall.setVisible(false);
        tpInstallLocation.setVisible(false);
    }

    /**
     * update the footer to the correct buttons for active tab
     */
    public void updateFooter () {
        boolean result;
        switch (currentPane) {
        case TEXTURE:
            if (tpEnabled) {
                tpInstall.setVisible(true);
                tpInstallLocation.setVisible(true);
                disableMainButtons();
                disableMapButtons();
            } else {
                result = mapsPane.type.equals("Server");
                mapInstall.setVisible(!result);
                mapInstallLocation.setVisible(!result);
                serverMap.setVisible(result);
                disableMainButtons();
                disableTextureButtons();

            }
            break;
        default:
            launch.setVisible(true);
            disableMapButtons();
            disableTextureButtons();
            break;
        }
    }

    // TODO: Make buttons dynamically sized.
    /**
     * updates the buttons/text to language specific
     */
    public void updateLocale () {
        if (I18N.currentLocale == Locale.deDE) {
            mapInstall.setBounds(620, 20, 190, 30);
            mapInstallLocation.setBounds(420, 20, 190, 30);
            tpInstallLocation.setBounds(420, 20, 190, 30);
            tpInstall.setBounds(620, 20, 190, 30);
        } else {
            mapInstall.setBounds(650, 20, 160, 30);
            mapInstallLocation.setBounds(480, 20, 160, 30);
            tpInstallLocation.setBounds(480, 20, 160, 30);
            tpInstall.setBounds(650, 20, 160, 30);
        }
        footerCurse.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                footerCurse.setBounds(72 + 123 + 15, 15, 132, 42);
                footerCurse.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked (MouseEvent event) {
                }
        });
        launch.setText(I18N.getLocaleString("LAUNCH_BUTTON"));
        mapInstall.setText(I18N.getLocaleString("INSTALL_MAP"));
        serverMap.setText(I18N.getLocaleString("DOWNLOAD_MAP_SERVER"));
        tpInstall.setText(I18N.getLocaleString("INSTALL_TEXTUREPACK"));
        dropdown_[0] = I18N.getLocaleString("PROFILE_SELECT");
        dropdown_[1] = I18N.getLocaleString("PROFILE_CREATE");
        optionsPane.updateLocale();
        modPacksPane.updateLocale();
        thirdPartyPane.updateLocale();
        mapsPane.updateLocale();
        tpPane.updateLocale();
        if(trayMenu != null) {
        	trayMenu.updateLocale();
        }
    }

    public void doLaunch () {
        JavaInfo java = Settings.getSettings().getCurrentJava();
        int[] minSup = ModPack.getSelectedPack().getMinJRE();
        if (ModPack.getSelectedPack().getMinLaunchSpec() <= Constants.buildNumber) {
            if (ModPack.getSelectedPack() != null) {
                if (minSup.length >= 2 && minSup[0] <= java.getMajor() && minSup[1] <= java.getMinor()) {
                    Settings.getSettings().setLastFTBPack(ModPack.getSelectedPack(true).getDir());
                    Settings.getSettings().setLastThirdPartyPack(ModPack.getSelectedPack(false).getDir());
                    saveSettings();
                        Main.getUserManager().read();
                        if (UserManager._users.size() >= 1 && ModPack.getSelectedPack() != null)
                            doLogin(UserManager.getUsernames().get(0), UserManager.getPassword(UserManager.getUsernames().get(0)), null, UserManager.getUsernames().get(0));
                        else {
                            Logger.logError("no users to login w/");
                        }
                            //UserManager.getMojangData(users.getSelectedItem().toString()), UserManager.getName(users.getSelectedItem().toString()));
                } else {//user can't run pack-- JRE not high enough
                    ErrorUtils.tossError("You must use at least java " + minSup[0] + "." + minSup[1] + " to play this pack! Please go to Options to get a link or Advanced Options enter a path.", java.toString());
                }
            }
        } else {
            ErrorUtils.tossError("Please update your launcher in order to launch this pack! This can be done by restarting your launcher, an update dialog will pop up.");
        }
    }

    public void swapTabs (boolean toMaps) {
        if (toMaps) {
            tabbedPane.remove(4);
            tabbedPane.add(mapsPane, 4);
            tabbedPane.setIconAt(4, LauncherStyle.getCurrentStyle().filterHeaderIcon(this.getClass().getResource("/image/tabs/mapstextures.png")));
            tabbedPane.setSelectedIndex(4);
            tpEnabled = false;
            updateFooter();
        } else {
            tabbedPane.remove(4);
            tabbedPane.add(tpPane, 4);
            tabbedPane.setIconAt(4, LauncherStyle.getCurrentStyle().filterHeaderIcon(this.getClass().getResource("/image/tabs/mapstextures.png")));
            tabbedPane.setSelectedIndex(4);
            tpEnabled = true;
            updateFooter();
        }
    }

    public static void setUpSystemTray() {
    	trayMenu = new TrayMenu();
    	
    	SystemTray tray = SystemTray.getSystemTray();
    	TrayIcon trayIcon = new TrayIcon(Toolkit.getDefaultToolkit().getImage(instance.getClass().getResource("/image/logo_ftb.png")));
    	
    	trayIcon.setPopupMenu(trayMenu);
    	trayIcon.setToolTip(Constants.name);
    	trayIcon.setImageAutoSize(true);
    	
    	try {
			tray.add(trayIcon);
		} catch (AWTException e) {
			e.printStackTrace();
		}
    }
    public static void forcedl () {
        if (ModPack.getSelectedPack() != null) {
            bypasslaunch = true;//TODO FIX THIS \/
           // instance.runGameUpdater(new LoginResponse("A:1:token:progwml6:b6e3d27b070240cda22adb9f61077027:83898b2861184900913741ffc46b6e10"));
        }
    }

    public static void main(String args[]) {
        Main.main(args);// just in case someone is launching w/ this as the main class
    }
}
