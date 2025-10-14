import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

// Class representing a node in the priority queue for Dijkstra's algorithm
class ParkNode implements Comparable<ParkNode> {
    public String name;
    public double time;
    public ParkNode(String name, double time) {
        this.name = name;
        this.time = time;
    }
    @Override
    public int compareTo(ParkNode other) {
        return Double.compare(this.time, other.time);
    }
}

public class AmusementPark extends JFrame {
    
    // --- Data Structures ---
    private Map<String, Map<String, Double>> travelGraph = new HashMap<>();
    private Map<String, String[]> rideDetails = new HashMap<>(); 
    private Map<String, Double> paceMultipliers = new HashMap<>();
    private List<String> allDestinations = new ArrayList<>(); 
    // --- UPDATED START NODE CONSTANT ---
    private static final String START_NODE = "[01] Entrance"; 

    // --- Image and Zoom/Pan State Variables ---
    private Image mapImage; 
    private double scale = 1.0; 
    private double offsetX = 0; 
    private double offsetY = 0; 
    private Point mousePoint; 

    // --- GUI Components ---
    private JComboBox<String> rideDropdown;
    private JTextArea detailPanel;
    private JPanel destinationPanel;
    private JComboBox<String> paceDropdown;
    private JButton submitButton;
    private JTextArea pathResultArea;
    
    private List<JComboBox<String>> destinationDropdowns = new ArrayList<>();
    
    // Constructor
    public AmusementPark() {
        super("Amusement Park Planner");
        
        loadData();
        loadMapImage("park_map.jpg"); 
        createGUI();
        
        // --- FIXED FRAME DIMENSIONS ---
        setPreferredSize(new Dimension(1600, 900));
        setResizable(false); 
        // ------------------------------
        
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack(); 
        setLocationRelativeTo(null); 
        setVisible(true);
    }

