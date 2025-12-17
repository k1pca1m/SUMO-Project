package org.example;

import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public class TrafficManager implements Runnable {

    public static class MetricsSample {
        public final double simTime;
        public final int active;
        public final double avgWaitMin, congestion, throughput;
        public final String tlState;
        public final int tlPhase;

        public MetricsSample(double simTime,int active,double avgWaitMin,double congestion,double throughput,String tlState,int tlPhase){
            this.simTime=simTime; this.active=active; this.avgWaitMin=avgWaitMin;
            this.congestion=congestion; this.throughput=throughput;
            this.tlState=tlState; this.tlPhase=tlPhase;
        }
    }

    private final Logger log = Logging.log();

    private final MapPanel panel;
    private final JLabel activeLabel, avgWaitLabel, congestionLabel, throughputLabel, tlStateLabel;
    private final Runnable onStopped;

    private final SumoConnector sumo = new SumoConnector();
    private final VehicleManager vehicles = new VehicleManager();
    private final TrafficLightControl tlControl = new TrafficLightControl();

    private volatile boolean started=false, running=true;
    private volatile int sleepMs=50;

    private double totalWaitingSeconds=0.0;
    private int waitingSamples=0;

    private final List<MetricsSample> metricsLog = Collections.synchronizedList(new ArrayList<>());

    public TrafficManager(MapPanel panel, JLabel active, JLabel avg, JLabel cong, JLabel thr, JLabel tl, Runnable onStopped){
        this.panel=panel;
        this.activeLabel=active; this.avgWaitLabel=avg; this.congestionLabel=cong; this.throughputLabel=thr; this.tlStateLabel=tl;
        this.onStopped=onStopped;

        log.info("TrafficManager created; starting SUMO thread…");
        new Thread(this,"SUMO-Simulation-Thread").start();
    }

    public List<MetricsSample> getMetricsLog(){ return metricsLog; }

    public void startSimulation(){ started=true; log.info("Simulation START pressed."); }
    public void stopSimulation(){ running=false; log.info("Simulation STOP pressed."); }
    public void setSpeedDelay(int ms){ sleepMs=Math.max(1, ms); }

    public TrafficLightControl getTrafficLightControl(){ return tlControl; }

    public void injectN(String type, String[] routes, int n){
        log.info("Inject: " + type + " x" + n);
        for(int i=0;i<n;i++){
            String route = routes[CommonConfig.RNG.nextInt(routes.length)];
            vehicles.injectVehicle(sumo, type, route);
        }
    }

    public void exportCSV(File exportFile) throws Exception {
        if(metricsLog.isEmpty()) throw new IllegalStateException("No data recorded yet.");
        try(PrintWriter out=new PrintWriter(new FileWriter(exportFile))){
            out.println("simTime,activeVehicles,avgWaitMinutes,congestion,throughput,tlPhase,tlState");
            synchronized(metricsLog){
                for(MetricsSample s: metricsLog){
                    out.printf(Locale.US,"%.2f,%d,%.4f,%.4f,%.2f,%d,%s%n",
                            s.simTime,s.active,s.avgWaitMin,s.congestion,s.throughput,s.tlPhase,
                            "\"" + s.tlState.replace("\"","\"\"") + "\"");
                }
            }
        }
    }

    private void addRoutesOnce(){
        String[] ids = {
                "r_l1_left","r_l1_straight","r_l1_right",
                "r_l2_left","r_l2_straight","r_l2_right",
                "r_l3_left","r_l3_straight","r_l3_right",
                "r_l4_left","r_l4_straight","r_l4_right"
        };
        String[][] edges = {
                {"E0","-E2"},{"E0","E0.45"},{"E0","-E1"},
                {"-E0","-E1"},{"-E0","-E0.40"},{"-E0","-E2"},
                {"E1","-E0.40"},{"E1","-E2"},{"E1","E0.45"},
                {"E2","E0.45"},{"E2","-E1"},{"E2","-E0.40"}
        };
        for(int i=0;i<ids.length;i++) sumo.addRoute(ids[i], edges[i][0], edges[i][1]);
        log.info("Routes added.");
    }

    @Override public void run() {
        long tick=0;
        try{
            sumo.preload();
            sumo.start(CommonConfig.SUMO_CFG, CommonConfig.USE_GUI);

            tlControl.captureInitialProgramIfPossible(sumo);
            addRoutesOnce();

            while(running){
                if(!started){ Thread.sleep(50); continue; }

                sumo.step(); tick++;
                Thread.sleep(sleepMs);

                VehicleManager.Snapshot snap = vehicles.collectSnapshot(sumo);

                double congestion = snap.active>0 ? (double)snap.stopped/snap.active : 0.0;
                totalWaitingSeconds += snap.stopped; waitingSamples++;
                double avgWaitMin = waitingSamples>0 ? (totalWaitingSeconds/waitingSamples)/60.0 : 0.0;

                double simTime = sumo.currentTimeSeconds();
                double simHours = simTime/3600.0;
                double throughput = simHours>0 ? vehicles.getInjectedCounter()/simHours : 0.0;

                String programState = sumo.getTLState(CommonConfig.TL_ID);
                int phase = sumo.getTLPhase(CommonConfig.TL_ID);
                String effectiveState = tlControl.computeAndApplyEffectiveState(sumo, programState);

                if(tick%40==0){
                    log.info(String.format(Locale.US,
                            "tick=%d | simTime=%.1fs | active=%d | avgWait=%.2fmin | cong=%.2f | thr=%.0f v/h | phase=%d",
                            tick, simTime, snap.active, avgWaitMin, congestion, throughput, phase));
                }

                String tlText="<html>TL Phase: "+phase+"<br>State: "+effectiveState+"</html>";
                metricsLog.add(new MetricsSample(simTime, snap.active, avgWaitMin, congestion, throughput, effectiveState, phase));

                SwingUtilities.invokeLater(() -> {
                    panel.updateVehicles(snap.positions, snap.types, snap.lanes);
                    panel.setTrafficLightState(effectiveState);

                    activeLabel.setText("Active Vehicles: "+snap.active);
                    avgWaitLabel.setText(String.format("Avg. Wait Time: %.2f min", avgWaitMin));
                    congestionLabel.setText(String.format("Congestion Index: %.2f", congestion));
                    throughputLabel.setText(String.format("Throughput: %.0f v/h", throughput));
                    tlStateLabel.setText(tlText);
                });
            }

            log.info("Stopping SUMO…");
            try{ sumo.close(); }catch(Exception e){ log.severe("Simulation.close failed: "+e.getMessage()); }
            log.info("SUMO closed.");
            if(onStopped!=null) SwingUtilities.invokeLater(onStopped);

        } catch (Exception ex){
            log.severe("Simulation thread crashed: "+ex.getMessage());
            if(onStopped!=null) SwingUtilities.invokeLater(onStopped);
        }
    }
}