package com.faforever.moderatorclient.ui.main_window;

import com.faforever.commons.api.dto.GroupPermission;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.domain.MapService;
import com.faforever.moderatorclient.api.domain.UserService;
import com.faforever.moderatorclient.ui.*;
import com.faforever.moderatorclient.ui.domain.BanInfoFX;
import com.faforever.moderatorclient.ui.domain.MapVersionFX;
import com.faforever.moderatorclient.ui.domain.PlayerFX;
import com.faforever.moderatorclient.ui.domain.TeamkillFX;
import com.faforever.moderatorclient.ui.domain.UniqueIdFx;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import static com.faforever.moderatorclient.ui.MainController.CONFIGURATION_FOLDER;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecentActivityController implements Controller<VBox> {
    private final UserService userService;
    private final MapService mapService;
    private final ObservableList<PlayerFX> users = FXCollections.observableArrayList();
    private final ObservableList<TeamkillFX> teamkills = FXCollections.observableArrayList();
    private final ObservableList<MapVersionFX> mapVersions = FXCollections.observableArrayList();
    private final FafApiCommunicationService communicationService;
    private final UiService uiService;
    private final PlatformService platformService;

    public VBox root;
    public TextArea suspiciousUserTextArea;

    public TitledPane userRegistrationFeedPane;
    public TitledPane teamkillFeedPane;
    public TitledPane mapUploadFeedPane;

    public TableView<PlayerFX> userRegistrationFeedTableView;
    public Text excludedItemsText;
    public TableView<TeamkillFX> teamkillFeedTableView;
    public TableView<MapVersionFX> mapUploadFeedTableView;
    public CheckBox includeGlobalBannedUserCheckBox;

    @Override public VBox getRoot() {return root;}

    private boolean checkPermissionForTitledPane(String permissionTechnicalName, TitledPane titledPane) {
        if (communicationService.hasPermission(permissionTechnicalName)) {
            titledPane.setDisable(false);
            return true;
        } else {
            titledPane.setDisable(true);
            return false;
        }
    }

    @FXML
    public void initialize() {
        if (checkPermissionForTitledPane(GroupPermission.ROLE_READ_ACCOUNT_PRIVATE_DETAILS, userRegistrationFeedPane)) {
            ViewHelper.buildUserTableView(platformService, userRegistrationFeedTableView, users, this::addBan,
                    playerFX -> ViewHelper.loadForceRenameDialog(uiService, playerFX), communicationService);
        }

        if (checkPermissionForTitledPane(GroupPermission.ROLE_READ_TEAMKILL_REPORT, teamkillFeedPane)) {
            ViewHelper.buildTeamkillTableView(teamkillFeedTableView, teamkills, true, this::addBan);
        }

        if (checkPermissionForTitledPane(GroupPermission.ROLE_ADMIN_MAP, mapUploadFeedPane)) {
            ViewHelper.buildMapFeedTableView(mapUploadFeedTableView, mapVersions, this::toggleHide);
        }

    }
    private void checkBlacklistedItem(String fileName, List<String> blacklistedItems, String item, PlayerFX account) {
        for (String blacklistedItem : blacklistedItems) {
            if (blacklistedItem.equals(item)) {
                log.debug("[!] " + fileName + " [" + blacklistedItem + "] for " + account.getRepresentation());
                suspiciousUserTextArea.setText(suspiciousUserTextArea.getText() + "[!] " + fileName + " [" + blacklistedItem + "] for " + account.getRepresentation() + "\n");
            }
        }
    }

    private void addBan(PlayerFX playerFX) {
        BanInfoController banInfoController = uiService.loadFxml("ui/banInfo.fxml");
        BanInfoFX banInfo = new BanInfoFX();
        banInfo.setPlayer(playerFX);
        banInfoController.setBanInfo(banInfo);
        banInfoController.addPostedListener(banInfoFX -> refresh());
        banInfoController.addRevokedListener(this::refresh);
        Stage banInfoDialog = new Stage();
        banInfoDialog.setTitle("Apply new ban");
        banInfoDialog.setScene(new Scene(banInfoController.getRoot()));
        banInfoDialog.showAndWait();
    }

    private void toggleHide(MapVersionFX mapVersionFX) {
        mapVersionFX.setHidden(!mapVersionFX.isHidden());
        mapService.patchMapVersion(mapVersionFX);
    }

    private void loadList(File file, List<String> list) {
        Task task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try (Scanner s = new Scanner(file)) {
                    while (s.hasNextLine()) {
                        String blacklistedItem = s.nextLine();
                        list.add(blacklistedItem);
                    }
                } catch (FileNotFoundException e) {
                    log.debug(String.valueOf(e));
                }

                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line = reader.readLine();
                    int lineCount = 0;
                    while (line != null) {
                        lineCount++;
                        line = reader.readLine();
                    }
                    log.debug("[info] " + file + " loaded. Total items: " + lineCount);
                } catch (IOException e) {
                    log.debug(String.valueOf(e));
                }
                return null;
            }
        };
        new Thread(task).start();

    }
    private List<String> filterList(List<String> list, List<String> excludedItems) {
        return list.stream()
                .filter(item -> !excludedItems.contains(item))
                .collect(Collectors.toList());
    }
    public void refresh() {
        Task task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                teamkills.setAll(userService.findLatestTeamkills());
                teamkillFeedTableView.getSortOrder().clear();
                mapVersions.setAll(mapService.findLatestMapVersions());
                mapUploadFeedTableView.getSortOrder().clear();

                suspiciousUserTextArea.setText("");

                List<String> excludedItems = new ArrayList<>();
                List<String> blacklistedHash = new ArrayList<>();
                List<String> blacklistedIP = new ArrayList<>();
                List<String> blacklistedMemorySN = new ArrayList<>();
                List<String> blacklistedSN = new ArrayList<>();
                List<String> blacklistedUUID = new ArrayList<>();
                List<String> blacklistedVolumeSN = new ArrayList<>();
                //TODO ref
                //String[] fileNames = {"excludedItems", "blacklistedHash", "blacklistedIP", "blacklistedMemorySN",
                //        "blacklistedSN", "blacklistedUUID", "blacklistedVolumeSN"};
                //List<File> files = new ArrayList<>();
                //for (String name : fileNames) {
                //    files.add(new File(CONFIGURATION_FOLDER + "/" + name + ".txt"));
                //}
                File fileExcludedItems = new File(CONFIGURATION_FOLDER + "/excludedItems" + ".txt");
                File fileBlacklistedHash = new File(CONFIGURATION_FOLDER + "/blacklistedHash" + ".txt");
                File fileBlacklistedIP = new File(CONFIGURATION_FOLDER + "/blacklistedIP" + ".txt");
                File fileBlacklistedMemorySN = new File(CONFIGURATION_FOLDER + "/blacklistedMemorySN" + ".txt");
                File fileBlacklistedSN = new File(CONFIGURATION_FOLDER + "/blacklistedSN" + ".txt");
                File fileBlacklistedUUID = new File(CONFIGURATION_FOLDER + "/blacklistedUUID" + ".txt");
                File fileBlacklistedVolumeSN = new File(CONFIGURATION_FOLDER + "/blacklistedVolumeSN" + ".txt");

                //TODO if the format is not correct, it will add an empty item to the list which can cause issues later?
                try {
                    Scanner s = new Scanner(fileExcludedItems);
                    while (s.hasNextLine()) {
                        excludedItems.add(s.nextLine());
                    }
                    s.close();
                    log.debug("[info] " + fileExcludedItems + " loaded.");

                    log.debug("fileExcludedItems:" + excludedItems);
                } catch (Exception e) {
                    log.debug(String.valueOf(e));
                }

                loadList(fileBlacklistedHash, blacklistedHash);
                loadList(fileBlacklistedIP, blacklistedIP);
                loadList(fileBlacklistedMemorySN, blacklistedMemorySN);
                loadList(fileBlacklistedSN, blacklistedSN);
                loadList(fileBlacklistedUUID, blacklistedUUID);
                loadList(fileBlacklistedVolumeSN, blacklistedVolumeSN);

                List<String> filteredBlacklistedHash = filterList(blacklistedHash, excludedItems);
                List<String> filteredBlacklistedIP = filterList(blacklistedIP, excludedItems);
                List<String> filteredBlacklistedMemorySN = filterList(blacklistedMemorySN, excludedItems);
                List<String> filteredBlacklistedSN = filterList(blacklistedSN, excludedItems);
                List<String> filteredBlacklistedUUID = filterList(blacklistedUUID, excludedItems);
                List<String> filteredBlacklistedVolumeSN = filterList(blacklistedVolumeSN, excludedItems);

                users.setAll(userService.findLatestRegistrations());
                userRegistrationFeedTableView.getSortOrder().clear();

                List<PlayerFX> accounts = userService.findLatestRegistrations();

                for (PlayerFX account : accounts) {
                    // cycle through players
                    Boolean includeBannedUserGlobally = includeGlobalBannedUserCheckBox.isSelected();

                    if (includeBannedUserGlobally.equals(account.isBannedGlobally())) {
                        ObservableSet<UniqueIdFx> accountUniqueIds = account.getUniqueIds();

                        for (UniqueIdFx item : accountUniqueIds) {
                            checkBlacklistedItem("blacklistedHash", filteredBlacklistedHash, item.getHash(), account);
                            checkBlacklistedItem("blacklistedIP", filteredBlacklistedIP, account.getRecentIpAddress(), account);
                            checkBlacklistedItem("blacklistedMemorySN", filteredBlacklistedMemorySN, item.getMemorySerialNumber(), account);
                            checkBlacklistedItem("blacklistedSN", filteredBlacklistedSN, item.getSerialNumber(), account);
                            checkBlacklistedItem("blacklistedUUID", filteredBlacklistedUUID, item.getUuid(), account);
                            checkBlacklistedItem("blacklistedVolumeSN", filteredBlacklistedVolumeSN, item.getVolumeSerialNumber(), account);
                        }
                    }
                }
                log.debug("[info] Last 10k users checked.");
                return null;
            }
        };
        new Thread(task).start();
    }
}
