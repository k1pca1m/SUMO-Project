package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MapPanel extends JPanel {

    private final Map<String, Point2D.Double> vehicles      = new ConcurrentHashMap<>();
    private final Map<String, String>        vehicleShapes  = new ConcurrentHashMap<>();
    private final Map<String, String>        vehicleLanes   = new ConcurrentHashMap<>();

    public static final Color BG_MAP       = new Color(0x020617);
    public static final Color ROAD_DARK    = new Color(0x111827);
    public static final Color ROAD_EDGE    = new Color(0x1F2933);
    public static final Color ROAD_LINE    = new Color(0xFACC15);

    public static final double VIEW_MIN_X = TrafficManager.VIEW_MIN_X;
    public static final double VIEW_MAX_X = TrafficManager.VIEW_MAX_X;
    public static final double VIEW_MIN_Y = TrafficManager.VIEW_MIN_Y;
    public static final double VIEW_MAX_Y = TrafficManager.VIEW_MAX_Y;

    public static final String TYPE_CAR   = SumoConnector.TYPE_CAR;
    public static final String TYPE_TRUCK = SumoConnector.TYPE_TRUCK;
    public static final String TYPE_BUS   = SumoConnector.TYPE_BUS;

    void updateVehicles(Map<String, Point2D.Double> newData,
                        Map<String, String> types,
                        Map<String, String> lanes) {
        vehicles.clear();
        vehicleShapes.clear();
        vehicleLanes.clear();
        vehicles.putAll(newData);
        vehicleShapes.putAll(types);
        vehicleLanes.putAll(lanes);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(BG_MAP);
        g2.fillRect(0, 0, getWidth(), getHeight());

        double width  = VIEW_MAX_X - VIEW_MIN_X;
        double height = VIEW_MAX_Y - VIEW_MIN_Y;

        java.util.function.BiFunction<Double, Double, Point> toScreen =
                (wx, wy) -> {
                    double nx = (wx - VIEW_MIN_X) / width;
                    double ny = (wy - VIEW_MIN_Y) / height;

                    nx = Math.max(0.0, Math.min(1.0, nx));
                    ny = Math.max(0.0, Math.min(1.0, ny));

                    int sx = (int) (nx * getWidth());
                    int sy = (int) ((1.0 - ny) * getHeight());
                    return new Point(sx, sy);
                };

        Point xy = toScreen.apply(-15.0, 18.0);

        int panelW = getWidth();
        int panelH = getHeight();

        int roadHalfWidth   = 26;
        int sidewalkWidth   = 10;
        int centerLineWidth = 3;

        int hRoadTop    = xy.y - roadHalfWidth - sidewalkWidth;
        int hRoadHeight = 2 * (roadHalfWidth + sidewalkWidth);

        int vRoadLeft   = xy.x - roadHalfWidth - sidewalkWidth;
        int vRoadWidth  = 2 * (roadHalfWidth + sidewalkWidth);

        g2.setColor(new Color(0x374151));
        g2.fillRoundRect(0, hRoadTop, panelW, hRoadHeight, 30, 30);
        g2.fillRoundRect(vRoadLeft, 0, vRoadWidth, panelH, 30, 30);

        int hX = 0;
        int hY = xy.y - roadHalfWidth;
        int hLength = panelW;
        int hHeight = 2 * roadHalfWidth;

        int vX = xy.x - roadHalfWidth;
        int vY = 0;
        int vLength = panelH;
        int vWidth = 2 * roadHalfWidth;

        g2.setColor(ROAD_EDGE);
        g2.fillRoundRect(hX, hY - 3, hLength, hHeight + 6, 30, 30);
        g2.fillRoundRect(vX - 3, vY, vWidth + 6, vLength, 30, 30);

        g2.setColor(ROAD_DARK);
        g2.fillRoundRect(hX, hY, hLength, hHeight, 30, 30);
        g2.fillRoundRect(vX, vY, vWidth, vLength, 30, 30);

        int circleR = roadHalfWidth + 6;
        g2.setColor(new Color(0x4B5563));
        g2.fillOval(xy.x - circleR, xy.y - circleR, 2 * circleR, 2 * circleR);

        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0,
                new float[]{10, 10}, 0));
        g2.setColor(new Color(0x9CA3AF));

        int laneOffset = 8;
        int dashMargin = 40;

        g2.drawLine(xy.x + laneOffset, hY + 3,
                panelW - dashMargin, hY + 3);
        g2.drawLine(dashMargin, hY + hHeight - 4,
                xy.x - laneOffset, hY + hHeight - 4);

        g2.drawLine(vX + 3, dashMargin,
                vX + 3, xy.y - laneOffset);
        g2.drawLine(vX + vWidth - 4, xy.y + laneOffset,
                vX + vWidth - 4, panelH - dashMargin);

        g2.setStroke(new BasicStroke(centerLineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(ROAD_LINE);

        int lineMargin = 45;

        g2.drawLine(hX + lineMargin, xy.y, hX + hLength - lineMargin, xy.y);
        g2.drawLine(xy.x, vY + lineMargin, xy.x, vY + vLength - lineMargin);

        g2.setColor(Color.WHITE);
        int stripeWidth = 4;
        int stripeGap   = 4;
        int zebraLen    = 40;

        int topY = hY - sidewalkWidth + 2;
        for (int sx = xy.x - zebraLen; sx <= xy.x + zebraLen; sx += stripeWidth + stripeGap) {
            g2.fillRect(sx, topY, stripeWidth, sidewalkWidth - 4);
        }

        int bottomY = hY + hHeight - 2 - (sidewalkWidth - 4);
        for (int sx = xy.x - zebraLen; sx <= xy.x + zebraLen; sx += stripeWidth + stripeGap) {
            g2.fillRect(sx, bottomY, stripeWidth, sidewalkWidth - 4);
        }

        int leftX = vX - sidewalkWidth + 2;
        for (int sy = xy.y - zebraLen; sy <= xy.y + zebraLen; sy += stripeWidth + stripeGap) {
            g2.fillRect(leftX, sy, sidewalkWidth - 4, stripeWidth);
        }

        int rightX = vX + vWidth - 2 - (sidewalkWidth - 4);
        for (int sy = xy.y - zebraLen; sy <= xy.y + zebraLen; sy += stripeWidth + stripeGap) {
            g2.fillRect(rightX, sy, sidewalkWidth - 4, stripeWidth);
        }

        int laneShift = laneOffset;

        for (Map.Entry<String, Point2D.Double> entry : vehicles.entrySet()) {
            String id = entry.getKey();
            Point2D.Double p = entry.getValue();
            Point s = toScreen.apply(p.x, p.y);

            String laneId = vehicleLanes.get(id);
            int sx = s.x;
            int sy = s.y;

            if (laneId != null) {
                if (laneId.startsWith("E0") || laneId.startsWith("E0.45")) {
                    sy -= laneShift;
                } else if (laneId.startsWith("-E0")) {
                    sy += laneShift;
                } else if (laneId.startsWith("E1")) {
                    sx += laneShift;
                } else if (laneId.startsWith("E2") || laneId.startsWith("-E2")) {
                    sx -= laneShift;
                }
            }

            String type = vehicleShapes.getOrDefault(id, TYPE_CAR);

            switch (type) {
                case TYPE_CAR:
                    g2.setColor(new Color(0xF97316));
                    g2.fillRoundRect(sx - 6, sy - 3, 12, 6, 3, 3);
                    g2.setColor(Color.BLACK);
                    g2.fillOval(sx - 5, sy + 2, 3, 3);
                    g2.fillOval(sx + 2, sy + 2, 3, 3);
                    break;

                case TYPE_TRUCK:
                    g2.setColor(new Color(0x6B7280));
                    g2.fillRect(sx - 8, sy - 4, 16, 8);
                    g2.setColor(new Color(0xFDE68A));
                    g2.fillRect(sx + 2, sy - 4, 6, 8);
                    g2.setColor(Color.BLACK);
                    g2.fillOval(sx - 7, sy + 3, 3, 3);
                    g2.fillOval(sx + 3, sy + 3, 3, 3);
                    break;

                case TYPE_BUS:
                    g2.setColor(new Color(0xFACC15));
                    g2.fillRoundRect(sx - 10, sy - 4, 20, 8, 4, 4);
                    g2.setColor(Color.WHITE);
                    g2.fillRect(sx - 7, sy - 2, 3, 3);
                    g2.fillRect(sx - 2, sy - 2, 3, 3);
                    g2.fillRect(sx + 3, sy - 2, 3, 3);
                    g2.setColor(Color.BLACK);
                    g2.fillOval(sx - 9, sy + 3, 3, 3);
                    g2.fillOval(sx + 6, sy + 3, 3, 3);
                    break;
            }
        }
    }
}
