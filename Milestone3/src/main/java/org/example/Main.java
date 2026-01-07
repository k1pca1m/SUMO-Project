// Main.java
package org.example;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        String sumocfgPath = "final.sumocfg";
        try {
            LiveConnectionSumo.validateProjectSetup(sumocfgPath);
        } catch (LiveConnectionSumo.Milestone3Exception ex) {
            Logging.LOG.severe("Project setup error: " + ex.getMessage());
            JOptionPane.showMessageDialog(null, ex.getMessage(), "Project setup error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Logging.LOG.info("App boot @ " + Logging.nowTag());
        new GUI().show(sumocfgPath);
    }
}