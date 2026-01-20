// ===================== VehicleInjection.java =====================
package org.example;

import org.eclipse.sumo.libtraci.*;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.w3c.dom.*;

public final class VehicleInjection {

    private VehicleInjection() {}

    // ===================== ROUTE DEFINITIONS =====================
    public static class RouteDef {
        public final String baseId;
        public String name;
        public final String fromEdge;
        public final String toEdge;
        public final java.util.List<String> viaEdges;

        public RouteDef(String baseId, String name, String fromEdge, String toEdge, java.util.List<String> viaEdges) {
            this.baseId = baseId;
            this.name = name;
            this.fromEdge = fromEdge;
            this.toEdge = toEdge;
            this.viaEdges = viaEdges == null ? new ArrayList<>() : viaEdges;
        }

        @Override public String toString() { return name; }
    }

    static class RouteVariant {
        final String routeId;
        final String label;
        final java.util.List<String> edges;
        final double score;
        RouteVariant(String routeId, String label, java.util.List<String> edges, double score) {
            this.routeId = routeId; this.label = label; this.edges = edges; this.score = score;
        }
    }

    public static final LinkedHashMap<String, RouteDef> TRIP_ROUTES = new LinkedHashMap<>();
    public static final java.util.List<RouteDef> ALLOWED_ROUTES = Collections.synchronizedList(new ArrayList<>());

    private static final Random RNG = new Random();

    // long-route cache + installed routes
    private static final Set<String> installedRoutes = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, java.util.List<RouteVariant>> variantsByScenarioType = new ConcurrentHashMap<>();
    private static final ArrayList<String> viaPool = new ArrayList<>();

    // 30/40/20/10
    private static final double[] BRANCH_P = new double[]{0.30, 0.40, 0.20, 0.10};

    private static final int LONG_MIN_EDGES = 14;
    private static final int LONG_TRIES = 800;
    private static final int PREFIX_EDGES_FOR_SPLIT = 4;
    private static final boolean DISALLOW_EDGE_REPEATS = true;

    private static volatile boolean ready = false;

    public static boolean isReady() { return ready; }

    // ===================== SUMOCFG PARSING =====================
    private static String readRouteFilesFromSumocfg(String sumocfgPath) {
        try {
            File f = new File(sumocfgPath);
            if (!f.exists()) return null;

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setExpandEntityReferences(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(f);
            doc.getDocumentElement().normalize();

            NodeList list = doc.getElementsByTagName("route-files");
            if (list.getLength() > 0) {
                Element e = (Element) list.item(0);
                String v = e.getAttribute("value");
                if (v != null && !v.isBlank()) return v.trim();
            }
        } catch (Exception ex) {
            Logging.LOG.log(java.util.logging.Level.WARNING, "Failed reading route-files from sumocfg", ex);
        }
        return null;
    }

    private static File resolveRelativeToSumocfg(String pathMaybeRelative) {
        File f = new File(pathMaybeRelative);
        if (f.exists()) return f;

        File cfg = new File(Main.SUMOCFG_PATH);
        File baseDir = cfg.getParentFile();
        if (baseDir == null) baseDir = new File(".");
        File alt = new File(baseDir, pathMaybeRelative);
        if (alt.exists()) return alt;

        return f;
    }

    private static String sanitizeId(String s) {
        if (s == null) return "x";
        return s.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    // ===================== LOAD TRIPS FROM ROU =====================
    public static void loadTripRoutesFromRou() {
        TRIP_ROUTES.clear();

        String routeFiles = readRouteFilesFromSumocfg(Main.SUMOCFG_PATH);
        if (routeFiles == null || routeFiles.isBlank()) {
            Logging.LOG.warning("sumocfg has no <route-files>. Put your final.rou.xml there.");
            return;
        }

        String[] parts = routeFiles.split("[,\\s]+");
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            File rou = resolveRelativeToSumocfg(p.trim());
            if (!rou.exists()) {
                Logging.LOG.warning("Route file not found: " + rou.getPath());
                continue;
            }
            parseTrips(rou);
        }

        Logging.LOG.info("Trips loaded: " + TRIP_ROUTES.size());
    }

    /**
     * Why parse XML manually instead of asking SUMO?
     * 1. Performance: Asking SUMO via TraCI for every edge in the map is slow (TCP overhead).
     * 2. Completeness: We need the full route definitions even before the simulation starts.
     * 为什么手动解析 XML 而不是询问 SUMO？
     * 1. 性能：通过 TraCI 询问地图中的每条边很慢（TCP 开销）。
     * 2. 完整性：甚至在仿真开始之前，我们就需要完整的路线定义。
     */
    private static void parseTrips(File rouFile) {
        try {

            // Standard Java DOM parsing to read .rou.xml files.
            // 使用标准的 Java DOM 解析读取 .rou.xml 文件。
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

            // Security: Disable DTD loading to prevent XXE attacks and speed up parsing.
            // 安全性：禁用 DTD 加载以防止 XXE 攻击并加快解析速度。
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

                java.util.List<String> viaEdges = new ArrayList<>();
                if (via != null && !via.isBlank()) {
                    for (String ve : via.trim().split("\\s+")) {
                        if (ve != null && !ve.isBlank()) viaEdges.add(ve.trim());
                    }
                }

                String baseId = "rt_trip_" + sanitizeId(from) + "_" + sanitizeId(id == null ? ("t" + i) : id);
                String label = "TRIP: " + from + " -> " + to;
                TRIP_ROUTES.put(from, new RouteDef(baseId, label, from.trim(), to.trim(), viaEdges));
            }
        } catch (Exception ex) {
            Logging.LOG.log(java.util.logging.Level.WARNING, "Failed parsing rou file: " + rouFile.getPath(), ex);
        }
    }

