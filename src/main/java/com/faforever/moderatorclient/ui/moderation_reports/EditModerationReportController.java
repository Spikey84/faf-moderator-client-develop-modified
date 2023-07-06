package com.faforever.moderatorclient.ui.moderation_reports;

import com.faforever.commons.api.dto.ModerationReport;
import com.faforever.commons.api.dto.ModerationReportStatus;
import com.faforever.moderatorclient.api.domain.ModerationReportService;
import com.faforever.moderatorclient.ui.Controller;
import com.faforever.moderatorclient.ui.domain.ModerationReportFX;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.Scanner;

import static com.faforever.moderatorclient.ui.MainController.CONFIGURATION_FOLDER;

@Component
@RequiredArgsConstructor
public class EditModerationReportController implements Controller<Pane> {
	private final ModerationReportService moderationReportService;
	public TextArea privateNoteTextArea;
	public TextArea publicNoteTextArea;
	public ChoiceBox<ModerationReportStatus> statusChoiceBox;
	public Pane root;
	public Button pastePoorReportQualityButton;
	private Runnable onSaveRunnable;
	private ModerationReportFX moderationReportFx;
	public Button pasteDiscardedTemplate;
	public Button pasteCompletedTemplate;

	@FXML
	public void initialize() {
		statusChoiceBox.setItems(FXCollections.observableArrayList(ModerationReportStatus.values()));
	}

	public void onSave() {
		ModerationReport updateModerationReport = new ModerationReport();
		updateModerationReport.setId(moderationReportFx.getId());
		updateModerationReport.setReportStatus(statusChoiceBox.getSelectionModel().getSelectedItem());
		updateModerationReport.setModeratorPrivateNote(privateNoteTextArea.getText());
		updateModerationReport.setModeratorNotice(publicNoteTextArea.getText());
		moderationReportService.patchReport(updateModerationReport);
		moderationReportFx.setReportStatus(statusChoiceBox.getSelectionModel().getSelectedItem());
		moderationReportFx.setModeratorPrivateNote(privateNoteTextArea.getText());
		moderationReportFx.setModeratorNotice(publicNoteTextArea.getText());
		onSaveRunnable.run();
		close();
	}

	public void setOnSaveRunnable(Runnable onSaveRunnable) {
		this.onSaveRunnable = onSaveRunnable;
	}

	public void setModerationReportFx(ModerationReportFX moderationReportFx) {
		this.moderationReportFx = moderationReportFx;
		statusChoiceBox.getSelectionModel().select(moderationReportFx.getReportStatus());
		privateNoteTextArea.setText(moderationReportFx.getModeratorPrivateNote());
		publicNoteTextArea.setText(moderationReportFx.getModeratorNotice());
	}

	@Override
	public Pane getRoot() {
		return root;
	}

	public void close() {
		Stage stage = (Stage) root.getScene().getWindow();
		stage.close();
	}

	public void pasteTemplate(String templateName, ModerationReportStatus status, boolean autoReport) throws FileNotFoundException {
		String content = new Scanner(new File(templateName)).useDelimiter("\\Z").next();
		publicNoteTextArea.setText(content);
		statusChoiceBox.getSelectionModel().select(status);
		if (autoReport) {
			onSave();
		}
	}

	public void onPasteCompletedTemplate() {
		Properties config = new Properties();
		try {
			config.load(new FileInputStream(CONFIGURATION_FOLDER + "/config.properties"));
			pasteTemplate(CONFIGURATION_FOLDER+File.separator+"templateCompleted.txt", ModerationReportStatus.COMPLETED, Boolean.parseBoolean(config.getProperty("autoCompleteCheckBox", "false")));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void onPasteDiscardedTemplate() {
		Properties config = new Properties();
		try {
			config.load(new FileInputStream(CONFIGURATION_FOLDER + "/config.properties"));
			pasteTemplate(CONFIGURATION_FOLDER+File.separator+"templateDiscarded.txt", ModerationReportStatus.DISCARDED, Boolean.parseBoolean(config.getProperty("autoDiscardCheckBox", "false")));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void onPastePoorReportQuality(ActionEvent actionEvent) {
		Properties config = new Properties();
		try {
			config.load(new FileInputStream(CONFIGURATION_FOLDER + "/config.properties"));
			pasteTemplate(CONFIGURATION_FOLDER+File.separator+"templatePoorReportQuality.txt", ModerationReportStatus.DISCARDED, Boolean.parseBoolean(config.getProperty("autoDiscardCheckBox", "false")));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
