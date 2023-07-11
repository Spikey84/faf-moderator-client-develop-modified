package com.faforever.moderatorclient.ui.main_window;

import com.faforever.commons.api.dto.ModerationReportStatus;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import com.faforever.moderatorclient.ui.Controller;

import java.lang.annotation.Annotation;

@Component
@RequiredArgsConstructor
public class ZoomInMapWindow implements Controller<Pane> {
    public Pane root;
    private ImageView zoomInWindow;

    public void setImage(Image image) {
        zoomInWindow.setImage(image);
    }

    @Override
    public Pane getRoot() {
        return root;
    }

    public void close() {
        Stage stage = (Stage) root.getScene().getWindow();
        stage.close();
    }

    @FXML
    public void initialize() {

    }
}
