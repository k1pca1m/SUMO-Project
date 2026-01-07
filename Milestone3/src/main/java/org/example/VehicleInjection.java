// VehicleInjection.java
package org.example;

import org.eclipse.sumo.libtraci.*;

import javax.swing.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public class VehicleInjection {

    public static class RouteDef {
        public final String baseId;
        public String name;
        public final String fromEdge;
        public final String toEdge;
        public final List<String> viaEdges;

        public RouteDef(String baseId, String name, String fromEdge, String toEdge, List<String> viaEdges) {
            this.baseId = baseId;
            this.name = name;
            this.fromEdge = fromEdge;
            this.toEdge = toEdge;
            this.viaEdges = viaEdges == null ? new ArrayList<>() : viaEdges;
        }

        public List<String> waypoints() {
            List<String> pts = new ArrayList<>();
            pts.add(fromEdge);
            pts.addAll(viaEdges);
            pts.add(toEdge);
            return pts;
        }

        @Override public String toString() { return name; }
    }

    public final LinkedHashMap<String, RouteDef> TRIP_ROUTES = new LinkedHashMap<>();
    public final List<RouteDef> ALLOWED_ROUTES = Collections.synchronizedList(new ArrayList<>());

    private final Set<String> installedRoutes = ConcurrentHashMap.newKeySet();
    private int injectedCounter = 0;

    public boolean dropSecondRoute = true;

    public static String sanitizeId(String s) {
        if (s == null) return "x";
        return s.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    public void loadTripRoutesFromRou(String sumocfgPath) {
        TRIP_ROUTES.clear();

        String routeFiles = LiveConnectionSumo.readRouteFilesFromSumocfg(sumocfgPath);
        if (routeFiles == null || routeFiles.isBlank()) {
            Logging.LOG.warning("sumocfg has no <route-files>.");
            return;
        }

        String[] parts = routeFiles.split("[,\\s]+");
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            File rou = LiveConnectionSumo.resolveRelativeToSumocfg(sumocfgPath, p.trim());
            if (!rou.exists()) {
                Logging.LOG.warning("Route file not found: " + rou.getPath());
                continue;
            }
            parseTrips(rou);
        }

        Logging.LOG.info("Trips loaded: " + TRIP_ROUTES.size());
    }

    private void parseTrips(File rouFile) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setExpandEntityReferences(false);

            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(rouFile);
            doc.getDocumentElement().normalize();

            NodeList trips = doc.getElementsByTagName("trip");
            for (int i = 0; i < trips.getLength(); i++) {
                Node n = trips.item(i);
                if (!(n instanceof Element)) continue;
                Element t = (Element) n;

                String from = t.getAttribute("from");
                String to   = t.getAttribute("to");
                String via  = t.getAttribute("via");
                String id   = t.getAttribute("id");

                if (from == null || from.isBlank() || to == null || to.isBlank()) continue;
                if (TRIP_ROUTES.containsKey(from)) continue;

                List<String> viaEdges = new ArrayList<>();
                if (via != null && !via.isBlank()) {
                    for (String ve : via.trim().split("\\s+")) {
                        if (ve != null && !ve.isBlank()) viaEdges.add(ve.trim());
                    }
                }

                String baseId = "rt_trip_" + sanitizeId(from) + "_" + sanitizeId(id == null ? ("t" + i) : id);
                String label = "TRIP: " + from + " \u2192 " + to;
                TRIP_ROUTES.put(from, new RouteDef(baseId, label, from.trim(), to.trim(), viaEdges));
            }
        } catch (Exception ex) {
            Logging.LOG.log(Level.WARNING, "Failed parsing rou file: " + rouFile.getPath(), ex);
        }
    }

    public void rebuildAllowedRoutesAndDropdown(JComboBox<RouteDef> routeCombo) {
        ALLOWED_ROUTES.clear();

        for (RouteDef rd : TRIP_ROUTES.values()) {
            List<String> edges = computeEdgesForRouteDef(rd, LiveConnectionSumo.TYPE_CAR);
            if (edges != null && !edges.isEmpty()) ALLOWED_ROUTES.add(rd);
            else Logging.LOG.warning("Dropping invalid trip route (car can't route): " + rd.name);
        }

        if (dropSecondRoute && ALLOWED_ROUTES.size() >= 2) {
            RouteDef removed = ALLOWED_ROUTES.remove(1);
            Logging.LOG.warning("Removed 2nd dropdown route (forced): " + removed.name);
        }

        String[] niceNames = {"Route 1", "Route 2", "Route 3", "Route 4"};
        for (int i = 0; i < ALLOWED_ROUTES.size(); i++) {
            ALLOWED_ROUTES.get(i).name = (i < niceNames.length) ? niceNames[i] : ("Route " + (i + 1));
        }

        SwingUtilities.invokeLater(() -> {
            DefaultComboBoxModel<RouteDef> model = new DefaultComboBoxModel<>();
            for (RouteDef rd : ALLOWED_ROUTES) model.addElement(rd);
            routeCombo.setModel(model);
            routeCombo.setEnabled(model.getSize() > 0);
            if (model.getSize() > 0) routeCombo.setSelectedIndex(0);
        });

        Logging.LOG.info("Dropdown built. Routes count=" + ALLOWED_ROUTES.size());
    }

    // ===== build a temp routes file with only vTypes (manual injection) =====
    public String buildRoutesFileWithVTypesOnly() throws IOException {
        File tmp = File.createTempFile("manual_only_", ".rou.xml");
        tmp.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8"))) {
            pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            pw.println("<routes xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
            pw.println("        xsi:noNamespaceSchemaLocation=\"http://sumo.dlr.de/xsd/routes_file.xsd\">");
            pw.println("  <!-- Manual injection only -->");
            pw.println("  <vType id=\"car\"   vClass=\"passenger\" length=\"5.0\"  accel=\"2.6\" decel=\"4.5\" maxSpeed=\"33\"/>");
            pw.println("  <vType id=\"truck\" vClass=\"truck\"     length=\"8.0\"  accel=\"1.3\" decel=\"4.0\" maxSpeed=\"25\"/>");
            pw.println("  <vType id=\"bus\"   vClass=\"bus\"       length=\"12.0\" accel=\"1.1\" decel=\"4.0\" maxSpeed=\"22\"/>");
            pw.println("</routes>");
        }
        Logging.LOG.info("Temp manual routes file: " + tmp.getAbsolutePath());
        return tmp.getAbsolutePath();
    }

    private static Object tryInvokeRet(Class<?> clazz, String methodName, Class<?>[] sig, Object[] args) {
        try {
            Method m = clazz.getMethod(methodName, sig);
            return m.invoke(null, args);
        } catch (Exception ignored) { return null; }
    }

    private StringVector extractEdgesFromFindRouteResult(Object obj) {
        if (obj == null) return null;
        if (obj instanceof StringVector) return (StringVector) obj;

        try {
            Method m = obj.getClass().getMethod("getEdges");
            Object edges = m.invoke(obj);
            if (edges instanceof StringVector) return (StringVector) edges;
        } catch (Exception ignored) {}

        try {
            Method m = obj.getClass().getMethod("getEdgeList");
            Object edges = m.invoke(obj);
            if (edges instanceof StringVector) return (StringVector) edges;
        } catch (Exception ignored) {}

        return null;
    }

    private StringVector findRouteEdges(String fromEdge, String toEdge, String vTypeId) {
        Object r2 = tryInvokeRet(Simulation.class, "findRoute",
                new Class<?>[]{String.class, String.class, String.class},
                new Object[]{fromEdge, toEdge, vTypeId});
        StringVector sv = extractEdgesFromFindRouteResult(r2);
        if (sv != null && sv.size() > 0) return sv;

        Object r3 = tryInvokeRet(Simulation.class, "findRoute",
                new Class<?>[]{String.class, String.class, String.class, double.class},
                new Object[]{fromEdge, toEdge, vTypeId, Simulation.getCurrentTime()});
        sv = extractEdgesFromFindRouteResult(r3);
        if (sv != null && sv.size() > 0) return sv;

        return null;
    }

    private boolean edgeAllowsVType(String edgeId, String vTypeId) {
        try {
            int ln = Edge.getLaneNumber(edgeId);
            if (ln <= 0) return true;

            String laneId = edgeId + "_0";
            Method m = Lane.class.getMethod("getAllowed", String.class);
            Object allowedObj = m.invoke(null, laneId);

            if (!(allowedObj instanceof StringVector)) return true;
            StringVector allowed = (StringVector) allowedObj;
            if (allowed.size() == 0) return true;

            String want = "passenger";
            if (LiveConnectionSumo.TYPE_TRUCK.equals(vTypeId)) want = "truck";
            if (LiveConnectionSumo.TYPE_BUS.equals(vTypeId)) want = "bus";

            for (int i = 0; i < allowed.size(); i++) {
                if (want.equalsIgnoreCase(allowed.get(i))) return true;
            }
            return false;
        } catch (Exception ex) {
            return true;
        }
    }

    private List<String> computeEdgesForRouteDef(RouteDef rd, String vTypeId) {
        List<String> pts = rd.waypoints();
        List<String> full = new ArrayList<>();

        for (int i = 0; i < pts.size() - 1; i++) {
            String a = pts.get(i);
            String b = pts.get(i + 1);

            if (!edgeAllowsVType(a, vTypeId) || !edgeAllowsVType(b, vTypeId)) return null;

            StringVector seg = findRouteEdges(a, b, vTypeId);
            if (seg == null || seg.size() == 0) return null;

            for (int k = 0; k < seg.size(); k++) {
                String e = seg.get(k);
                if (full.isEmpty()) full.add(e);
                else if (!full.get(full.size() - 1).equals(e)) full.add(e);
            }
        }
        return full;
    }

    private boolean ensureRouteInstalled(RouteDef rd, String vTypeId) {
        String routeId = rd.baseId + "_" + vTypeId;
        if (installedRoutes.contains(routeId)) return true;

        List<String> computed = computeEdgesForRouteDef(rd, vTypeId);
        if (computed == null || computed.isEmpty()) return false;

        try {
            StringVector sv = new StringVector();
            for (String e : computed) sv.add(e);
            Route.add(routeId, sv);
            installedRoutes.add(routeId);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void addVehicleRobust(String vehId, String routeId, String typeId) throws Exception {
        boolean used = false;
        try {
            Method m = Vehicle.class.getMethod("add",
                    String.class, String.class, String.class,
                    double.class, String.class, double.class, double.class);
            m.invoke(null, vehId, routeId, typeId, Simulation.getCurrentTime(), "best", 0.0, -1.0);
            used = true;
        } catch (Exception ignored) {}

        if (!used) Vehicle.add(vehId, routeId, typeId);
    }

    public void injectVehicles(JFrame owner, boolean ready, RouteDef rd, String typeId, int n) {
        if (!ready) {
            JOptionPane.showMessageDialog(owner, "Not ready yet. Press Start Simulation first.",
                    "Not Ready", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (rd == null) return;

        if (!ensureRouteInstalled(rd, typeId)) {
            JOptionPane.showMessageDialog(owner,
                    "This route is not valid for type: " + typeId + "\nTry another route.",
                    "Route not possible", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String routeId = rd.baseId + "_" + typeId;

        for (int i = 0; i < n; i++) {
            String vehId = typeId + "_" + (injectedCounter++);
            try {
                addVehicleRobust(vehId, routeId, typeId);
            } catch (Exception ex) {
                Logging.LOG.log(Level.SEVERE, "Vehicle.add failed for " + vehId + " route=" + routeId, ex);
            }
        }
    }
}
