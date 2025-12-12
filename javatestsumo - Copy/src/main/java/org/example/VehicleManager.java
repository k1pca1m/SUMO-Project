package org.example;

import java.util.Random;

public class VehicleManager {

    private final SumoConnector connector;

    public VehicleManager(SumoConnector connector) {
        this.connector = connector;
    }

    public void spawnVehicles(String laneKey, String typeId, int count) {
        String[] routes = connector.getRoutesForLane(laneKey);
        Random rng = connector.getRng();

        for (int i = 0; i < count; i++) {
            String routeId = routes[rng.nextInt(routes.length)];
            connector.injectVehicle(typeId, routeId);
        }
    }
}
