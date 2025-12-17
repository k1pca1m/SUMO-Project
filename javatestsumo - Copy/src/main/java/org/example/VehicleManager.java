package org.example;

import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.TraCIPosition;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class VehicleManager {

    public static final class Snapshot {
        public final Map<String, Point2D.Double> positions;
        public final Map<String, String> types;
        public final Map<String, String> lanes;
        public final int active;
        public final int stopped;
        public Snapshot(Map<String, Point2D.Double> p, Map<String, String> t, Map<String, String> l, int a, int s){
            positions=p; types=t; lanes=l; active=a; stopped=s;
        }
    }

    private final Logger log = Logging.log();
    private int injectedCounter=0;

    public int getInjectedCounter(){ return injectedCounter; }

    public void injectVehicle(SumoConnector sumo, String typeId, String routeId){
        String vehId=typeId+"_"+(injectedCounter++);
        try{
            sumo.addVehicle(vehId, routeId, typeId);
            log.info("Vehicle injected: "+vehId+" | type="+typeId+" | route="+routeId);
        }catch(Exception ex){
            log.severe("Vehicle.add failed: "+ex.getMessage());
        }
    }

    public Snapshot collectSnapshot(SumoConnector sumo){
        Map<String, Point2D.Double> positions=new HashMap<>();
        Map<String, String> types=new HashMap<>();
        Map<String, String> lanes=new HashMap<>();

        StringVector vIds=sumo.getVehicleIds();
        int stopped=0;

        for(int i=0;i<vIds.size();i++){
            String id=vIds.get(i);
            TraCIPosition pos=sumo.getVehiclePosition(id);
            positions.put(id, new Point2D.Double(pos.getX(), pos.getY()));

            try { lanes.put(id, sumo.getVehicleLaneId(id)); } catch(Exception ignored){}

            if(id.startsWith("car_")) types.put(id, CommonConfig.TYPE_CAR);
            else if(id.startsWith("truck_")) types.put(id, CommonConfig.TYPE_TRUCK);
            else if(id.startsWith("bus_")) types.put(id, CommonConfig.TYPE_BUS);
            else types.put(id, CommonConfig.TYPE_CAR);

            if(sumo.getVehicleSpeed(id)<0.1) stopped++;
        }

        return new Snapshot(positions, types, lanes, vIds.size(), stopped);
    }
}