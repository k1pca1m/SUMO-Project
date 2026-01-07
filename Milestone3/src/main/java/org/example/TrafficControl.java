// TrafficControl.java
package org.example;

import org.eclipse.sumo.libtraci.*;

import javax.swing.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TrafficControl {

    public static class TlsItem {
        public final String id;
        public final String tag;
        public TlsItem(String id, String tag) { this.id = id; this.tag = tag; }
        @Override public String toString() {
            if (tag == null || tag.isBlank() || tag.equals(id)) return id;
            return tag + "  (" + id + ")";
        }
    }

    enum ManualTlsMode { NONE, FORCE_RED, FORCE_GREEN }

    private final ConcurrentHashMap<String, ManualTlsMode> manualTlsMode = new ConcurrentHashMap<>();
    private final Map<String, String> originalTlsPrograms = new ConcurrentHashMap<>();
    private volatile List<String> tlsIdsCached = new ArrayList<>();
    private volatile String selectedTlsId = null;

    public void setSelectedTls(String tlsId) { this.selectedTlsId = tlsId; }
    public String getSelectedTls() { return selectedTlsId; }

    public void rebuildTrafficLightDropdown(JComboBox<TlsItem> tlCombo, JLabel tlStateLabel) {
        try {
            StringVector ids = TrafficLight.getIDList();
            final List<String> list = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) list.add(ids.get(i));
            tlsIdsCached = new ArrayList<>(list);

            SwingUtilities.invokeLater(() -> {
                tlCombo.removeAllItems();
                for (String id : list) {
                    String tag = (MapVisualisation.TLS_LABELS != null)
                            ? MapVisualisation.TLS_LABELS.getOrDefault(id, id)
                            : id;
                    tlCombo.addItem(new TlsItem(id, tag));
                }
                tlCombo.setEnabled(!list.isEmpty());
                if (!list.isEmpty()) tlCombo.setSelectedIndex(0);
                selectedTlsId = list.isEmpty() ? null : list.get(0);

                tlStateLabel.setText(list.isEmpty()
                        ? "TL State: none"
                        : "TL State: ready (" + list.size() + ")");
            });
        } catch (Exception ex) {
            Logging.LOG.log(Level.WARNING, "Failed to read traffic light IDs", ex);
            SwingUtilities.invokeLater(() -> {
                tlCombo.removeAllItems();
                tlCombo.setEnabled(false);
                tlStateLabel.setText("TL State: error");
            });
        }
    }

    private void rememberOriginalProgram(String tlsId) {
        if (tlsId == null) return;
        if (originalTlsPrograms.containsKey(tlsId)) return;
        try {
            String prog = TrafficLight.getProgram(tlsId);
            originalTlsPrograms.put(tlsId, (prog == null || prog.isBlank()) ? "0" : prog);
        } catch (Exception ex) {
            originalTlsPrograms.put(tlsId, "0");
        }
    }

    public void applyManualOverrideIfNeeded() {
        for (Map.Entry<String, ManualTlsMode> e : manualTlsMode.entrySet()) {
            String tlsId = e.getKey();
            ManualTlsMode mode = e.getValue();
            if (mode == null || mode == ManualTlsMode.NONE) continue;

            try {
                String current = TrafficLight.getRedYellowGreenState(tlsId);
                int n = current.length();
                StringBuilder sb = new StringBuilder(n);
                if (mode == ManualTlsMode.FORCE_RED) {
                    for (int i = 0; i < n; i++) sb.append('r');
                } else {
                    for (int i = 0; i < n; i++) sb.append('G');
                }
                TrafficLight.setRedYellowGreenState(tlsId, sb.toString());
            } catch (Exception ignored) {}
        }
    }

    public void forceTrafficLightRed(String tlsId, JLabel tlStateLabel) {
        try {
            rememberOriginalProgram(tlsId);
            manualTlsMode.put(tlsId, ManualTlsMode.FORCE_RED);

            String current = TrafficLight.getRedYellowGreenState(tlsId);
            int n = current.length();
            StringBuilder sb = new StringBuilder(n);
            for (int i = 0; i < n; i++) sb.append('r');
            TrafficLight.setRedYellowGreenState(tlsId, sb.toString());

            SwingUtilities.invokeLater(() -> tlStateLabel.setText("TL " + tlsId + " forced RED (persistent)"));
        } catch (Exception ex) {
            Logging.LOG.log(Level.WARNING, "forceTrafficLightRed failed for " + tlsId, ex);
            SwingUtilities.invokeLater(() -> tlStateLabel.setText("TL " + tlsId + " error (red)"));
        }
    }

    public void forceTrafficLightGreen(String tlsId, JLabel tlStateLabel) {
        try {
            rememberOriginalProgram(tlsId);
            manualTlsMode.put(tlsId, ManualTlsMode.FORCE_GREEN);

            String current = TrafficLight.getRedYellowGreenState(tlsId);
            int n = current.length();
            StringBuilder sb = new StringBuilder(n);
            for (int i = 0; i < n; i++) sb.append('G');
            TrafficLight.setRedYellowGreenState(tlsId, sb.toString());

            SwingUtilities.invokeLater(() -> tlStateLabel.setText("TL " + tlsId + " forced GREEN (persistent)"));
        } catch (Exception ex) {
            Logging.LOG.log(Level.WARNING, "forceTrafficLightGreen failed for " + tlsId, ex);
            SwingUtilities.invokeLater(() -> tlStateLabel.setText("TL " + tlsId + " error (green)"));
        }
    }

    public void resetAllForcedTrafficLights(JLabel tlStateLabel) {
        int ok = 0, fail = 0;
        List<String> ids = new ArrayList<>(manualTlsMode.keySet());

        for (String tlsId : ids) {
            try {
                String prog = originalTlsPrograms.get(tlsId);
                if (prog == null || prog.isBlank()) prog = "0";

                TrafficLight.setProgram(tlsId, prog);
                try { TrafficLight.setPhaseDuration(tlsId, 0.0); } catch (Exception ignore) {}

                manualTlsMode.put(tlsId, ManualTlsMode.NONE);
                ok++;
            } catch (Exception ex) {
                fail++;
                Logging.LOG.log(Level.WARNING, "Reset failed for TLS " + tlsId, ex);
            }
        }

        manualTlsMode.entrySet().removeIf(e -> e.getValue() == ManualTlsMode.NONE);
        originalTlsPrograms.clear();

        final int okF = ok, failF = fail;
        SwingUtilities.invokeLater(() -> {
            if (okF > 0 && failF == 0) tlStateLabel.setText("TL Reset: all back to NORMAL (" + okF + ")");
            else if (okF > 0) tlStateLabel.setText("TL Reset: normal=" + okF + ", failed=" + failF);
            else tlStateLabel.setText("TL Reset: nothing to reset");
        });
    }

    public String buildTlsStateLine() {
        String tlsShow = selectedTlsId;
        if (tlsShow == null || tlsShow.isBlank()) return "TL State: none";
        try {
            String ry = org.eclipse.sumo.libtraci.TrafficLight.getRedYellowGreenState(tlsShow);
            ManualTlsMode mm = manualTlsMode.getOrDefault(tlsShow, ManualTlsMode.NONE);
            String mmTxt = (mm == ManualTlsMode.NONE) ? "AUTO" : (mm == ManualTlsMode.FORCE_RED ? "FORCED RED" : "FORCED GREEN");
            return "TL State: " + tlsShow + " | " + ry + " | Mode=" + mmTxt;
        } catch (Exception ignore) {
            return "TL State: " + tlsShow + " (read error)";
        }
    }
}