    // ===================== Reflection helpers =====================

    // Using Reflection (Method.invoke) is crucial here. The libtraci library version might differ
    // on different machines. Using reflection prevents `NoSuchMethodError` crashes if the API signature changes slightly.
    // 在这里使用反射 (Method.invoke) 至关重要。libtraci 库的版本在不同机器上可能不同。
    // 如果 API 签名略有变化，使用反射可以防止 `NoSuchMethodError` 崩溃。
    private static Object tryInvokeRet(Class<?> clazz, String methodName, Class<?>[] sig, Object[] args) {
        try {
            Method m = clazz.getMethod(methodName, sig);
            return m.invoke(null, args);
        } catch (Exception ignored) { return null; }
    }

    private static Double tryInvokeDouble(Class<?> clazz, String methodName, Class<?>[] sig, Object[] args) {
        try {
            Method m = clazz.getMethod(methodName, sig);
            Object o = m.invoke(null, args);
            if (o instanceof Number) return ((Number) o).doubleValue();
        } catch (Exception ignored) {}
        return null;
    }

    private static Integer tryInvokeInt(Class<?> clazz, String methodName, Class<?>[] sig, Object[] args) {
        try {
            Method m = clazz.getMethod(methodName, sig);
            Object o = m.invoke(null, args);
            if (o instanceof Number) return ((Number) o).intValue();
        } catch (Exception ignored) {}
        return null;
    }

    // ===================== Long-route building helpers =====================
    private static void buildViaPoolOnce() {
        if (!viaPool.isEmpty()) return;

        HashSet<String> pool = new HashSet<>();
        for (RouteDef rd : TRIP_ROUTES.values()) {
            addEdgeToPool(pool, rd.fromEdge);
            addEdgeToPool(pool, rd.toEdge);
            if (rd.viaEdges != null) for (String v : rd.viaEdges) addEdgeToPool(pool, v);
        }
        java.util.List<MapVisualisation.RoadGeom> geoms = MapVisualisation.getRoadGeoms();
        if (geoms != null) {
            for (MapVisualisation.RoadGeom rg : geoms) addEdgeToPool(pool, rg.edgeId);
        }

        viaPool.addAll(pool);
        viaPool.removeIf(e -> e == null || e.isBlank() || e.startsWith(":"));
        Collections.shuffle(viaPool, RNG);

        Logging.LOG.info("Via-edge pool built: " + viaPool.size());
    }

    private static void addEdgeToPool(Set<String> pool, String e) {
        if (e == null) return;
        e = e.trim();
        if (e.isBlank()) return;
        if (e.startsWith(":")) return;
        pool.add(e);
    }

    private static java.util.List<String> toList(StringVector sv) {
        if (sv == null || sv.size() == 0) return null;
        ArrayList<String> out = new ArrayList<>(sv.size());
        for (int i = 0; i < sv.size(); i++) {
            String e = sv.get(i);
            if (e != null && !e.isBlank() && !e.startsWith(":")) out.add(e);
        }
        return out.isEmpty() ? null : out;
    }

