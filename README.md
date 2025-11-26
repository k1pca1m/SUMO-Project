# SUMO Traffic Simulation (Java)

This project connects a Java application to the SUMO traffic simulator using TraCI to visualize and analyze traffic flow on a small road network.

## Features
- Starts SUMO with a custom `.sumocfg` scenario.
- Reads live vehicle positions via TraCI and displays them in a Java GUI.
- Shows basic metrics such as active vehicles and simple congestion indicators.

## Tech Stack
- Java (Swing for GUI)
- Eclipse SUMO
- TraCI / libtraci bindings

## How to Run
1. Install SUMO and ensure it runs with your `.sumocfg` file.
2. Configure the Java code with the correct path to `test2.sumocfg` and SUMO binaries.
3. Build and run the `Main` class from your IDE.
4. Watch the SUMO GUI and Java window to see vehicles move in the simulated network.
