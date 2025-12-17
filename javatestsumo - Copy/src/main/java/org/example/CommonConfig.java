package org.example;

import java.awt.Color;
import java.util.Random;

public final class CommonConfig {
    private CommonConfig() {}

    public static final String TYPE_CAR="car", TYPE_TRUCK="truck", TYPE_BUS="bus";
    public static final String TL_ID="XY";

    public static final String[][] LANE_ROUTES = {
            {"r_l1_left","r_l1_straight","r_l1_right"},
            {"r_l2_left","r_l2_straight","r_l2_right"},
            {"r_l3_left","r_l3_straight","r_l3_right"},
            {"r_l4_left","r_l4_straight","r_l4_right"}
    };
    public static final String[] ROUTES_LANE1 = LANE_ROUTES[0], ROUTES_LANE2 = LANE_ROUTES[1],
            ROUTES_LANE3 = LANE_ROUTES[2], ROUTES_LANE4 = LANE_ROUTES[3];

    public static final int[] WEST_IDX  = {0,1,2};
    public static final int[] EAST_IDX  = {6,7,8};
    public static final int[] SOUTH_IDX = {3,4,5};
    public static final int[] NORTH_IDX = {9,10,11};
    public static final int[][] DIR_IDX = { WEST_IDX, EAST_IDX, SOUTH_IDX, NORTH_IDX };

    public static final double NET_MIN_X=-60.98, NET_MIN_Y=-9.38, NET_MAX_X=32.32, NET_MAX_Y=41.31;
    public static final double VIEW_MIN_X=NET_MIN_X, VIEW_MAX_X=NET_MAX_X, VIEW_MIN_Y=-9.38, VIEW_MAX_Y=NET_MAX_Y;

    public static final Color BG_DARK=new Color(0x0F172A), BG_PANEL=new Color(0x111827), BG_MAP=new Color(0x020617);
    public static final Color ROAD_DARK=new Color(0x111827), ROAD_LINE=new Color(0xFACC15);
    public static final Color ACCENT_BLUE=new Color(0x3B82F6), ACCENT_GREEN=new Color(0x22C55E),
            ACCENT_RED=new Color(0xEF4444), ACCENT_INDIGO=new Color(0x6366F1);
    public static final Color TEXT_PRIMARY=Color.BLACK, TEXT_MUTED=new Color(0x9CA3AF), BORDER_COL=new Color(0x1F2937);

    public static final Random RNG = new Random();

    // SUMO config
    public static final String SUMO_CFG = "test2.sumocfg";
    public static final boolean USE_GUI = true;
}