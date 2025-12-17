package org.example;

import org.eclipse.sumo.libtraci.*;

import java.util.logging.Logger;

public class SumoConnector {

    private final Logger log = Logging.log();

    public void preload() { Simulation.preloadLibraries(); }

    public void start(String sumoCfg, boolean gui) {
        StringVector cmd=new StringVector();
        cmd.add(gui ? "sumo-gui" : "sumo");
        cmd.add("-c"); cmd.add(sumoCfg);
        cmd.add("--start");
        cmd.add("--quit-on-end");

        log.info("Starting SUMO: " + cmd);
        Simulation.start(cmd);
        log.info("SUMO started.");
    }

    public void step() { Simulation.step(); }

    public void close() { Simulation.close(); }

    // --- time ---
    public double currentTimeSeconds() { return Simulation.getCurrentTime(); }

    // --- routes ---
    public void addRoute(String routeId, String edge1, String edge2) {
        StringVector sv=new StringVector();
        sv.add(edge1); sv.add(edge2);
        Route.add(routeId, sv);
    }

    // --- vehicles ---
    public StringVector getVehicleIds() { return Vehicle.getIDList(); }
    public TraCIPosition getVehiclePosition(String id) { return Vehicle.getPosition(id); }
    public double getVehicleSpeed(String id) { return Vehicle.getSpeed(id); }
    public String getVehicleLaneId(String id) { return Vehicle.getLaneID(id); }
    public void addVehicle(String vehId, String routeId, String typeId) { Vehicle.add(vehId, routeId, typeId); }

    // --- traffic light ---
    public String getTLState(String tlId) { return TrafficLight.getRedYellowGreenState(tlId); }
    public int getTLPhase(String tlId) { return TrafficLight.getPhase(tlId); }
    public void setTLState(String tlId, String state) { TrafficLight.setRedYellowGreenState(tlId, state); }
    public void setTLPhase(String tlId, int phase) { TrafficLight.setPhase(tlId, phase); }
    public void setTLPhaseDuration(String tlId, double seconds) { TrafficLight.setPhaseDuration(tlId, seconds); }

    // program methods not always available in every libtraci build -> reflect safely
    private static Object callTL(String methodName, Class<?>[] sig, Object[] args) {
        try {
            java.lang.reflect.Method m=TrafficLight.class.getMethod(methodName, sig);
            return m.invoke(null, args);
        } catch (Exception ignored) { return null; }
    }
    public String getTLProgram(String tlId) {
        Object res = callTL("getProgram", new Class<?>[]{String.class}, new Object[]{tlId});
        return (res instanceof String) ? (String) res : null;
    }
    public void setTLProgram(String tlId, String programId) {
        callTL("setProgram", new Class<?>[]{String.class,String.class}, new Object[]{tlId, programId});
    }
}