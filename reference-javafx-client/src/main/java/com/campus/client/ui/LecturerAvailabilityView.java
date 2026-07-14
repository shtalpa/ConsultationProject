package com.campus.client.ui;

import com.campus.client.mcp.CampusMcpClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LecturerAvailabilityView {

    private final VBox root = new VBox(20);
    private CampusMcpClient mcp;

    // --- UI Input Components ---
    private final ComboBox<String> moduleDropdown = new ComboBox<>();
    private final ComboBox<String> lecturerDropdown = new ComboBox<>();
    private final DatePicker datePicker = new DatePicker();
    private final ComboBox<String> startTimeDropdown = new ComboBox<>();
    private final ComboBox<String> endTimeDropdown = new ComboBox<>();

    private final Button searchButton = new Button("Search Availability");
    private final Button clearButton = new Button("Clear Filters");

    // UI Output Components
    private final Label resultsHeader = new Label();
    private final VBox resultsContainer = new VBox(10);
    private final ScrollPane scrollPane = new ScrollPane();

    // --- Frontend Data Maps (facilities.txt) ---
    private final Map<String, List<String>> moduleToLecturersMap = Map.of(
            "ITS66704", List.of("Dr Steve Teoh", "Dr Sohaib Ahmed"),
            "ITS610304", List.of("Dr Nur Fatin Liyana"),
            "CYS5004", List.of("Dr Osama Rehman")
    );

    private final Map<String, String> lecturerToRoomMap = Map.of(
            "Dr Steve Teoh", "D9A.01",
            "Dr Sohaib Ahmed", "D9B.01",
            "Dr Nur Fatin Liyana", "E9A.03",
            "Dr Osama Rehman", "E7.01"
    );

    private final Map<String, String> roomToVenueMap = Map.of(
            "D9A.01", "Block D Consultation Room (D9A.01)",
            "D9B.01", "Block D Consultation Room (D9B.01)",
            "E9A.03", "Block E Discussion Room (E9A.03)",
            "E7.01", "Block E Group Study Room (E7.01)"
    );

    private final Map<String, List<String>> lecturerScheduleMap = Map.of(
            "Dr Steve Teoh", List.of("13:00", "13:20", "13:40"),
            "Dr Sohaib Ahmed", List.of("13:00", "13:20", "13:40"),
            "Dr Nur Fatin Liyana", List.of("13:00", "13:20", "13:40"),
            "Dr Osama Rehman", List.of("13:00", "13:20", "13:40")
    );

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "availability-worker");
        t.setDaemon(true);
        return t;
    });

    public LecturerAvailabilityView() {
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #FFFFFF;");

        Label title = new Label("LECTURER AVAILABILITY");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(javafx.scene.paint.Color.web("#2B6CB0"));

        resultsHeader.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        resultsHeader.setTextFill(javafx.scene.paint.Color.web("#4A5568"));

        setupInputControls();

        // --- Search Area Layout ---
        VBox searchArea = new VBox(15);
        searchArea.setStyle("-fx-background-color: #F8FAFC; -fx-padding: 20; -fx-border-color: #E2E8F0; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        // Dropdown Grid
        GridPane searchGrid = new GridPane();
        searchGrid.setHgap(15);
        searchGrid.setVgap(10);

        searchGrid.add(createRequiredLabel("Module"), 0, 0);
        searchGrid.add(moduleDropdown, 0, 1);

        searchGrid.add(createRequiredLabel("Lecturer"), 1, 0);
        searchGrid.add(lecturerDropdown, 1, 1);

        searchGrid.add(createRequiredLabel("Date"), 2, 0);
        searchGrid.add(datePicker, 2, 1);

        searchGrid.add(new Label("Search From"), 3, 0);
        searchGrid.add(startTimeDropdown, 3, 1);

        searchGrid.add(new Label("Search Until"), 4, 0);
        searchGrid.add(endTimeDropdown, 4, 1);

        // Buttons
        HBox buttonBar = new HBox(15, searchButton, clearButton);
        buttonBar.setAlignment(Pos.CENTER_LEFT);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));

        searchArea.getChildren().addAll(searchGrid, buttonBar);

        // ScrollPane
        scrollPane.setContent(resultsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #FFFFFF; -fx-background-color: transparent; -fx-border-color: transparent; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        root.getChildren().addAll(title, searchArea, resultsHeader, scrollPane);

        wireActions();
    }

    private void setupInputControls() {
        moduleDropdown.getItems().addAll(moduleToLecturersMap.keySet());
        moduleDropdown.setPromptText("Select Module");

        lecturerDropdown.getItems().addAll(lecturerToRoomMap.keySet());
        lecturerDropdown.setPromptText("Select Lecturer");

        // Cascading Filter Logic: When Module changes, update Lecturer list
        moduleDropdown.setOnAction(e -> {
            String selectedModule = moduleDropdown.getValue();
            lecturerDropdown.getItems().clear();

            if (selectedModule != null) {
                // Filtered list
                List<String> filteredLecturers = moduleToLecturersMap.get(selectedModule);
                lecturerDropdown.getItems().addAll(filteredLecturers);
            } else {
                // Reset to all if module is cleared
                lecturerDropdown.getItems().addAll(lecturerToRoomMap.keySet());
            }
        });

        // Generate Times
        for (int h = 8; h <= 18; h++) {
            startTimeDropdown.getItems().add(String.format("%02d:00", h));
            endTimeDropdown.getItems().add(String.format("%02d:00", h));
        }

        startTimeDropdown.setOnAction(e -> {
            String selectedStart = startTimeDropdown.getValue();
            if (selectedStart != null) {
                LocalTime start = LocalTime.parse(selectedStart);
                String currentEnd = endTimeDropdown.getValue();

                endTimeDropdown.getItems().clear();
                for (int h = 8; h <= 18; h++) {
                    LocalTime t = LocalTime.of(h, 0);
                    if (t.isAfter(start)) {
                        endTimeDropdown.getItems().add(String.format("%02d:00", h));
                    }
                }
                if (currentEnd != null && LocalTime.parse(currentEnd).isAfter(start)) {
                    endTimeDropdown.setValue(currentEnd);
                }
            }
        });

        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (date.isBefore(LocalDate.now())) { setDisable(true); }
            }
        });

        searchButton.setStyle("-fx-background-color: #3182CE; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-cursor: hand;");
        searchButton.setPrefWidth(160);
        searchButton.setPrefHeight(40);
        searchButton.setMinHeight(40);

        clearButton.setStyle("-fx-background-color: #E2E8F0; -fx-text-fill: #4A5568; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-cursor: hand;");
        clearButton.setPrefWidth(160);
        clearButton.setPrefHeight(40);
        clearButton.setMinHeight(40);
    }

    private HBox createRequiredLabel(String text) {
        HBox box = new HBox(2);
        Label label = new Label(text);
        Label asterisk = new Label("*");
        asterisk.setTextFill(javafx.scene.paint.Color.RED);
        box.getChildren().addAll(label, asterisk);
        return box;
    }

    public void bind(CampusMcpClient mcp) {
        this.mcp = mcp;
    }

    public VBox getView() {
        return root;
    }

    private void wireActions() {
        // Clear Filters resets everything to default
        clearButton.setOnAction(event -> {
            moduleDropdown.setValue(null);
            lecturerDropdown.setValue(null);
            datePicker.setValue(null);
            startTimeDropdown.setValue(null);
            endTimeDropdown.setValue(null);

            endTimeDropdown.getItems().clear();
            for (int h = 8; h <= 18; h++) {
                endTimeDropdown.getItems().add(String.format("%02d:00", h));
            }

            resultsContainer.getChildren().clear();
            resultsHeader.setText("");
        });

        searchButton.setOnAction(event -> {
            if (mcp == null) {
                new Alert(Alert.AlertType.ERROR, "System not connected to server!").showAndWait();
                return;
            }

            String mod = moduleDropdown.getValue();
            String lec = lecturerDropdown.getValue();
            LocalDate date = datePicker.getValue();

            if (mod == null && lec == null) {
                new Alert(Alert.AlertType.WARNING, "Please select either a Module or a Lecturer.").showAndWait();
                return;
            }
            if (date == null) {
                new Alert(Alert.AlertType.WARNING, "Please select a Date.").showAndWait();
                return;
            }

            resultsContainer.getChildren().clear();
            boolean isModuleOnlySearch = (lec == null);

            if (isModuleOnlySearch) {
                resultsHeader.setText("Available slots for Module: " + mod);
            } else {
                resultsHeader.setText("Available slots for Lecturer: " + lec);
            }

            // Frontend driver logic
            worker.submit(() -> {
                try {
                    List<String> lecturersToSearch = isModuleOnlySearch
                            ? moduleToLecturersMap.get(mod)
                            : List.of(lec);

                    // 1. Temporary container for sorting (The "Lego Bin")
                    class SlotData {
                        final LocalTime timeObj;
                        final String timeStr;
                        final boolean isAvailable;
                        final String lecturerName;
                        final String venueName;

                        SlotData(String timeStr, boolean isAvailable, String lecturerName, String venueName) {
                            this.timeObj = LocalTime.parse(timeStr);
                            this.timeStr = timeStr;
                            this.isAvailable = isAvailable;
                            this.lecturerName = lecturerName;
                            this.venueName = venueName;
                        }
                    }

                    java.util.List<SlotData> collectedSlots = new java.util.ArrayList<>();

                    // 2. Fetch and aggregate all data first
                    for (String targetLec : lecturersToSearch) {
                        String hiddenRoomId = lecturerToRoomMap.get(targetLec);
                        String serverResponse = mcp.checkRoomAvailability(date.toString(), hiddenRoomId);

                        List<String> dailySlots = lecturerScheduleMap.getOrDefault(targetLec, List.of("13:00", "13:20", "13:40"));

                        for (String time : dailySlots) {
                            LocalTime slotTime = LocalTime.parse(time);

                            if (startTimeDropdown.getValue() != null && slotTime.isBefore(LocalTime.parse(startTimeDropdown.getValue()))) continue;
                            if (endTimeDropdown.getValue() != null && slotTime.isAfter(LocalTime.parse(endTimeDropdown.getValue()))) continue;

                            boolean isAvailable = !serverResponse.contains(time);
                            String venueName = roomToVenueMap.get(hiddenRoomId);

                            collectedSlots.add(new SlotData(time, isAvailable, targetLec, venueName));
                        }
                    }

                    // 3. Sort chronologically by time
                    collectedSlots.sort(java.util.Comparator.comparing(s -> s.timeObj));

                    // 4. Draw the UI Cards in the correct order
                    Platform.runLater(() -> {
                        for (SlotData slot : collectedSlots) {
                            HBox card = new HBox(20);
                            card.setPadding(new Insets(12, 20, 12, 20));
                            card.setAlignment(Pos.CENTER_LEFT);
                            card.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px;");

                            Label timeLabel = new Label(slot.timeStr);
                            timeLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
                            timeLabel.setTextFill(javafx.scene.paint.Color.web("#2D3748"));
                            timeLabel.setPrefWidth(70);

                            Label statusLabel = new Label(slot.isAvailable ? "Available" : "Booked");
                            statusLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
                            statusLabel.setTextFill(javafx.scene.paint.Color.web(slot.isAvailable ? "#38A169" : "#E53E3E"));
                            statusLabel.setPrefWidth(120);

                            VBox detailsBox = new VBox(2);
                            detailsBox.setAlignment(Pos.CENTER_LEFT);

                            if (isModuleOnlySearch) {
                                Label lecLabel = new Label(slot.lecturerName);
                                lecLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
                                lecLabel.setTextFill(javafx.scene.paint.Color.web("#2B6CB0"));
                                detailsBox.getChildren().add(lecLabel);
                            }

                            Label venueLabel = new Label(slot.venueName);
                            venueLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 13));
                            venueLabel.setTextFill(javafx.scene.paint.Color.web("#718096"));
                            detailsBox.getChildren().add(venueLabel);

                            Region spacer = new Region();
                            HBox.setHgrow(spacer, Priority.ALWAYS);

                            card.getChildren().addAll(timeLabel, statusLabel, detailsBox, spacer);

                            if (slot.isAvailable) {
                                Button bookBtn = new Button("Book Now");
                                bookBtn.setStyle("-fx-background-color: #3182CE; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 6px; -fx-cursor: hand;");
                                bookBtn.setPrefWidth(120);
                                bookBtn.setPrefHeight(38);

                                bookBtn.setOnAction(e -> {
                                    // Fetch the hidden room ID for this specific lecturer
                                    String roomId = lecturerToRoomMap.get(slot.lecturerName);

                                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                                    alert.setTitle("Booking Handoff");
                                    alert.setHeaderText("Proceed to Book Resource Tab");
                                    alert.setContentText(
                                            "To finalize this appointment, please switch to the 'Book Resource' tab.");
                                    alert.showAndWait();
                                });
                                card.getChildren().add(bookBtn);
                            } else {
                                Region emptyBtnSpace = new Region();
                                emptyBtnSpace.setPrefWidth(120);
                                card.getChildren().add(emptyBtnSpace);
                            }

                            resultsContainer.getChildren().add(card);
                        }
                    });

                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR, "Backend Error: " + ex.getMessage());
                        alert.showAndWait();
                    });
                }
            });
        });
    }
}