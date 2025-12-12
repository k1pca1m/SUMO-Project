package org.example;

public class TrafficLightController {

    private final SumoConnector connector;

    public TrafficLightController(SumoConnector connector) {
        this.connector = connector;
    }

    public void setGreen() {
        connector.setTrafficLightPhase(0);
    }

    public void setRed() {
        connector.setTrafficLightPhase(2);
    }
}
