package com.faforever.moderatorclient;

import com.faforever.moderatorclient.config.ApplicationProperties;
import com.faforever.moderatorclient.ui.MainController;
import com.faforever.moderatorclient.ui.PlatformService;
import com.faforever.moderatorclient.ui.PlatformServiceImpl;
import com.faforever.moderatorclient.ui.StageHolder;
import com.faforever.moderatorclient.ui.UiService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(ApplicationProperties.class)
@ComponentScan
@Slf4j

public class FafModeratorClientApplication extends Application {

    private ConfigurableApplicationContext applicationContext;

    public static void applicationMain(String[] args) {
        Application.launch(FafModeratorClientApplication.class, args);
    }

    @Override
    public void init() {
        SpringApplication app = new SpringApplication(FafModeratorClientApplication.class);
        applicationContext = app.run();
        applicationContext.getAutowireCapableBeanFactory().autowireBean(this);
    }

    @Override
    public void start(Stage primaryStage) {
        Font.loadFont(getClass().getResource("/style/NotoEmoji-Regular.ttf").toExternalForm(), 12);
        StageHolder.setStage(primaryStage);
        primaryStage.setTitle("Spikey's Magge's Modified Mordor");
        UiService uiService = applicationContext.getBean(UiService.class);
        MainController mainController = uiService.loadFxml("ui/mainWindow.fxml");
        mainController.display();
        primaryStage.getIcons().add(new Image(Objects.requireNonNull(this.getClass().getResourceAsStream("/media/favicon.png"))));
        Scene scene = new Scene(mainController.getRoot());
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style/main.css")).toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();
        primaryStage.setOnCloseRequest(e -> {
            e.consume();
            log.info("Close request received, exiting");
            Platform.exit();
            System.exit(0);
        });
        startTimerThread(primaryStage);
    }

    private void waitSecond() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startTimerThread(Stage primaryStage) {

        long startTime = System.currentTimeMillis();
        Thread timerThread = new Thread(() -> {
            while (true) {

                long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
                long minutes = TimeUnit.SECONDS.toMinutes(elapsedTime) % 60;
                long seconds = TimeUnit.SECONDS.toSeconds(elapsedTime) % 60;
                String elapsedTimeStr = String.format("%02d:%02d:%02d", TimeUnit.SECONDS.toHours(elapsedTime),
                        TimeUnit.SECONDS.toMinutes(elapsedTime) % TimeUnit.HOURS.toMinutes(1),
                        TimeUnit.SECONDS.toSeconds(elapsedTime) % TimeUnit.MINUTES.toSeconds(1));
                Platform.runLater(() -> primaryStage.setTitle("Spikey's Magge's Modified Mordor - Running Time: " + elapsedTimeStr + " Active Threads: " + Thread.activeCount()));
                if (minutes == 4 && seconds >= 14 && seconds <= 20) {
                    String[] emoticons = {" (o︵o )"," (o︵o)"," ( o︵o)", " ( o︵o)/", " ( o︵o)y─", " ( o︵o)y─\uD83D\uDD25", " ( *‿*)y─┛~"};
                    int index = (int) (seconds - 14) % emoticons.length;
                    String emoticon = emoticons[index];
                    Platform.runLater(() -> primaryStage.setTitle("Spikey's Magge's Modified Mordor - Running Time: " + elapsedTimeStr + " " + emoticon));
                }
                waitSecond();
            }
        });
        timerThread.start();
    }

    @Bean
    public PlatformService platformService() {
        return new PlatformServiceImpl(getHostServices());
    }
}
