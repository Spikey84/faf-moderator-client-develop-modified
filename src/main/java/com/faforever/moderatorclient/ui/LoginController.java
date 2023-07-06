package com.faforever.moderatorclient.ui;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.FafUserCommunicationService;
import com.faforever.moderatorclient.api.TokenService;
import com.faforever.moderatorclient.api.event.ApiAuthorizedEvent;
import com.faforever.moderatorclient.config.ApplicationProperties;
import com.faforever.moderatorclient.config.EnvironmentProperties;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.springframework.context.event.EventListener;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class LoginController implements Controller<Pane> {
    static {

        System.setProperty("java.awt.headless", "false");
    }

    private final ApplicationProperties applicationProperties;
    private final FafApiCommunicationService fafApiCommunicationService;
    private final FafUserCommunicationService fafUserCommunicationService;
    private final TokenService tokenService;
    private OAuth2AccessToken tokenCache;


    public VBox root;
    public ComboBox<String> environmentComboBox;
    public WebView loginWebView;
    public String state;

    private CompletableFuture<Void> resetPageFuture;

    @Override
    public Pane getRoot() {
        return root;
    }

    @FXML
    public void initialize() throws IOException {
        applicationProperties.getEnvironments().forEach((key, environmentProperties) ->
                environmentComboBox.getItems().add(key)
        );
        reloadLogin();
        environmentComboBox.getSelectionModel().select(0);
        loginWebView.getEngine().getLoadWorker().runningProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                String nameOrEmail = null;
                String password = null;
                try {
                    String homeDirectory = System.getProperty("user.home");
                    String filePath = homeDirectory + File.separator + "account_credentials_mordor.txt";
                    File file = new File(filePath);
                    if (!file.exists()) {
                        try {
                            boolean created = file.createNewFile();
                            if (created) {
                                log.debug("Created account_credentials_mordor.txt file.");
                            }
                        } catch (IOException e) {
                            log.debug("Error creating account_credentials_mordor.txt file: " + e.getMessage());
                        }
                    }

                    List<String> accountCredentials = Files.readAllLines(Paths.get(filePath));
                    if (!accountCredentials.get(0).isEmpty()) {
                        nameOrEmail = accountCredentials.get(0);
                        password = accountCredentials.get(1);
                    }
                } catch (IOException e) {
                    log.debug(String.valueOf(e));
                }

                if (nameOrEmail != null) {
                    try {
                        if (loginWebView.getEngine().executeScript("javascript:document.getElementById('form-header');") != null) {
                            loginWebView.getEngine().executeScript(String.format("javascript:document.getElementsByName('usernameOrEmail')[0].value = '%s'", nameOrEmail));
                            loginWebView.getEngine().executeScript(String.format("javascript:document.getElementsByName('password')[0].value = '%s'", password));
                            log.debug("[autologin] Account credentials were entered.");
                            loginWebView.getEngine().executeScript("javascript:document.querySelector('input[type=\"submit\"][value=\"Log in\"]').click()");
                            log.debug("[autologin] Log in button was automatically clicked.");
                        }
                    } catch (Exception error) {
                        log.debug(String.valueOf(error));
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                try {
                    if (tokenCache == null || tokenCache.isExpired()) {
                        Document doc = loginWebView.getEngine().getDocument();
                        Element element = doc.getElementById("denial-form");
                        if (element != null) {
                            loginWebView.getEngine().executeScript("javascript:document.querySelector('input[type=\"submit\"][value=\"Authorize\"]').click()");
                            log.debug("[autologin] Authorize button was automatically clicked.");
                        }
                    }
                }
                 catch (NullPointerException nullPointerException) {
                    log.debug("Catch: " + nullPointerException);
                } catch (Exception error) {
                    log.debug(String.valueOf(error));
                }

                resetPageFuture.complete(null);
            }
        });

        loginWebView.getEngine().locationProperty().addListener((observable, oldValue, newValue) -> {
            List<NameValuePair> params;

            try {
                params = URLEncodedUtils.parse(new URI(newValue), StandardCharsets.UTF_8);
            } catch (URISyntaxException e) {
                log.warn("Could not parse webpage url: {}", newValue, e);
                reloadLogin();
                onFailedLogin("Could not parse url");
                return;
            }

            if (params.stream().anyMatch(param -> param.getName().equals("error"))) {
                String error = params.stream().filter(param -> param.getName().equals("error"))
                        .findFirst().map(NameValuePair::getValue).orElse(null);
                String errorDescription = params.stream().filter(param -> param.getName().equals("error_description"))
                        .findFirst().map(NameValuePair::getValue).orElse(null);
                log.warn("Error during login error: url {}; error {}; {}", newValue, error, errorDescription);
                reloadLogin();
                onFailedLogin(MessageFormat.format("{0}; {1}", error, errorDescription));
                return;
            }

            String reportedState = params.stream().filter(param -> param.getName().equals("state"))
                    .map(NameValuePair::getValue)
                    .findFirst()
                    .orElse(null);

            String code = params.stream().filter(param -> param.getName().equals("code"))
                    .map(NameValuePair::getValue)
                    .findFirst()
                    .orElse(null);

            if (reportedState != null) {

                if (!state.equals(reportedState)) {
                    log.warn("Reported state does not match there is something fishy going on. Saved State `{}`, Returned State `{}`, Location `{}`", state, reportedState, newValue);
                    reloadLogin();
                    onFailedLogin("State returned by user service does not match initial state");
                    return;
                }

                if (code != null) {
                    tokenService.loginWithAuthorizationCode(code);
                }
            }
        });
    }

    public void reloadLogin() {
        log.debug("reloadLogin");
        resetPageFuture = new CompletableFuture<>();
        resetPageFuture.thenAccept(aVoid -> Platform.runLater(this::loadLoginPage));

        if (!loginWebView.getEngine().getLoadWorker().isRunning()) {
            resetPageFuture.complete(null);
        }
    }

    private void loadLoginPage(){
        loginWebView.getEngine().setJavaScriptEnabled(true);
        loginWebView.getEngine().load(getHydraUrl());
    }

    private void onFailedLogin(String message) {
        Platform.runLater(() ->
                ViewHelper.errorDialog("Login Failed", MessageFormat.format("Something went wrong while logging in please see the details from the user service. Error: {0}", message)));
    }

    public String getHydraUrl() {
        EnvironmentProperties environmentProperties = applicationProperties.getEnvironments().get(environmentComboBox.getValue());
        fafApiCommunicationService.initialize(environmentProperties);
        fafUserCommunicationService.initialize(environmentProperties);
        tokenService.prepare(environmentProperties);
        state = RandomStringUtils.randomAlphanumeric(50, 100);
        return String.format("%s/oauth2/auth?response_type=code&client_id=%s" +
                        "&state=%s&redirect_uri=%s" +
                        "&scope=%s",
                environmentProperties.getOauthBaseUrl(), environmentProperties.getClientId(), state, environmentProperties.getOauthRedirectUrl(), environmentProperties.getOauthScopes());
    }

    @EventListener
    public void onApiAuthorized(ApiAuthorizedEvent event) {
        root.getScene().getWindow().hide();
    }
}