    // --- Data Loading Methods (Reads new node names from files) ---
    private void loadData() {
        try {
            loadRideDetails("RideDetails.txt");
            loadGraphAndTimes("ParkMap.txt", "TravelTimes.txt");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error loading data files: " + e.getMessage(), 
                                          "File Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private void loadRideDetails(String filename) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    // Storing: [Duration, Fare, Description]
                    rideDetails.put(parts[0].trim(), new String[]{parts[1], parts[2], parts[3]});
                }
            }
        }
    }
    
    private void loadGraphAndTimes(String mapFile, String timeFile) throws IOException {
        // Load all unique nodes from the map file
        Set<String> nodes = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(mapFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 2) {
                    nodes.add(parts[0].trim());
                    nodes.add(parts[1].trim());
                }
            }
        }
        allDestinations.addAll(nodes);
        Collections.sort(allDestinations);
        
        // Load travel times and pace multipliers
        try (BufferedReader br = new BufferedReader(new FileReader(timeFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                String nodeA = parts[0].trim();
                String nodeB = parts[1].trim();
                
                if (nodeA.startsWith("PaceMultipliers")) {
                    paceMultipliers.put(nodeB, Double.parseDouble(parts[2].trim()));
                } else if (parts.length >= 3) {
                    double time = Double.parseDouble(parts[2].trim());
                    // Add connection (undirected graph)
                    travelGraph.computeIfAbsent(nodeA, k -> new HashMap<>()).put(nodeB, time);
                    travelGraph.computeIfAbsent(nodeB, k -> new HashMap<>()).put(nodeA, time);
                }
            }
        }
    }

    private void loadMapImage(String filename) {
        try {
            mapImage = ImageIO.read(new File(filename));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error loading map image file: " + filename + "\n" + e.getMessage(), 
                                          "Image Load Error", JOptionPane.ERROR_MESSAGE);
            mapImage = null; 
        }
    }
    
    // --- GUI Setup (Includes Zoom/Pan Logic and Fixed Aspect Ratio) ---
    private void createGUI() {
        setLayout(new BorderLayout());

        JPanel mapPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                
                if (mapImage != null) {
                    
                    int panelWidth = getWidth();
                    int panelHeight = getHeight();
                    int imgWidth = mapImage.getWidth(this);
                    int imgHeight = mapImage.getHeight(this);
                    
                    if (imgWidth <= 0 || imgHeight <= 0) return;

                    // 1. Calculate the base scale factor to fit the image while preserving aspect ratio
                    double scaleX = (double) panelWidth / imgWidth;
                    double scaleY = (double) panelHeight / imgHeight;
                    
                    double ratio = Math.min(scaleX, scaleY);

                    // 2. Apply the current zoom level (scale)
                    double finalScale = ratio * scale;
                    
                    // 3. Calculate the scaled image dimensions
                    int scaledWidth = (int) (imgWidth * finalScale);
                    int scaledHeight = (int) (imgHeight * finalScale);
                    
                    // 4. Calculate coordinates to center the image (x, y)
                    int x = (panelWidth - scaledWidth) / 2;
                    int y = (panelHeight - scaledHeight) / 2;
                    
                    // 5. Apply Pan and Draw
                    g2d.translate(offsetX + x, offsetY + y);
                    g2d.drawImage(mapImage, 0, 0, scaledWidth, scaledHeight, this);
                    
                    // Reset transformations for drawing the title
                    g2d.translate(-(offsetX + x), -(offsetY + y));
                    
                    g2d.setColor(Color.BLACK);
                    g2d.setFont(new Font("Arial", Font.BOLD, 16));
                    g2d.drawString("Amusement Park Map (Zoomable Image, Proportions Fixed)", 10, 20); 

                } else {
                    g2d.setColor(Color.LIGHT_GRAY);
                    g2d.fillRect(0, 0, getWidth(), getHeight());
                    g2d.setColor(Color.RED);
                    g2d.drawString("Map Image Not Found: park_map.jpg", 50, 50);
                    g2d.drawString("Pathfinding calculations will still work.", 50, 75);
                    g2d.drawString("Use the mouse wheel to zoom (Min: 1.0x, Max: 5.0x)", 50, 100);
                    g2d.drawString("Drag the mouse to pan.", 50, 125);
                }
            }
        };
        
        // --- INCREASED MAP PANEL SIZE ---
        mapPanel.setPreferredSize(new Dimension(950, 850)); 
        // --------------------------------

        // --- ADD MOUSE LISTENERS FOR ZOOM/PAN ---
        mapPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                mousePoint = e.getPoint();
            }
        });

        mapPanel.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (mousePoint != null) {
                    offsetX += e.getX() - mousePoint.x;
                    offsetY += e.getY() - mousePoint.y;
                    mousePoint = e.getPoint();
                    mapPanel.repaint();
                }
            }
        });
        
        mapPanel.addMouseWheelListener(new MouseAdapter() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double zoomFactor = 1.1;
                double newScale = scale;
                
                if (e.getWheelRotation() < 0) {
                    newScale *= zoomFactor;
                } else {
                    newScale /= zoomFactor;
                }
                
                newScale = Math.max(1.0, newScale);
                newScale = Math.min(5.0, newScale);

                if (newScale != scale) {
                    scale = newScale;
                    mapPanel.repaint();
                }
            }
        });

        add(mapPanel, BorderLayout.WEST);

        // Right side: Features Panel
        JPanel featuresPanel = new JPanel();
        featuresPanel.setLayout(new BoxLayout(featuresPanel, BoxLayout.Y_AXIS));
        featuresPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- Feature 1: Ride Details ---
        featuresPanel.add(new JLabel("1. Ride Details (Non-Editable):"));
        rideDropdown = new JComboBox<>(rideDetails.keySet().toArray(new String[0]));
        rideDropdown.insertItemAt("--- Select a Ride ---", 0);
        rideDropdown.setSelectedIndex(0);
        detailPanel = new JTextArea(5, 30);
        detailPanel.setEditable(false);
        detailPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        
        rideDropdown.addActionListener(e -> displayRideDetails());
        
        featuresPanel.add(rideDropdown);
        featuresPanel.add(new JScrollPane(detailPanel));
        featuresPanel.add(Box.createVerticalStrut(15)); 

        // --- Feature 2: Destination Selection ---
        // Note: START_NODE is used here to guide the user's understanding
        featuresPanel.add(new JLabel("2. Select Route Destinations: (Start & End at Entrance)")); 
        destinationPanel = new JPanel();
        destinationPanel.setLayout(new BoxLayout(destinationPanel, BoxLayout.Y_AXIS));
        
        addDestinationDropdown();
        
        JButton addButton = new JButton("+");
        addButton.addActionListener(e -> addDestinationDropdown());
        
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonRow.add(addButton);
        
        featuresPanel.add(buttonRow);
        featuresPanel.add(destinationPanel);
        featuresPanel.add(Box.createVerticalStrut(15));

        // --- Feature 3: Pace Selection ---
        featuresPanel.add(new JLabel("3. Select Walking Pace:"));
        paceDropdown = new JComboBox<>(paceMultipliers.keySet().toArray(new String[0]));
        paceDropdown.setSelectedItem("Normal Pace");
        featuresPanel.add(paceDropdown);
        featuresPanel.add(Box.createVerticalStrut(15)); 

        add(featuresPanel, BorderLayout.EAST);
        
        // --- Bottom: Submit Button and Results ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        
        submitButton = new JButton("Submit Plan");
        submitButton.addActionListener(e -> calculateShortestPath());
        
        JPanel submitWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        submitWrapper.add(submitButton);
        bottomPanel.add(submitWrapper, BorderLayout.NORTH);
        
        pathResultArea = new JTextArea(12, 70); 
        pathResultArea.setEditable(false);
        pathResultArea.setBorder(BorderFactory.createTitledBorder("Shortest Time Path Result"));
        bottomPanel.add(new JScrollPane(pathResultArea), BorderLayout.SOUTH);
        
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    // --- Helper Methods ---

    private void displayRideDetails() {
        String selectedRide = (String) rideDropdown.getSelectedItem();
        if (selectedRide != null && !selectedRide.startsWith("---")) {
            String[] details = rideDetails.get(selectedRide);
            if (details != null && details.length >= 3) {
                detailPanel.setText(String.format(
                    "Ride: %s\nDuration: %s minutes\nFare: $%s\nDescription: %s",
                    selectedRide, details[0], details[1], details[2]
                ));
            }
        } else {
            detailPanel.setText("");
        }
    }
    
    private void addDestinationDropdown() {
        // 1. Create the new JComboBox for the destination
        JComboBox<String> newDropdown = new JComboBox<>(allDestinations.toArray(new String[0]));
        newDropdown.insertItemAt("--- Select Destination ---", 0);
        newDropdown.setSelectedIndex(0);
        
        // 2. Determine the sequential label
        int index = destinationDropdowns.size() + 1;
        JLabel label = new JLabel(String.format("Destination %d:", index));
        
        // 3. Create a JPanel for the row and use GridBagLayout for alignment
        JPanel row = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        
        // Settings for all components in the row
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 5); // Add a small margin
        
        // Add Label (Column 0)
        gbc.gridx = 0;
        gbc.weightx = 0; // Prevent label from stretching
        row.add(label, gbc);
        
        // Add Dropdown (Column 1)
        gbc.gridx = 1;
        gbc.weightx = 1.0; // Allow dropdown to take up available width
        gbc.fill = GridBagConstraints.HORIZONTAL;
        row.add(newDropdown, gbc);
        
        // Add Remove Button (Column 2)
        if (destinationDropdowns.size() > 0) {
            JButton removeButton = new JButton("-");
            removeButton.addActionListener(e -> removeDestinationDropdown(row, newDropdown));
            gbc.gridx = 2;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            row.add(removeButton, gbc);
        }
        
        destinationDropdowns.add(newDropdown);
        destinationPanel.add(row);
        
        updateDestinationLabels(); 
        
        destinationPanel.revalidate();
        destinationPanel.repaint();
    }
    
    // --- NEW HELPER METHOD: To fix labels after adding/removing ---
    private void updateDestinationLabels() {
        Component[] rows = destinationPanel.getComponents();
        for (int i = 0; i < rows.length; i++) {
            if (rows[i] instanceof JPanel) {
                JPanel row = (JPanel) rows[i];
                // Since we use GridBagLayout, the label is usually the first component added.
                Component[] components = row.getComponents();
                for (Component comp : components) {
                    if (comp instanceof JLabel) {
                        JLabel label = (JLabel) comp;
                        label.setText(String.format("Destination %d:", i + 1));
                        break;
                    }
                }
            }
        }
    }
    // --- MODIFIED Method: Call updateDestinationLabels after removal ---
    private void removeDestinationDropdown(JPanel row, JComboBox<String> dropdown) {
        if (destinationDropdowns.size() > 1) {
            destinationDropdowns.remove(dropdown);
            destinationPanel.remove(row);
            
            // Re-number labels after removal
            updateDestinationLabels(); 
            
            destinationPanel.revalidate();
            destinationPanel.repaint();
        }
    }
    
    // --- Pathfinding Logic (Dijkstra's Algorithm) ---
    // --- Pathfinding Logic (MODIFIED for Greedy TSP Reordering) ---
    private void calculateShortestPath() {
        List<String> rawDestinations = new ArrayList<>();
        for (JComboBox<String> dd : destinationDropdowns) {
            String selection = (String) dd.getSelectedItem();
            if (selection != null && !selection.startsWith("---")) {
                // Ensure no duplicates in the destination list
                if (!rawDestinations.contains(selection)) { 
                    rawDestinations.add(selection);
                }
            } else {
                 JOptionPane.showMessageDialog(this, "Error: Please select a destination for all fields.", 
                                          "Input Error", JOptionPane.ERROR_MESSAGE);
                 return;
            }
        }
        
        // --- TSP GREEDY REORDERING LOGIC STARTS HERE ---
        List<String> destinationsToVisit = new LinkedList<>(rawDestinations);
        List<String> plannedRoute = new ArrayList<>();
        String current = START_NODE;
        
        String selectedPace = (String) paceDropdown.getSelectedItem();
        double multiplier = paceMultipliers.getOrDefault(selectedPace, 1.0);
        
        double totalTime = 0.0;
        double totalFare = 0.0;
        StringBuilder pathDisplay = new StringBuilder();
        
        pathDisplay.append(String.format("Pace: %s (Multiplier: %.2fx)\n", selectedPace, multiplier));
        pathDisplay.append("--------------------------------------------------\n");
        pathDisplay.append("Destinations Visited (Greedy Shortest Time Order):\n");
        
        int segmentCount = 0;
        
        while (!destinationsToVisit.isEmpty()) {
            String nextDestination = null;
            DijkstraResult shortestResult = null;
            double minTime = Double.MAX_VALUE;
            
            // Find the nearest unvisited destination (Greedy Choice)
            for (String target : destinationsToVisit) {
                DijkstraResult result = dijkstra(current, target, multiplier);
                if (result.time != Double.MAX_VALUE && result.time < minTime) {
                    minTime = result.time;
                    shortestResult = result;
                    nextDestination = target;
                }
            }
            
            if (nextDestination == null) {
                 pathDisplay.append("\nERROR: Cannot reach remaining destinations from " + current + "\n");
                 totalTime = Double.MAX_VALUE;
                 break; 
            }
            
            // Execute the segment to the nearest destination
            segmentCount++;
            
            // 1. Walking Time
            totalTime += shortestResult.time;
            
            pathDisplay.append(String.format("Segment %d: %s -> %s (Walk Time: %.2f min)\n",
                                             segmentCount, current, nextDestination, shortestResult.time));
            pathDisplay.append("    Route: " + String.join(" -> ", shortestResult.path) + "\n");
            
            // 2. Ride/Activity Time and Fare at the destination
            if (rideDetails.containsKey(nextDestination) && !nextDestination.equals(START_NODE)) {
                 String[] details = rideDetails.get(nextDestination);
                 
                 // Add ride duration and wait time
                 try {
                     double rideDuration = Double.parseDouble(details[0]);
                     double waitAndRide = rideDuration + 5.0; // 5 minutes standard wait time
                     totalTime += waitAndRide;
                     
                     pathDisplay.append(String.format("    - **Ride/Activity %s Time**: %.2f min (Activity: %.0f min + Wait: 5.0 min)\n", 
                                                      nextDestination, waitAndRide, rideDuration));
                 } catch (NumberFormatException ex) {
                     pathDisplay.append(String.format("    - **Ride/Activity %s Time**: Error parsing duration.\n", nextDestination));
                 }
                 
                 // Add fare
                 try {
                     double rideFare = Double.parseDouble(details[1]); // Fare is at index 1
                     totalFare += rideFare;
                 } catch (NumberFormatException ex) {
                     System.err.println("Warning: Could not parse fare for ride " + nextDestination);
                 }
            }
            
            // Update state
            current = nextDestination;
            destinationsToVisit.remove(nextDestination);
            plannedRoute.add(nextDestination);
        }
        
        // --- Final Segment: Return to Entrance ---
        if (totalTime != Double.MAX_VALUE) {
            DijkstraResult finalResult = dijkstra(current, START_NODE, multiplier);
            segmentCount++;
            
            if (finalResult.time == Double.MAX_VALUE) {
                 pathDisplay.append(String.format("Segment %d: %s -> %s (UNREACHABLE RETURN)\n", segmentCount, current, START_NODE));
                 totalTime = Double.MAX_VALUE;
            } else {
                totalTime += finalResult.time;
                pathDisplay.append(String.format("Segment %d: %s -> %s (Walk Time: %.2f min)\n",
                                                 segmentCount, current, START_NODE, finalResult.time));
                pathDisplay.append("    Route: " + String.join(" -> ", finalResult.path) + "\n");
            }
        }
        
        pathDisplay.append("--------------------------------------------------\n");
        pathDisplay.append("Optimal Route Order (Greedy): " + String.join(" -> ", plannedRoute) + "\n");
        if (totalTime == Double.MAX_VALUE) {
             pathDisplay.append("TOTAL SHORTEST TIME FOR ROUTE: UNABLE TO COMPLETE ROUTE\n");
             pathDisplay.append("TOTAL ESTIMATED FARE: N/A\n");
        } else {
             pathDisplay.append(String.format("TOTAL SHORTEST TIME FOR ROUTE: %.2f minutes\n", totalTime));
             pathDisplay.append(String.format("TOTAL ESTIMATED FARE: $%.2f\n", totalFare));
        }
        pathResultArea.setText(pathDisplay.toString());
    }


    private DijkstraResult dijkstra(String start, String end, double multiplier) {
        Map<String, Double> minTime = new HashMap<>();
        Map<String, String> predecessor = new HashMap<>();
        PriorityQueue<ParkNode> pq = new PriorityQueue<>();
        
        minTime.put(start, 0.0);
        pq.add(new ParkNode(start, 0.0));
        
        while (!pq.isEmpty()) {
            ParkNode current = pq.poll();
            String u = current.name;
            
            if (current.time > minTime.getOrDefault(u, Double.MAX_VALUE)) {
                continue;
            }
            
            if (u.equals(end)) {
                break;
            }
            
            Map<String, Double> neighbors = travelGraph.get(u);
            if (neighbors != null) {
                for (Map.Entry<String, Double> neighbor : neighbors.entrySet()) {
                    String v = neighbor.getKey();
                    double weight = neighbor.getValue() * multiplier;
                    double newTime = minTime.get(u) + weight;
                    
                    if (newTime < minTime.getOrDefault(v, Double.MAX_VALUE)) {
                        minTime.put(v, newTime);
                        predecessor.put(v, u);
                        pq.add(new ParkNode(v, newTime));
                    }
                }
            }
        }
        
        List<String> path = new LinkedList<>();
        String step = end;
        while (step != null) {
            path.add(0, step);
            if (step.equals(start)) break;
            step = predecessor.get(step);
        }
        
        double finalTime = minTime.getOrDefault(end, Double.MAX_VALUE);

        return new DijkstraResult(finalTime, path);
    }
    
    private static class DijkstraResult {
        double time;
        List<String> path;
        public DijkstraResult(double time, List<String> path) {
            this.time = time;
            this.path = path;
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AmusementPark());
    }
}