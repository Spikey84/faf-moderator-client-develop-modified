package com.faforever.moderatorclient.ui.moderation_reports;

import com.faforever.commons.api.dto.ModerationReport;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import com.faforever.commons.api.dto.ModerationReportStatus;
import com.faforever.commons.replay.ReplayDataParser;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.domain.ModerationReportService;
import com.faforever.moderatorclient.ui.BanInfoController;
import com.faforever.moderatorclient.ui.Controller;
import com.faforever.moderatorclient.ui.PlatformService;
import com.faforever.moderatorclient.ui.UiService;
import com.faforever.moderatorclient.ui.ViewHelper;
import com.faforever.moderatorclient.ui.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.URI;
import com.google.common.base.Strings;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.awt.*;
import java.awt.datatransfer.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.faforever.moderatorclient.ui.MainController.CONFIGURATION_FOLDER;
import static java.text.MessageFormat.format;
@Component
@Slf4j
@RequiredArgsConstructor
public class ModerationReportController implements Controller<Region> {
    public TableView mostReportedAccountsTableView;
    public TextArea moderatorStatisticsTextArea;
    private final ObjectMapper objectMapper;
    private final ModerationReportService moderationReportService;
    private final UiService uiService;
    private final FafApiCommunicationService communicationService;
    private final PlatformService platformService;
    private final ObservableList<PlayerFX> reportedPlayersOfCurrentlySelectedReport = FXCollections.observableArrayList();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    public CheckBox FilterLogCheckBox;
    public CheckBox AutomaticallyLoadChatLogCheckBox;
    public Button CreateReportButton;
    public Button UseTemplateWithoutReasonsButton;
    public TableView moderatorStatisticsTableView;
    public CheckBox reasonsCheckBox;
    public Button UseTemplateWithReasonsButton;

    @Value("${faforever.vault.replay-download-url-format}")
    private String replayDownLoadFormat;

    public Region root;
    public ChoiceBox<ChooseableStatus> statusChoiceBox;
    public TextField playerNameFilterTextField;
    public TableView<ModerationReportFX> reportTableView;
    public Button editReportButton;
    public TableView<PlayerFX> reportedPlayerTableView;
    public TextArea chatLogTextArea;
    public Button CopyReportedUserIDButton;
    public Button CopyChatLogButton;
    public Button CopyReportIDButton;
    public Button CopyGameIDButton;
    public Button StartReplayButton;

    public TextArea description;
    public TextArea privateNote;
    public TextArea publicNote;
    public TextArea lastModerator;
    public TextArea dateCreated;
    public TextArea moderationLog;


    private FilteredList<ModerationReportFX> filteredItemList;
    private ObservableMap<Integer, ModerationReportFX> itemMap;
    private ObservableList<ModerationReportFX> itemList;
    private ModerationReportFX currentlySelectedItemNotNull;

    private boolean isLoading = false;

    @Override
    public SplitPane getRoot() {
        return (SplitPane) root;
    }

    public void onCopyReportedUserID() {
        setSysClipboardText(CopyReportedUserIDButton.getId());
    }

    public void onCopyChatLog() {
        setSysClipboardText(CopyChatLogButton.getId());
    }

    public void onCopyReportID() {
        setSysClipboardText(CopyReportIDButton.getId() + ",");
    }

    public void onCopyGameID() {
        setSysClipboardText(CopyGameIDButton.getId());
    }

    public void onCreateReportButton() throws IOException {
        String reportedUserId = CreateReportButton.getId();
        String url = "https://forum.faforever.com/search?term=" + reportedUserId + "&in=titles";
        String cmd = "cmd /c start chrome " + url;
        Runtime.getRuntime().exec(cmd);
    }