    private static StringVector extractEdgesFromFindRouteResult(Object obj) {
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

    private static StringVector findRouteEdges(String fromEdge, String toEdge, String vTypeId) {
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

    private static boolean edgeAllowsVType(String edgeId, String vTypeId) {
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
            if (Main.TYPE_TRUCK.equals(vTypeId)) want = "truck";
            if (Main.TYPE_BUS.equals(vTypeId)) want = "bus";

            for (int i = 0; i < allowed.size(); i++) {
                if (want.equalsIgnoreCase(allowed.get(i))) return true;
            }
            return false;
        } catch (Exception ex) {
            return true;
        }
    }

    private static double safeEdgeLengthMeters(String edgeId) {
        try {
            String lane0 = edgeId + "_0";
            Double len = tryInvokeDouble(Lane.class, "getLength", new Class<?>[]{String.class}, new Object[]{lane0});
            if (len != null && len > 0 && Double.isFinite(len)) return len;
        } catch (Exception ignore) {}
        return 5.0;
    }

    private static double scoreRouteEdges(java.util.List<String> edges) {
        if (edges == null || edges.isEmpty()) return -1;
        double sum = 0.0;
        for (String e : edges) sum += safeEdgeLengthMeters(e);
        sum += edges.size() * 2.0;
        return sum;
    }

    private static void appendUnique(java.util.List<String> out, java.util.List<String> seg) {
        if (seg == null || seg.isEmpty()) return;
        for (String e : seg) {
            if (out.isEmpty()) out.add(e);
            else if (!out.get(out.size() - 1).equals(e)) out.add(e);
        }
    }

    private static boolean hasRepeats(java.util.List<String> edges) {
        HashSet<String> seen = new HashSet<>();
        for (String e : edges) {
            if (seen.contains(e)) return true;
            seen.add(e);
        }
        return false;
    }

    private static boolean sharesPrefix(java.util.List<String> edges, java.util.List<String> prefix) {
        if (edges == null || prefix == null) return false;
        if (edges.size() < prefix.size()) return false;
        for (int i = 0; i < prefix.size(); i++) {
            if (!Objects.equals(edges.get(i), prefix.get(i))) return false;
        }
        return true;
    }

    /**
     * Logic to create variety in traffic.
     * Instead of all cars taking the exact shortest path, this method generates variations (long routes).
     * It caches results in `variantsByScenarioType` (ConcurrentHashMap) so we don't recalculate heavy routing logic every click.
     * 交通多样化逻辑。
     * 此方法生成变体（长路线），而不是让所有车辆都走完全相同的最短路径。
     * 它将结果缓存在 `variantsByScenarioType` (ConcurrentHashMap) 中，因此我们不必在每次点击时重新计算繁重的路由逻辑。
     */
    private static java.util.List<RouteVariant> buildAndInstallLongVariants(RouteDef rd, String typeId) {

        // 1. Caching Strategy / 缓存策略
        // Why? Route calculation (Dijkstra/A*) is CPU intensive. We cache the results to keep the UI responsive.
        // 为什么？路径计算（Dijkstra/A*）非常消耗 CPU。我们缓存结果以保持 UI 响应流畅。
        String key = rd.baseId + "|" + typeId;
        java.util.List<RouteVariant> cached = variantsByScenarioType.get(key);
        if (cached != null && cached.size() >= 4) return cached;

        if (!edgeAllowsVType(rd.fromEdge, typeId) || !edgeAllowsVType(rd.toEdge, typeId)) {
            return Collections.emptyList();
        }

        buildViaPoolOnce();

        java.util.List<String> base = toList(findRouteEdges(rd.fromEdge, rd.toEdge, typeId));
        if (base == null || base.size() < 6) {
            if (base == null) base = new ArrayList<>();
        }

        int splitIndex = Math.min(Math.max(1, PREFIX_EDGES_FOR_SPLIT - 1), Math.max(1, base.size() - 3));
        java.util.List<String> prefix = new ArrayList<>();
        if (base.size() > 0) {
            for (int i = 0; i <= splitIndex && i < base.size(); i++) prefix.add(base.get(i));
        }
        String splitEdge = (prefix.isEmpty()) ? rd.fromEdge : prefix.get(prefix.size() - 1);

        ArrayList<RouteVariant> best = new ArrayList<>();
        HashSet<String> usedSignatures = new HashSet<>();
        HashSet<String> usedNextEdge = new HashSet<>();

        int poolN = viaPool.size();
        if (poolN < 10) {
            Logging.LOG.warning("Via pool very small (" + poolN + "). Long route variety may be limited.");
        }

        // 2. The "Via-Point" Algorithm / "中间点"算法
        // We try to find a path: Start -> ViaPoint -> End.
        // By picking random 'ViaPoints', we force SUMO to calculate alternative routes.
        // 我们尝试找到一条路径：起点 -> 中间点 -> 终点。
        // 通过选取随机的“中间点”，我们强制 SUMO 计算替代路线。
        for (int t = 0; t < LONG_TRIES; t++) {
            String via1 = viaPool.get(RNG.nextInt(Math.max(1, poolN)));
            String via2 = viaPool.get(RNG.nextInt(Math.max(1, poolN)));

            if (via1 == null || via2 == null) continue;
            via1 = via1.trim(); via2 = via2.trim();
            if (via1.isBlank() || via2.isBlank()) continue;
            if (via1.startsWith(":") || via2.startsWith(":")) continue;
            if (via1.equals(via2)) continue;
            if (via1.equals(splitEdge) || via2.equals(splitEdge)) continue;
            if (via1.equals(rd.toEdge) || via2.equals(rd.toEdge)) continue;

            if (!edgeAllowsVType(via1, typeId) || !edgeAllowsVType(via2, typeId)) continue;

            // 3. Asking SUMO for sub-paths / 询问 SUMO 子路径
            // We use Simulation.findRoute() which triggers SUMO's internal routing engine.
            // This ensures the generated path is physically connected and valid.
            // 我们使用 Simulation.findRoute()，这会触发 SUMO 内部的路由引擎。
            // 这确保生成的路径在物理上是连通且有效的。
            java.util.List<String> seg1 = toList(findRouteEdges(splitEdge, via1, typeId));
            if (seg1 == null || seg1.size() < 2) continue;

            java.util.List<String> seg2 = toList(findRouteEdges(via1, via2, typeId));
            if (seg2 == null || seg2.size() < 2) continue;

            java.util.List<String> seg3 = toList(findRouteEdges(via2, rd.toEdge, typeId));
            if (seg3 == null || seg3.size() < 2) continue;

            ArrayList<String> combined = new ArrayList<>(prefix.size() + seg1.size() + seg2.size() + seg3.size());
            appendUnique(combined, prefix);
            appendUnique(combined, seg1);
            appendUnique(combined, seg2);
            appendUnique(combined, seg3);

            if (combined.isEmpty() || !rd.toEdge.equals(combined.get(combined.size() - 1))) continue;
            if (!prefix.isEmpty() && !sharesPrefix(combined, prefix)) continue;
            if (combined.size() < LONG_MIN_EDGES) continue;
            if (DISALLOW_EDGE_REPEATS && hasRepeats(combined)) continue;

            String nextEdge = (combined.size() > prefix.size()) ? combined.get(prefix.size()) : "";
            String sig = String.join(">", combined);
            if (usedSignatures.contains(sig)) continue;

            if (nextEdge != null && !nextEdge.isBlank() && usedNextEdge.contains(nextEdge) && best.size() < 4) {
                continue;
            }

            // 4. Scoring / 评分机制
            // We calculate a score based on length. We want routes that are long but not "loops".
            // 我们根据长度计算分数。我们想要较长的路线，但不能是“死循环”。
            double sc = scoreRouteEdges(combined);
            String rid = rd.baseId + "_LONG_" + typeId + "_" + Math.abs(sig.hashCode());
            RouteVariant var = new RouteVariant(rid, "V", combined, sc);

            best.add(var);
            best.sort((a, b) -> Double.compare(b.score, a.score));

            usedSignatures.add(sig);
            if (nextEdge != null && !nextEdge.isBlank()) usedNextEdge.add(nextEdge);

            while (best.size() > 10) best.remove(best.size() - 1);
        }

        // fallback: 1 via
        if (best.size() < 4 && poolN > 0) {
            for (int t = 0; t < LONG_TRIES; t++) {
                String via1 = viaPool.get(RNG.nextInt(Math.max(1, poolN)));
                if (via1 == null) continue;
                via1 = via1.trim();
                if (via1.isBlank() || via1.startsWith(":")) continue;
                if (via1.equals(splitEdge) || via1.equals(rd.toEdge)) continue;
                if (!edgeAllowsVType(via1, typeId)) continue;

                /**
                 * Logic to create variety in traffic.
                 * Instead of all cars taking the exact shortest path, this method generates variations (long routes).
                 * It caches results in `variantsByScenarioType` (ConcurrentHashMap) so we don't recalculate heavy routing logic every click.
                 * 交通多样化逻辑。
                 * 此方法生成变体（长路线），而不是让所有车辆都走完全相同的最短路径。
                 * 它将结果缓存在 `variantsByScenarioType` (ConcurrentHashMap) 中，因此我们不必在每次点击时重新计算繁重的路由逻辑。
                 */
                java.util.List<String> seg1 = toList(findRouteEdges(splitEdge, via1, typeId));
                if (seg1 == null || seg1.size() < 2) continue;

                java.util.List<String> seg2 = toList(findRouteEdges(via1, rd.toEdge, typeId));
                if (seg2 == null || seg2.size() < 2) continue;

                ArrayList<String> combined = new ArrayList<>();
                appendUnique(combined, prefix);
                appendUnique(combined, seg1);
                appendUnique(combined, seg2);

                if (combined.isEmpty() || !rd.toEdge.equals(combined.get(combined.size() - 1))) continue;
                if (combined.size() < Math.max(10, LONG_MIN_EDGES - 2)) continue;
                if (DISALLOW_EDGE_REPEATS && hasRepeats(combined)) continue;

                String sig = String.join(">", combined);
                if (usedSignatures.contains(sig)) continue;

                double sc = scoreRouteEdges(combined);
                String rid = rd.baseId + "_LONG1_" + typeId + "_" + Math.abs(sig.hashCode());
                best.add(new RouteVariant(rid, "V", combined, sc));
                best.sort((a, b) -> Double.compare(b.score, a.score));
                usedSignatures.add(sig);
                if (best.size() >= 8) break;
            }
        }

        // last fallback: base
        if (best.isEmpty() && base != null && base.size() >= 2) {
            String sig = String.join(">", base);
            String rid = rd.baseId + "_BASE_" + typeId + "_" + Math.abs(sig.hashCode());
            best.add(new RouteVariant(rid, "BASE", new ArrayList<>(base), scoreRouteEdges(base)));
        }

        // top 4 => A/B/C/D
        best.sort((a, b) -> Double.compare(b.score, a.score));
        ArrayList<RouteVariant> top = new ArrayList<>();
        String[] names = new String[]{"A", "B", "C", "D"};
        for (int i = 0; i < Math.min(4, best.size()); i++) {
            RouteVariant v = best.get(i);
            String rid = rd.baseId + "_V" + (i + 1) + "_" + typeId;
            top.add(new RouteVariant(rid, "Variant " + names[i], v.edges, v.score));
        }

        // install
        for (RouteVariant v : top) {
            if (installedRoutes.contains(v.routeId)) continue;
            try {
                StringVector sv = new StringVector();
                for (String e : v.edges) sv.add(e);
                Route.add(v.routeId, sv);
                installedRoutes.add(v.routeId);
            } catch (Exception ex) {
                Logging.LOG.warning("Route.add failed for " + v.routeId + " (" + v.label + "): " + ex.getMessage());
            }
        }

        variantsByScenarioType.put(key, top);

        if (!top.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Built ").append(top.size()).append(" long variants for ").append(rd.name)
                    .append(" type=").append(typeId).append(" (dest=").append(rd.toEdge).append(")");
            for (RouteVariant v : top) sb.append(" | ").append(v.label).append(":edges=").append(v.edges.size());
            Logging.LOG.info(sb.toString());
        }

        return top;
        // Returns top 4 distinct route variants (A, B, C, D) / 返回前4个不同的路线变体
    }

    private static int pickVariantIndex() {
        double r = RNG.nextDouble();
        double acc = 0.0;
        for (int i = 0; i < BRANCH_P.length; i++) {
            acc += BRANCH_P[i];
            if (r <= acc) return i;
        }
        return BRANCH_P.length - 1;
    }

    // ===================== Build dropdown scenarios (called after SUMO starts) =====================
    public static void rebuildAllowedRoutesAndDropdown(JComboBox<RouteDef> routeCombo) {
        ALLOWED_ROUTES.clear();

        for (RouteDef rd : TRIP_ROUTES.values()) {
            java.util.List<String> base = toList(findRouteEdges(rd.fromEdge, rd.toEdge, Main.TYPE_CAR));
            if (base != null && !base.isEmpty()) ALLOWED_ROUTES.add(rd);
            else Logging.LOG.warning("Dropping invalid trip route (car can't route): " + rd.name);
        }

        if (Main.DROP_SECOND_ROUTE && ALLOWED_ROUTES.size() >= 2) {
            RouteDef removed = ALLOWED_ROUTES.remove(1);
            Logging.LOG.warning("Removed 2nd dropdown route (forced): " + removed.name);
        }

        String[] niceNames = {"Route 1", "Route 2", "Route 3", "Route 4", "Route 5", "Route 6"};
        for (int i = 0; i < ALLOWED_ROUTES.size(); i++) {
            if (i < niceNames.length) ALLOWED_ROUTES.get(i).name = niceNames[i];
            else ALLOWED_ROUTES.get(i).name = "Route " + (i + 1);
        }

        SwingUtilities.invokeLater(() -> {
            DefaultComboBoxModel<RouteDef> model = new DefaultComboBoxModel<>();
            for (RouteDef rd : ALLOWED_ROUTES) model.addElement(rd);
            routeCombo.setModel(model);
            routeCombo.setEnabled(model.getSize() > 0);
            if (model.getSize() > 0) routeCombo.setSelectedIndex(0);
        });

        // prebuild variants
        for (RouteDef rd : ALLOWED_ROUTES) {
            buildAndInstallLongVariants(rd, Main.TYPE_CAR);
            buildAndInstallLongVariants(rd, Main.TYPE_TRUCK);
            buildAndInstallLongVariants(rd, Main.TYPE_BUS);
        }

        ready = !ALLOWED_ROUTES.isEmpty();
        Logging.LOG.info("Dropdown built. Scenarios=" + ALLOWED_ROUTES.size());
    }

    // ===================== Vehicle injection =====================
    /**
     * Robust Injection.
     * Tries a complex "add" method first, falls back to simple "add" if the API is older.
     * This ensures the code works on different SUMO versions.
     * 健壮的注入。
     * 首先尝试复杂的 "add" 方法，如果 API 较旧，则回退到简单的 "add"。
     * 这确保代码在不同的 SUMO 版本上都能工作。
     */
    private static void addVehicleRobust(String vehId, String routeId, String typeId) throws Exception {
        boolean used = false;
        try {

            // Attempt to call the complex "add" method (with departPos, departSpeed, etc.)
            // 尝试调用复杂的 "add" 方法（包含出发位置、出发速度等参数）
            // Attempt to set specific departPos, departSpeed, etc.
            // 尝试设置特定的出发位置、出发速度等。
            Method m = Vehicle.class.getMethod("add",
                    String.class, String.class, String.class,
                    double.class, String.class, double.class, double.class);
            m.invoke(null, vehId, routeId, typeId,
                    Simulation.getCurrentTime(),
                    "best", 0.0, -1.0);
            used = true;
        } catch (Exception ignored) {}

        // Fallback: simple addition
        // 回退：简单添加
        // Fallback strategy / 回退策略
        // If the complex method doesn't exist (older API), use the simple one.
        // 如果复杂方法不存在（旧版 API），则使用简单方法。
        if (!used) Vehicle.add(vehId, routeId, typeId);
    }

    public static void injectVehicles(JFrame owner, String typeId, RouteDef rd, int n) {
        if (!ready) {
            JOptionPane.showMessageDialog(owner, "Not ready yet. Press Start Simulation first.",
                    "Not Ready", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (rd == null) return;

        java.util.List<RouteVariant> vars = buildAndInstallLongVariants(rd, typeId);
        if (vars == null || vars.isEmpty()) {
            JOptionPane.showMessageDialog(owner,
                    "Could not build long routes for this scenario/type.\nTry another scenario.",
                    "No Long Routes", JOptionPane.ERROR_MESSAGE);
            return;
        }

        for (int i = 0; i < n; i++) {
            int idx = pickVariantIndex();
            if (idx >= vars.size()) idx = vars.size() - 1;
            RouteVariant chosen = vars.get(idx);

            String vehId = typeId + "_" + (System.nanoTime() & 0x7FFFFFFF);
            try {
                addVehicleRobust(vehId, chosen.routeId, typeId);
            } catch (Exception ex) {
                Logging.LOG.log(java.util.logging.Level.SEVERE, "Vehicle.add failed for " + vehId + " route=" + chosen.routeId, ex);
            }
        }
    }
}