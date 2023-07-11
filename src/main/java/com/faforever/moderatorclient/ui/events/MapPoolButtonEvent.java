package com.faforever.moderatorclient.ui.events;

import java.awt.event.ActionEvent;

public class MapPoolButtonEvent extends ActionEvent {
    public MapPoolButtonEvent(Object source, int id, String command) {
        super(source, id, command);
    }
}