    public void onStartReplay() throws IOException, InterruptedException {
        String replayId = StartReplayButton.getId();
        String replayUrl = "https://replay.faforever.com/" + replayId;
        Path tempFilePath = Files.createTempFile("faf_replay_", ".fafreplay");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(replayUrl))
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFilePath));

        String cmd = "cmd /c " + tempFilePath;
        Runtime.getRuntime().exec(cmd);
    }

    private void removeTrailingComma(StringBuilder sb) {
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }
    }
    public void onUseTemplateWithoutReasonsButton() {
        try {
            ObservableList<ModerationReportFX> selectedItems = reportTableView.getSelectionModel().getSelectedItems();

            String selectedIds = selectedItems.stream()
                    .map(item -> String.valueOf(item.getId()))
                    .collect(Collectors.joining(","));

            String selectedGameIds = selectedItems.stream()
                    .map(ModerationReportFX::getGame)
                    .filter(Objects::nonNull)
                    .map(game -> String.valueOf(game.getId()))
                    .distinct()
                    .collect(Collectors.joining(","));

            removeTrailingComma(new StringBuilder(selectedIds));
            removeTrailingComma(new StringBuilder(selectedGameIds));

            String result;
            result = selectedIds + "\n\n" + "DAY_NUMBER day ban - ReplayID " + selectedGameIds + " - SOME_REASON";
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(result);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(clipboardContent);

            UseTemplateWithoutReasonsButton.setText("Copied");
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> UseTemplateWithoutReasonsButton.setText("Use template"));
                }
            }, 750);
        } catch (NullPointerException e) {
            log.debug(String.valueOf(e));
        }
    };

    public void onUseTemplateWithReasonsButton() {
        try {
            ObservableList<ModerationReportFX> selectedItems = reportTableView.getSelectionModel().getSelectedItems();

            String selectedIds = selectedItems.stream()
                    .map(item -> String.valueOf(item.getId()))
                    .collect(Collectors.joining(","));

            String selectedGameIds = selectedItems.stream()
                    .map(ModerationReportFX::getGame)
                    .filter(Objects::nonNull)
                    .map(game -> String.valueOf(game.getId()))
                    .distinct()
                    .collect(Collectors.joining(","));

            removeTrailingComma(new StringBuilder(selectedIds));
            removeTrailingComma(new StringBuilder(selectedGameIds));

            Stage stage = new Stage();
            final String selectedReasons = "";
            stage.setTitle("Select reasons:");

            GridPane gridPane = new GridPane();
            gridPane.setHgap(10);
            gridPane.setVgap(10);
            gridPane.setPadding(new Insets(120, 120, 120, 120));

            File file = new File(CONFIGURATION_FOLDER + File.separator + "templateReasonsCheckBox.txt");
            List<String> checkBoxLabels = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    checkBoxLabels.add(line);
                }
            } catch (IOException e) {
                log.debug(String.valueOf(e));
            }

            List<CheckBox> checkBoxes = new ArrayList<>();
            for (String label : checkBoxLabels) {
                CheckBox checkBox = new CheckBox(label);
                checkBoxes.add(checkBox);
            }

            for (int i = 0; i < checkBoxes.size(); i++) {
                gridPane.add(checkBoxes.get(i), 0, i);
            }

            Button okButton = new Button("OK");
            gridPane.add(okButton, 0, checkBoxLabels.size());

            Scene scene = new Scene(gridPane);
            stage.setScene(scene);
            stage.show();

            final String[] updatedReasons = {selectedReasons};
            okButton.setOnAction(event -> {
                for (CheckBox checkBox : checkBoxes) {
                    if (checkBox.isSelected()) {
                        updatedReasons[0] += checkBox.getText() + ", ";
                    }
                }

                String updatedReasonsString = Arrays.toString(updatedReasons);
                updatedReasonsString = updatedReasonsString.substring(1, updatedReasonsString.length() - 1); // Remove brackets
                updatedReasonsString = updatedReasonsString.substring(0, updatedReasonsString.length() - 2); // Remove latest two char

                String result;
                result = selectedIds + "\n\n" + "DAY_NUMBER day ban - ReplayID " + selectedGameIds + " - " + updatedReasonsString;

                ClipboardContent clipboardContent = new ClipboardContent();
                clipboardContent.putString(result);
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(clipboardContent);
                stage.close();
            });

            UseTemplateWithReasonsButton.setText("Copied");
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> UseTemplateWithReasonsButton.setText("Use template, with reasons"));
                }
            }, 750);
        } catch (NullPointerException e) {
            log.debug(String.valueOf(e));
        }
    }

    private void showInTableRepeatedOffenders(List<ModerationReportFX> reps) {

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                List<ModerationReportFX> reports = Lists.newArrayList(reps.listIterator());

                Map<String, Long> offendersAwaitingReports = reports.stream()
                        .filter(report -> report.getReportStatus().equals(ModerationReportStatus.AWAITING))
                        .flatMap(report -> report.getReportedUsers().stream())
                        .collect(Collectors.groupingBy(PlayerFX::getRepresentation, Collectors.counting()));

                Map<String, Long> sortedOffendersAwaitingReports = offendersAwaitingReports.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

                List<Offender> offenders = sortedOffendersAwaitingReports.entrySet().stream().map(entry -> {
                    String offenderUsername = entry.getKey();
                    Long offenderReportCount = entry.getValue();
                    String containsRU = reports.stream()
                            .filter(report -> report.getReportedUsers().stream()
                                    .anyMatch(user -> user.getRepresentation().equals(offenderUsername)))
                            .anyMatch(report -> report.getReportDescription().matches(".*[А-Яа-я]+.*")) ? "yes" : "no";

                    Optional<OffsetDateTime> maxCreateTime = reports.stream()
                            .filter(report -> report.getReportedUsers().stream()
                                    .anyMatch(user -> user.getRepresentation().equals(offenderUsername)))
                            .map(ModerationReportFX::getCreateTime)
                            .max(Comparator.naturalOrder());
                    assert maxCreateTime.orElse(null) != null;
                    LocalDateTime lastReported = maxCreateTime.orElse(null).toLocalDateTime();

                    return new Offender(offenderUsername, offenderReportCount, containsRU, lastReported);
                }).collect(Collectors.toList());

                Platform.runLater(() -> mostReportedAccountsTableView.setItems(FXCollections.observableArrayList(offenders)));

                mostReportedAccountsTableView.setOnKeyPressed(event -> {
                    if (event.isControlDown() && event.getCode() == KeyCode.C) {
                        Offender selectedOffender = (Offender) mostReportedAccountsTableView.getSelectionModel().getSelectedItem();
                        if (selectedOffender != null) {
                            StringSelection stringSelection = new StringSelection(selectedOffender.getPlayer());
                            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                            clipboard.setContents(stringSelection, null);
                        }
                    }
                });
                return null;
            }
        };

        new Thread(task).start();

    }

    public class ModeratorStatistics {
        private final StringProperty moderator;
        private final LongProperty completedReports;
        private final LongProperty discardedReports;
        private final LongProperty processingReports;
        private final LongProperty allReports;
        private LocalDateTime lastActivity;

        public ModeratorStatistics(String moderator) {
            this.moderator = new SimpleStringProperty(moderator);
            this.completedReports = new SimpleLongProperty(0L);
            this.discardedReports = new SimpleLongProperty(0L);
            this.processingReports = new SimpleLongProperty(0L);
            this.allReports = new SimpleLongProperty(0L);
        }

        public LocalDateTime getLastActivity() {
            return lastActivity;
        }
        public String getModerator() {
            return moderator.get();
        }
        public void setModerator(String moderator) {
            this.moderator.set(moderator);
        }
        public Long getCompletedReports() {
            return completedReports.get();
        }
        public void setCompletedReports(Long completedReports) {
            this.completedReports.set(completedReports);
        }
        public Long getDiscardedReports() {
            return discardedReports.get();
        }
        public void setDiscardedReports(Long discardedReports) {
            this.discardedReports.set(discardedReports);
        }
        public Long getProcessingReports() {
            return processingReports.get();
        }
        public void setProcessingReports(Long processingReports) {
            this.processingReports.set(processingReports);
        }
        public Long getAllReports() {
            return allReports.get();
        }
        public void setAllReports(Long allReports) {
            this.allReports.set(allReports);
        }

        public void setLastActivity(LocalDateTime lastActivity) {
            this.lastActivity = lastActivity;
        }
    }

    private void processStatisticsModerator(List<ModerationReportFX> reps) {

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                //TODO ref readable
                ArrayList<ModerationReportFX> reports = Lists.newArrayList(reps.iterator());

                Map<PlayerFX, Map<ModerationReportStatus, Integer>> moderatorReportCounts = new HashMap<>();
                for (ModerationReportFX report : reports) {
                    if (report.getLastModerator() != null) {
                        moderatorReportCounts.computeIfAbsent(report.getLastModerator(), k -> new HashMap<>());
                        moderatorReportCounts.get(report.getLastModerator()).compute(report.getReportStatus(), (k, v) -> v == null ? 1 : v + 1);
                    }
                }

                Map<ModerationReportStatus, Integer> totalReportCounts = new HashMap<>();
                for (ModerationReportFX report : reports) {
                    totalReportCounts.compute(report.getReportStatus(), (k, v) -> v == null ? 1 : v + 1);
                }

                int totalReports = 0;
                for (Integer count : totalReportCounts.values()) {
                    totalReports += count;
                }

                StringBuilder sb = new StringBuilder();
                sb.append("All Reports: ").append(totalReports).append(" | ");
                sb.append("Completed: ").append(totalReportCounts.getOrDefault(ModerationReportStatus.COMPLETED, 0)).append(" | ");
                sb.append("Discarded: ").append(totalReportCounts.getOrDefault(ModerationReportStatus.DISCARDED, 0)).append(" | ");
                sb.append("Awaiting: ").append(totalReportCounts.getOrDefault(ModerationReportStatus.AWAITING, 0)).append(" | ");
                sb.append("Processing: ").append(totalReportCounts.getOrDefault(ModerationReportStatus.PROCESSING, 0));

                Set<String> uniqueModerators = new HashSet<>();
                for (ModerationReportFX report : reports) {
                    if (report.getLastModerator() != null) {
                        uniqueModerators.add(report.getLastModerator().getRepresentation());
                    }
                }

                ObservableList<ModeratorStatistics> data = FXCollections.observableArrayList();
                for (String moderator : uniqueModerators) {
                    data.add(new ModeratorStatistics(moderator));
                }

                Map<String, Long> reportsByModeratorAndStatus = reports.stream()
                        .filter(report -> report.getLastModerator() != null)
                        .filter(report -> report.getReportStatus().equals(ModerationReportStatus.DISCARDED)
                                || report.getReportStatus().equals(ModerationReportStatus.COMPLETED)
                                || report.getReportStatus().equals(ModerationReportStatus.PROCESSING))
                        .collect(Collectors.groupingBy(r -> r.getLastModerator().getRepresentation() + "-" + r.getReportStatus().name(), Collectors.counting()));

                for (ModeratorStatistics moderatorStat : data) {
                    moderatorStat.setCompletedReports(reportsByModeratorAndStatus.getOrDefault(moderatorStat.getModerator() + "-COMPLETED", 0L));
                    moderatorStat.setDiscardedReports(reportsByModeratorAndStatus.getOrDefault(moderatorStat.getModerator() + "-DISCARDED", 0L));
                    moderatorStat.setProcessingReports(reportsByModeratorAndStatus.getOrDefault(moderatorStat.getModerator() + "-PROCESSING", 0L));
                }

                Map<String, Long> allReportsByModerator = reports.stream()
                        .filter(report -> report.getLastModerator() != null)
                        .filter(report -> report.getReportStatus().equals(ModerationReportStatus.DISCARDED)
                                || report.getReportStatus().equals(ModerationReportStatus.COMPLETED)
                                || report.getReportStatus().equals(ModerationReportStatus.PROCESSING))
                        .collect(Collectors.groupingBy(r -> r.getLastModerator().getRepresentation(), Collectors.counting()));

                for (ModeratorStatistics moderatorStat : data) {
                    moderatorStat.setAllReports(allReportsByModerator.getOrDefault(moderatorStat.getModerator(), 0L));
                }

                Map<String, OffsetDateTime> maxLastActivityByModerator = reports.stream()
                        .filter(report -> report.getLastModerator() != null)
                        .collect(Collectors.groupingBy(report -> report.getLastModerator().getRepresentation(),
                                Collectors.mapping(ModerationReportFX::getUpdateTime, Collectors.maxBy(Comparator.naturalOrder()))
                        ))
                        .entrySet().stream()
                        .filter(entry -> entry.getValue().isPresent())
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));

                for (ModeratorStatistics moderatorStat : data) {
                    moderatorStat.setLastActivity(maxLastActivityByModerator.get(moderatorStat.getModerator()).toLocalDateTime());
                }
                Platform.runLater(() -> {
                    moderatorStatisticsTableView.setItems(data);
                    moderatorStatisticsTextArea.setText(sb.toString());
                });
                return null;
            }
        };

        new Thread(task).start();

    }

    public static class Offender {
        private final StringProperty player;
        private final LongProperty offenseCount;
        private final StringProperty containsRU;
        private LocalDateTime lastReported;

        public Offender(String player, long offenseCount, String containsRU, LocalDateTime lastReported ) {
            this.player = new SimpleStringProperty(player);
            this.offenseCount = new SimpleLongProperty(offenseCount);
            this.containsRU = new SimpleStringProperty(containsRU);
            this.lastReported = lastReported;
        }

        public void lastReported(LocalDateTime lastReported) {
            this.lastReported = lastReported;
        }
        public String isContainsRU() {
            return containsRU.getValue();
        }

        public LocalDateTime getLastReported() {
            return lastReported;
        }
        public String getPlayer() {
            return player.get();
        }

        public StringProperty playerProperty() {
            return player;
        }

        public long getOffenseCount() {
            return offenseCount.get();
        }

        public LongProperty offenseCountProperty() {
            return offenseCount;
        }

        public void setContainsRU(String containsRU) {
            this.containsRU.set(containsRU);
        }

    }

    @FXML
    public void initialize() {
        mostReportedAccountsTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            try {
                ModerationReportController.Offender selectedAccount = (ModerationReportController.Offender) newValue;
                String selectedAccountString = String.valueOf(selectedAccount.getPlayer());
                int startIndex = 0;
                int endIndex = selectedAccountString.indexOf(" [id");
                String accountName = selectedAccountString.substring(startIndex, endIndex);
                playerNameFilterTextField.setText(accountName);
            } catch (ClassCastException e) {
                // Do nothing if no account is selected in table
            }
        });
        statusChoiceBox.setItems(FXCollections.observableArrayList(ChooseableStatus.values()));
        statusChoiceBox.getSelectionModel().select(ChooseableStatus.AWAITING);
        editReportButton.disableProperty().bind(reportTableView.getSelectionModel().selectedItemProperty().isNull());
        itemMap = FXCollections.observableHashMap();
        itemList = FXCollections.observableArrayList();

        MapChangeListener<Integer, ModerationReportFX> listener = entry -> {
            if (entry.wasRemoved()) {
                itemList.remove(entry.getValueRemoved());
            } else if (entry.wasAdded()) {
                itemList.add(entry.getValueAdded());
            }
        };
        itemMap.addListener(listener);

        filteredItemList = new FilteredList<ModerationReportFX>(itemList);

        renewFilter();
        SortedList<ModerationReportFX> sortedItemList = new SortedList<>(filteredItemList);
        sortedItemList.comparatorProperty().bind(reportTableView.comparatorProperty());
        ViewHelper.buildModerationReportTableView(reportTableView, sortedItemList, this::showChatLog, this::markCompletedButton, this::markDiscardedButton);
        statusChoiceBox.getSelectionModel().selectedItemProperty().addListener(observable -> renewFilter());
        playerNameFilterTextField.textProperty().addListener(observable -> renewFilter());
        reportTableView.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> {
                    try {
                        reportedPlayersOfCurrentlySelectedReport.setAll(newValue.getReportedUsers());
                        currentlySelectedItemNotNull = newValue;
                        CopyReportIDButton.setId(newValue.getId());
                        CopyReportIDButton.setText("Report ID: " + newValue.getId());
                        if (newValue.getGame() != null) {
                            CopyGameIDButton.setId(newValue.getGame().getId());
                            CopyGameIDButton.setText("Game ID: " + newValue.getGame().getId());

                            StartReplayButton.setId(CopyGameIDButton.getId());
                            StartReplayButton.setText("Start Replay: " + CopyGameIDButton.getId());

                            if (AutomaticallyLoadChatLogCheckBox.isSelected()) {
                                showChatLog(newValue);
                                log.debug("[LoadChatLog] log automatically loaded");
                            }
                        }

                        for (PlayerFX item : reportedPlayersOfCurrentlySelectedReport) {
                            log.debug("Selected report id - offenders: " + item.getRepresentation());
                            CopyReportedUserIDButton.setId(item.getRepresentation());
                            CopyReportedUserIDButton.setText(item.getRepresentation());
                            CreateReportButton.setId(StringUtils.substringBetween(item.getRepresentation(), " [id ", "]"));
                            CreateReportButton.setText("Create report for " + StringUtils.substringBetween(item.getRepresentation(), " [id ", "]"));
                        }
                        updateRightUi(newValue);
                    } catch (Exception ErrorSelectedReport) {
                        log.debug("Exception for selected report: ");
                        log.debug(String.valueOf(ErrorSelectedReport));
                        chatLogTextArea.setText("Game ID is invalid or missing.");
                        CopyChatLogButton.setText("Chat does not exist");
                        CopyChatLogButton.setId("");
                        CopyGameIDButton.setText("Game ID does not exist");
                        CopyGameIDButton.setId("");
                        StartReplayButton.setText("Replay does not exist");
                        StartReplayButton.setId("");
                        CreateReportButton.setText("no value / missing Game ID");
                        CreateReportButton.setId("");
                        CopyReportedUserIDButton.setText("no value / missing Game ID");
                        CopyReportedUserIDButton.setId("");
                    }
                });
        chatLogTextArea.setText("select a report first");
        moderationLog.setText("Coming soon!");
        ViewHelper.buildUserTableView(platformService, reportedPlayerTableView, reportedPlayersOfCurrentlySelectedReport, this::addBan,
                playerFX -> ViewHelper.loadForceRenameDialog(uiService, playerFX), communicationService);
        updateRightUi(null);
    }

    public static void setSysClipboardText(String writeMe) {
        System.setProperty("java.awt.headless", "false");
        Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable tText = new StringSelection(writeMe);
        clip.setContents(tText, null);
    }

    private void addBan(PlayerFX accountFX) {
        BanInfoController banInfoController = uiService.loadFxml("ui/banInfo.fxml");
        BanInfoFX ban = new BanInfoFX();
        ban.setPlayer(accountFX);
        banInfoController.setBanInfo(ban);
        banInfoController.addPostedListener(banInfoFX -> onRefreshAllReports());
        Stage banInfoDialog = new Stage();
        banInfoDialog.setTitle("Apply new ban");
        banInfoDialog.setScene(new Scene(banInfoController.getRoot()));
        banInfoController.preSetReportId(currentlySelectedItemNotNull.getId());
        banInfoDialog.showAndWait();
    }

    private void renewFilter() {
        filteredItemList.setPredicate(moderationReportFx -> {
            String playerFilter = playerNameFilterTextField.getText().toLowerCase();
            if (!Strings.isNullOrEmpty(playerFilter)) {
                boolean reportedPlayerPositive = moderationReportFx.getReportedUsers().stream().anyMatch(accountFX -> accountFX.getLogin().toLowerCase().contains(playerFilter));
                boolean reporterPositive = moderationReportFx.getReporter().getLogin().toLowerCase().contains(playerFilter);
                if (!(reportedPlayerPositive || reporterPositive)) {
                    return false;
                }
            }
            ChooseableStatus selectedItemChoiceBox = statusChoiceBox.getSelectionModel().getSelectedItem();
            if (selectedItemChoiceBox.toString().equals("ALL")) return true;
            ModerationReportStatus moderationReportStatus = selectedItemChoiceBox.getModerationReportStatus();
            return moderationReportFx.getReportStatus() == moderationReportStatus;
        });
    }

    public void onRefreshAllReports() {
        if (isLoading) {
            System.out.println("Already Updating");
            return;
        }
        isLoading = true;
        createNewApiRequestThread(1, true);

    }

    private void createNewApiRequestThread(int x, boolean recursive) {
        int pageSize = 100;
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {

                moderationReportService.getPageOfReports(x, pageSize).thenAccept(reportFxes -> {
                    Platform.runLater(() -> {
                        reportFxes.forEach((report -> {
                            itemMap.put(Integer.valueOf(report.getId()), report);
                        }));
                    });
                    if (reportFxes.size() == pageSize || x < 2) {
                        if (recursive) {
                            createNewApiRequestThread(x+5, true);
                            for (int i = 1; i < 5; i++) {
                                createNewApiRequestThread(x+i, false);
                            }
                        }

                    } else {
                        isLoading = false;
                        //Platform.runLater(()-> {
                            processStatisticsModerator(itemList);
                            showInTableRepeatedOffenders(itemList);
                        //});

                    }
                }).exceptionally(throwable -> {
                    log.error("error loading reports", throwable);
                    return null;
                });

                return null;
            }
        };
        new Thread(task).start();
    }

    public void onEdit() {
        EditModerationReportController editModerationReportController = uiService.loadFxml("ui/edit_moderation_report.fxml");
        editModerationReportController.setModerationReportFx(reportTableView.getSelectionModel().getSelectedItem());
        editModerationReportController.setOnSaveRunnable(() -> Platform.runLater( () -> {
            renewFilter();
            this.onRefreshAllReports();
        }));
        //statusChoiceBox.setItems(FXCollections.observableArrayList(ChooseableStatus.values()));
        Stage newCategoryDialog = new Stage();
        newCategoryDialog.setTitle("Edit Report");
        newCategoryDialog.setScene(new Scene(editModerationReportController.getRoot()));
        newCategoryDialog.showAndWait();
    }

    private enum ChooseableStatus {
        ALL(null),
        AWAITING(ModerationReportStatus.AWAITING),
        PROCESSING(ModerationReportStatus.PROCESSING),
        COMPLETED(ModerationReportStatus.COMPLETED),
        DISCARDED(ModerationReportStatus.DISCARDED);

        private final ModerationReportStatus moderationReportStatus;

        ChooseableStatus(ModerationReportStatus moderationReportStatus) {
            this.moderationReportStatus = moderationReportStatus;
        }
        public ModerationReportStatus getModerationReportStatus() {
            return moderationReportStatus;
        }
    }

    public void asyncChatLogs() {


    }

    @SneakyThrows
    private void showChatLog(ModerationReportFX report) {

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    GameFX game = report.getGame();
                    String header = format("CHAT LOG -- Report ID {0} -- Replay ID {1} -- Game \"{2}\"\n\n",
                            report.getId(), game.getId(), game.getName());
                    Path tempFilePath = null;
                    try {
                        tempFilePath = Files.createTempFile(format("faf_replay_", game.getId()), "");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        String replayUrl = game.getReplayUrl(replayDownLoadFormat);
                        log.info("Downloading replay from {} to {}", replayUrl, tempFilePath);
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(replayUrl))
                                .build();

                        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFilePath));
                        if (response.statusCode() == 404) {
                            log.debug("The requested resource was not found on the server");
                            StartReplayButton.setText("Replay not available");
                            CopyChatLogButton.setText("Chat log not available");
                            chatLogTextArea.setText(header + format("Replay not available"));
                        } else {
                            log.debug("The request was successful - parsing replay");
                            ReplayDataParser replayDataParser = new ReplayDataParser(tempFilePath, objectMapper);
                            String chatLog = header + replayDataParser.getChatMessages().stream()
                                    .map(message -> format("[{0}] from {1} to {2}: {3}",
                                            DurationFormatUtils.formatDuration(message.getTime().toMillis(), "HH:mm:ss"),
                                            message.getSender(), message.getReceiver(), message.getMessage()))
                                    .collect(Collectors.joining("\n"));

                            BufferedReader bufReader = new BufferedReader(new StringReader(chatLog));

                            StringBuilder chatLogFiltered = new StringBuilder();
                            String compileSentences = "Can you give me some mass, |Can you give me some energy, |" +
                                    "Can you give me one Engineer, | to notify: | to allies: Sent Mass | to allies: Sent Energy |" +
                                    " to allies: sent ";
                            Pattern pattern = Pattern.compile(compileSentences);
                            String chatLine;
                            while ((chatLine = bufReader.readLine()) != null) {
                                boolean matchFound = pattern.matcher(chatLine).find();
                                if (FilterLogCheckBox.isSelected() && matchFound) {
                                    continue;
                                }
                                if (chatLine.contains(report.getReportedUsers().iterator().next().getLogin())) chatLine = chatLine /*+ "THIS IS WORKING!"*/;

                                chatLogFiltered.append(chatLine).append("\n");
                            }
                            Platform.runLater(() -> {
                                CopyChatLogButton.setId(chatLogFiltered.toString());
                                CopyChatLogButton.setText("Copy Chat Log");
                                chatLogTextArea.setText(chatLogFiltered.toString());
                            });

                        }
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            StartReplayButton.setText("Replay not available");
                            CopyChatLogButton.setText("Chat log not available");
                            //chatLogTextArea.setText(header + format("Loading replay failed due to {0}: \n{1}", e, e.getMessage()));
                            chatLogTextArea.setText(header + "Chat log not available");
                        });
                    }
                    try {
                        Files.delete(tempFilePath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            };
            new Thread(task).start();
    }

    @SneakyThrows
    private void markCompletedButton(ModerationReportFX report) {
        String content = new Scanner(new File(CONFIGURATION_FOLDER+File.separator+"templateCompleted.txt")).useDelimiter("\\Z").next();
        report.setModeratorNotice(content);
        report.setReportStatus(ModerationReportStatus.COMPLETED);
        ModerationReport newReport = new ModerationReport();
        newReport.setId(report.getId());
        newReport.setReportStatus(report.getReportStatus());
        newReport.setModeratorPrivateNote(report.getModeratorPrivateNote());
        newReport.setModeratorNotice(report.getModeratorNotice());
        moderationReportService.patchReport(newReport);
        renewFilter();
    }

    @SneakyThrows
    private void markDiscardedButton(ModerationReportFX report) {
        String content = new Scanner(new File(CONFIGURATION_FOLDER+File.separator+"templateDiscarded.txt")).useDelimiter("\\Z").next();
        report.setModeratorNotice(content);
        report.setReportStatus(ModerationReportStatus.DISCARDED);
        ModerationReport newReport = new ModerationReport();
        newReport.setId(report.getId());
        newReport.setReportStatus(report.getReportStatus());
        newReport.setModeratorPrivateNote(report.getModeratorPrivateNote());
        newReport.setModeratorNotice(report.getModeratorNotice());
        moderationReportService.patchReport(newReport);
        renewFilter();
    }

    private void updateRightUi(ModerationReportFX reportFX) {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (reportFX == null) {
                    description.setText("");
                    privateNote.setText("");
                    publicNote.setText("");
                    dateCreated.setText("");
                    lastModerator.setText("");
                    return null;
                }
                if (reportFX.getReportDescription() != null) description.setText(reportFX.getReportDescription()); else description.setText("");
                if (reportFX.getModeratorPrivateNote() != null) privateNote.setText(reportFX.getModeratorPrivateNote()); else privateNote.setText("");
                if (reportFX.getModeratorNotice() != null) publicNote.setText(reportFX.getModeratorNotice()); else publicNote.setText("");
                if (reportFX.getCreateTime() != null) dateCreated.setText(reportFX.getCreateTime().toString()); else dateCreated.setText("");
                if (reportFX.getLastModerator() != null) lastModerator.setText(reportFX.getLastModerator().getRepresentation()); else lastModerator.setText("");
                return null;
            }
        };

        new Thread(task).start();
    }
}
