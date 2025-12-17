package org.example;

import javax.swing.*;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MapPanel extends JPanel {

    private final Map<String, Point2D.Double> vehicles=new ConcurrentHashMap<>();
    private final Map<String, String> vehicleShapes=new ConcurrentHashMap<>();
    private final Map<String, String> vehicleLanes=new ConcurrentHashMap<>();

    private final Color[][] tlCols = {
            {Color.RED, new Color(0x78350F), new Color(0x064E3B)},
            {Color.RED, new Color(0x78350F), new Color(0x064E3B)},
            {Color.RED, new Color(0x78350F), new Color(0x064E3B)},
            {Color.RED, new Color(0x78350F), new Color(0x064E3B)}
    };

    public void updateVehicles(Map<String, Point2D.Double> newData, Map<String, String> types, Map<String, String> lanes) {
        vehicles.clear(); vehicleShapes.clear(); vehicleLanes.clear();
        vehicles.putAll(newData); vehicleShapes.putAll(types); vehicleLanes.putAll(lanes);
        repaint();
    }

    public void setTrafficLightState(String s) {
        Color redDim=new Color(0x7F1D1D), yellowDim=new Color(0x78350F), greenDim=new Color(0x064E3B);
        for(int d=0; d<4; d++){
            boolean g=hasChar(s, CommonConfig.DIR_IDX[d], 'g')||hasChar(s, CommonConfig.DIR_IDX[d], 'G');
            boolean y=hasChar(s, CommonConfig.DIR_IDX[d], 'y')||hasChar(s, CommonConfig.DIR_IDX[d], 'Y');
            tlCols[d][0]= (g||y) ? redDim : Color.RED;
            tlCols[d][1]= g ? yellowDim : (y ? Color.YELLOW : yellowDim);
            tlCols[d][2]= g ? Color.GREEN : greenDim;
        }
        repaint();
    }

    private boolean hasChar(String s,int[] idx,char ch){
        if(s==null) return false;
        for(int i: idx) if(i>=0 && i<s.length()){
            char c=s.charAt(i);
            if(c==ch || Character.toLowerCase(c)==Character.toLowerCase(ch)) return true;
        }
        return false;
    }

    private void drawTrafficSignal(Graphics2D g2,int x,int y,Color r,Color yy,Color g,String label){
        int boxW=18, boxH=46;
        g2.setColor(new Color(0x6B7280)); g2.fillRect(x+boxW/2-2, y+boxH, 4, 18);
        g2.setColor(new Color(0x111827)); g2.fillRoundRect(x,y,boxW,boxH,6,6);
        g2.setColor(new Color(0x4B5563)); g2.drawRoundRect(x,y,boxW,boxH,6,6);

        int rad=8, cx=x+boxW/2;
        int cyR=y+6, cyY=y+19, cyG=y+32;
        g2.setColor(r);  g2.fillOval(cx-rad/2, cyR-rad/2, rad, rad);
        g2.setColor(yy); g2.fillOval(cx-rad/2, cyY-rad/2, rad, rad);
        g2.setColor(g);  g2.fillOval(cx-rad/2, cyG-rad/2, rad, rad);

        g2.setColor(Color.WHITE); g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.drawString(label, x+boxW+3, y+boxH/2);
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2=(Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(CommonConfig.BG_MAP); g2.fillRect(0,0,getWidth(),getHeight());

        double w=CommonConfig.VIEW_MAX_X-CommonConfig.VIEW_MIN_X, h=CommonConfig.VIEW_MAX_Y-CommonConfig.VIEW_MIN_Y;
        java.util.function.BiFunction<Double, Double, Point> toScreen = (wx,wy)->{
            double nx=(wx-CommonConfig.VIEW_MIN_X)/w, ny=(wy-CommonConfig.VIEW_MIN_Y)/h;
            nx=Math.max(0,Math.min(1,nx)); ny=Math.max(0,Math.min(1,ny));
            return new Point((int)(nx*getWidth()), (int)((1-ny)*getHeight()));
        };

        Point xy = toScreen.apply(-15.0, 18.0);
        int panelW=getWidth(), panelH=getHeight();
        int roadHalf=26, sidewalk=10, centerLine=3;

        int hRoadTop=xy.y-roadHalf-sidewalk, hRoadH=2*(roadHalf+sidewalk);
        int vRoadLeft=xy.x-roadHalf-sidewalk, vRoadW=2*(roadHalf+sidewalk);

        g2.setColor(new Color(0x374151));
        g2.fillRoundRect(0,hRoadTop,panelW,hRoadH,30,30);
        g2.fillRoundRect(vRoadLeft,0,vRoadW,panelH,30,30);

        int hX=0, hY=xy.y-roadHalf, hLen=panelW, hHt=2*roadHalf;
        int vX=xy.x-roadHalf, vY=0, vLen=panelH, vWd=2*roadHalf;

        g2.setColor(new Color(0x1F2933));
        g2.fillRoundRect(hX,hY-3,hLen,hHt+6,30,30);
        g2.fillRoundRect(vX-3,vY,vWd+6,vLen,30,30);

        g2.setColor(CommonConfig.ROAD_DARK);
        g2.fillRoundRect(hX,hY,hLen,hHt,30,30);
        g2.fillRoundRect(vX,vY,vWd,vLen,30,30);

        int circleR=roadHalf+6;
        g2.setColor(new Color(0x4B5563));
        g2.fillOval(xy.x-circleR, xy.y-circleR, 2*circleR, 2*circleR);

        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0, new float[]{10,10}, 0));
        g2.setColor(new Color(0x9CA3AF));
        int laneOffset=8, dashMargin=40;

        g2.drawLine(xy.x+laneOffset, hY+3, panelW-dashMargin, hY+3);
        g2.drawLine(dashMargin, hY+hHt-4, xy.x-laneOffset, hY+hHt-4);
        g2.drawLine(vX+3, dashMargin, vX+3, xy.y-laneOffset);
        g2.drawLine(vX+vWd-4, xy.y+laneOffset, vX+vWd-4, panelH-dashMargin);

        g2.setStroke(new BasicStroke(centerLine, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(CommonConfig.ROAD_LINE);
        int lineMargin=45;
        g2.drawLine(hX+lineMargin, xy.y, hX+hLen-lineMargin, xy.y);
        g2.drawLine(xy.x, vY+lineMargin, xy.x, vY+vLen-lineMargin);

        g2.setColor(Color.WHITE);
        int stripeW=4, stripeGap=4, zebraLen=40;

        int topY=hY-10+2;
        for(int sx=xy.x-zebraLen; sx<=xy.x+zebraLen; sx+=stripeW+stripeGap) g2.fillRect(sx, topY, stripeW, 6);
        int bottomY=hY+(2*roadHalf)-2-6;
        for(int sx=xy.x-zebraLen; sx<=xy.x+zebraLen; sx+=stripeW+stripeGap) g2.fillRect(sx, bottomY, stripeW, 6);

        int leftX=vX-10+2;
        for(int sy=xy.y-zebraLen; sy<=xy.y+zebraLen; sy+=stripeW+stripeGap) g2.fillRect(leftX, sy, 6, stripeW);
        int rightX=vX+(2*roadHalf)-2-6;
        for(int sy=xy.y-zebraLen; sy<=xy.y+zebraLen; sy+=stripeW+stripeGap) g2.fillRect(rightX, sy, 6, stripeW);

        Point westTL=new Point(xy.x-roadHalf-30, xy.y+roadHalf+8);
        Point eastTL=new Point(xy.x+roadHalf+10, xy.y-roadHalf-54);
        Point southTL=new Point(xy.x+roadHalf+14, xy.y+roadHalf+8);
        Point northTL=new Point(xy.x-roadHalf-30, xy.y-roadHalf-54);

        // SAME behavior: labels swapped T1<->T2 (as in your working file)
        drawTrafficSignal(g2, westTL.x,  westTL.y,  tlCols[1][0], tlCols[1][1], tlCols[1][2], "T2");
        drawTrafficSignal(g2, eastTL.x,  eastTL.y,  tlCols[0][0], tlCols[0][1], tlCols[0][2], "T1");
        drawTrafficSignal(g2, southTL.x, southTL.y, tlCols[2][0], tlCols[2][1], tlCols[2][2], "T3");
        drawTrafficSignal(g2, northTL.x, northTL.y, tlCols[3][0], tlCols[3][1], tlCols[3][2], "T4");

        int laneShift=laneOffset;
        for (Map.Entry<String, Point2D.Double> e : vehicles.entrySet()) {
            String id=e.getKey(); Point2D.Double p=e.getValue();
            Point s=toScreen.apply(p.x,p.y);
            String laneId=vehicleLanes.get(id);

            int sx=s.x, sy=s.y;
            if(laneId!=null){
                if(laneId.startsWith("E0")||laneId.startsWith("E0.45")) sy-=laneShift;
                else if(laneId.startsWith("-E0")) sy+=laneShift;
                else if(laneId.startsWith("E1")) sx+=laneShift;
                else if(laneId.startsWith("E2")||laneId.startsWith("-E2")) sx-=laneShift;
            }

            String type=vehicleShapes.getOrDefault(id, CommonConfig.TYPE_CAR);
            switch(type){
                case CommonConfig.TYPE_CAR:
                    g2.setColor(new Color(0xF97316));
                    g2.fillRoundRect(sx-6, sy-3, 12, 6, 3, 3);
                    g2.setColor(Color.BLACK);
                    g2.fillOval(sx-5, sy+2, 3, 3); g2.fillOval(sx+2, sy+2, 3, 3);
                    break;
                case CommonConfig.TYPE_TRUCK:
                    g2.setColor(new Color(0x6B7280));
                    g2.fillRect(sx-8, sy-4, 16, 8);
                    g2.setColor(new Color(0xFDE68A));
                    g2.fillRect(sx+2, sy-4, 6, 8);
                    g2.setColor(Color.BLACK);
                    g2.fillOval(sx-7, sy+3, 3, 3); g2.fillOval(sx+3, sy+3, 3, 3);
                    break;
                case CommonConfig.TYPE_BUS:
                    g2.setColor(new Color(0xFACC15));
                    g2.fillRoundRect(sx-10, sy-4, 20, 8, 4, 4);
                    g2.setColor(Color.WHITE);
                    g2.fillRect(sx-7, sy-2, 3, 3); g2.fillRect(sx-2, sy-2, 3, 3); g2.fillRect(sx+3, sy-2, 3, 3);
                    g2.setColor(Color.BLACK);
                    g2.fillOval(sx-9, sy+3, 3, 3); g2.fillOval(sx+6, sy+3, 3, 3);
                    break;
            }
        }
    }
}