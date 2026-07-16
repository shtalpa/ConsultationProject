package com.campus.client.ui;

import java.time.LocalDate;
import java.time.LocalTime;
import javafx.scene.control.DateCell;
import com.campus.client.mcp.CampusMcpClient;
import com.campus.client.rag.RagService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * A professional conversational user interface structured for the Student Workspace role.
 * Organizes operations into a horizontal top tab layout to isolate distinct tasks,
 * launching with the System Capabilities view by default.
 */
public final class MainView {

    private final BorderPane root = new BorderPane();
    private final Label status = new Label("System Status: Ready");

    // Top Navigation Tabs
    private final Button navDiscoveryButton = new Button("System Capabilities");
    private final Button navChatButton = new Button("AI Assistant");
    private final Button navAvailabilityButton = new Button("Lecturer Availability");
    private final Button navBookingButton = new Button("Book Resource");
    private final Button navProfileButton = new Button("My Profile");

    // Page View Components (Decoupled & Inline Architectures Normalized)
    private VBox chatPageView;
    private VBox discoveryPageView;
    private VBox bookingPageView;
    private VBox availabilityPageView; // Retained for fallback structural checks
    private final LecturerAvailabilityView lecturerAvailabilityView = new LecturerAvailabilityView();
    private ProfileTabView profileTabView;

    // RAG Chat Panel Layout Elements
    private final ScrollPane chatScrollPane = new ScrollPane();
    private final VBox chatTimeline = new VBox(12);
    private final TextField questionField = new TextField();
    private final Button askButton = new Button("Send ➔");

    private final TextArea discoveryArea = new TextArea();

    // ===== LECTURER / ROOM AVAILABILITY legacy CONTROLS =====
    private final DatePicker availabilityDatePicker = new DatePicker();
    private final TextField buildingField = new TextField();
    private final Spinner<String> availStartTime = new Spinner<>();
    private final Spinner<String> availEndTime = new Spinner<>();
    private final Button checkAvailabilityButton = new Button("Check Availability");
    private final TextArea availabilityResultArea = new TextArea();

    // ===== BOOK RESOURCE CONTROLS =====
    private final TextField resourceIdField = new TextField();
    private final DatePicker bookingDatePicker = new DatePicker();
    private final Spinner<String> startTimeField = new Spinner<>();
    private final Spinner<String> endTimeField = new Spinner<>();
    private final TextField studentIdField = new TextField();

    private final Button bookButton = new Button("Book Resource");
    private final TextArea bookingResultArea = new TextArea();

    // Asynchronous managed background lifecycle single-thread executor execution layer
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ui-worker");
        t.setDaemon(true);
        return t;
    });

    private CampusMcpClient mcp;
    private RagService rag;

    // Styling Design Constants
    private static final String FONT_FAMILY = "Segoe UI";
    private static final String FONT_MONO = "Consolas";
    private static final String BRAND_PRIMARY = "#2B6CB0";
    private static final String CHAT_BG_USER = "#EBF8FF";
    private static final String CHAT_BG_AI = "#F7FAFC";

    public MainView() {

        // Enforce defensive guard parameters on date node picker cells
        bookingDatePicker.setDayCellFactory(picker ->
                new DateCell() {
                    @Override
                    public void updateItem(LocalDate date, boolean empty) {
                        super.updateItem(date, empty);
                        if (date.isBefore(LocalDate.now())) {
                            setDisable(true);
                        }
                    }
                });

        availabilityDatePicker.setDayCellFactory(picker ->
                new DateCell() {
                    @Override
                    public void updateItem(LocalDate date, boolean empty) {
                        super.updateItem(date, empty);
                        if (date.isBefore(LocalDate.now())) {
                            setDisable(true);
                        }
                    }
                });

        // --- 1. TOP HEADER PANEL (BRANDING + TABS) ---
        VBox topContainer = new VBox(0);

        HBox headerBanner = new HBox(8);
        headerBanner.setPadding(new Insets(16, 24, 12, 24));
        headerBanner.setAlignment(Pos.CENTER_LEFT);
        headerBanner.setStyle("-fx-background-color: #1A202C;");

        Label heading = new Label("Campus Workspace Client");
        heading.setTextFill(javafx.scene.paint.Color.WHITE);
        heading.setFont(Font.font(FONT_FAMILY, FontWeight.BOLD, 18));

        Label subHeading = new Label(" • Student Persona Node");
        subHeading.setTextFill(javafx.scene.paint.Color.web("#A0AEC0"));
        subHeading.setFont(Font.font(FONT_FAMILY, FontWeight.NORMAL, 12));
        headerBanner.getChildren().addAll(heading, subHeading);

        HBox topTabBar = new HBox(4);
        topTabBar.setPadding(new Insets(0, 24, 0, 24));
        topTabBar.setAlignment(Pos.BOTTOM_LEFT);
        topTabBar.setStyle("-fx-background-color: #1A202C;");

        styleTabButton(navDiscoveryButton);
        styleTabButton(navChatButton);
        styleTabButton(navAvailabilityButton);
        styleTabButton(navBookingButton);
        styleTabButton(navProfileButton);

        topTabBar.getChildren().addAll(
                navDiscoveryButton,
                navChatButton,
                navAvailabilityButton,
                navBookingButton,
                navProfileButton
        );

        topContainer.getChildren().addAll(headerBanner, topTabBar);
        root.setTop(topContainer);

        // --- 2. PAGE VIEW VIEWS STACK INITIALIZATION ---
        initDiscoveryPage();
        initChatPage();
        initAvailabilityPage(); // Keeps inline logic synchronized for backup checks
        initBookingPage();
        this.profileTabView = new ProfileTabView(this.worker);

        // Central Display Stack Panel - Mount views systematically
        StackPane contentStack = new StackPane();
        contentStack.getChildren().addAll(
                discoveryPageView,
                chatPageView,
                availabilityPageView,
                lecturerAvailabilityView.getView(), // Standalone structural page container template
                bookingPageView,
                profileTabView
        );
        root.setCenter(contentStack);

        // Displays capabilities view first by default
        showPage(discoveryPageView, navDiscoveryButton);

        // --- 3. PERSISTENT SYSTEM STATUS FOOTER ---
        HBox footerBar = new HBox();
        footerBar.setPadding(new Insets(8, 20, 8, 20));
        footerBar.setAlignment(Pos.CENTER_LEFT);
        footerBar.setStyle("-fx-background-color: #EDF2F7; -fx-border-color: #CBD5E0; -fx-border-width: 1 0 0 0;");

        status.setFont(Font.font(FONT_FAMILY, FontWeight.MEDIUM, 12));
        status.setTextFill(javafx.scene.paint.Color.web("#4A5568"));
        status.setWrapText(true);
        footerBar.getChildren().add(status);
        root.setBottom(footerBar);

        setEnabled(false);
        wire();
    }

    public BorderPane getRoot() {
        return root;
    }

    public ProfileTabView getProfileTabView() {
        return profileTabView;
    }

    public void bind(CampusMcpClient mcp, RagService rag) {
        this.mcp = mcp;
        this.rag = rag;
        this.lecturerAvailabilityView.bind(mcp); // Complete integration step from version 3
        setEnabled(true);
        if (rag == null) {
            askButton.setDisable(true);
        }
    }

    public void setStatus(String text) {
        Platform.runLater(() -> status.setText("System Status: " + text));
    }

    public void refreshDiscovery() {
        if (mcp == null) return;
        worker.submit(() -> {
            try {
                String tools = mcp.listTools().stream()
                        .map(t -> "  • " + t.name() + " \n    ↳ " + t.description())
                        .collect(Collectors.joining("\n\n"));
                String resources = mcp.listResources().stream()
                        .map(r -> "  • " + r.uri() + " (" + r.name() + ")")
                        .collect(Collectors.joining("\n"));
                String prompts = mcp.listPrompts().stream()
                        .map(p -> "  • " + p.name() + " \n    ↳ " + p.description())
                        .collect(Collectors.joining("\n\n"));
                String text = "=== REGISTERED CAPABILITY TOOLS ===\n" + tools +
                        "\n\n=== EXPOSED CAMPUS FILE RESOURCES ===\n" + resources +
                        "\n\n=== PRE-CONFIGURED FRAME PROMPTS ===\n" + prompts;
                Platform.runLater(() -> discoveryArea.setText(text));
            } catch (Exception e) {
                Platform.runLater(() -> discoveryArea.setText("Discovery context initialization fail: " + e.getMessage()));
            }
        });
    }

    // ---- Page Construction Initializations ----------------------------------------------

    private void initDiscoveryPage() {
        styleOutputTextArea(discoveryArea);
        VBox.setVgrow(discoveryArea, Priority.ALWAYS);

        discoveryPageView = new VBox(12,
                createSectionHeaderLabel("Advertised Server Schema Structure"),
                discoveryArea);
        discoveryPageView.setPadding(new Insets(24));
        discoveryPageView.setStyle("-fx-background-color: #FFFFFF;");
    }

    private void initChatPage() {
        chatTimeline.setPadding(new Insets(8));
        chatTimeline.setStyle("-fx-background-color: #FFFFFF;");

        chatScrollPane.setContent(chatTimeline);
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setStyle("-fx-background: #FFFFFF; -fx-border-color: #E2E8F0; -fx-border-radius: 4px;");
        VBox.setVgrow(chatScrollPane, Priority.ALWAYS);

        styleInputField(questionField, "Ask about rules, or browse availability (e.g., 'When is Dr Steve free?')");
        styleActionButton(askButton, BRAND_PRIMARY);

        HBox inputDock = new HBox(8);
        inputDock.setAlignment(Pos.CENTER_LEFT);
        inputDock.setPadding(new Insets(8, 0, 0, 0));
        HBox.setHgrow(questionField, Priority.ALWAYS);
        inputDock.getChildren().addAll(questionField, askButton);

        appendMessageBubble("Hello Student! I am your Taylor's University Workspace assistant. You can ask me campus policy questions or query open slots directly.", false);

        chatPageView = new VBox(8,
                createSectionHeaderLabel("Grounded Student Virtual Assistant (RAG Chat)"),
                chatScrollPane,
                inputDock);
        chatPageView.setPadding(new Insets(24));
        chatPageView.setStyle("-fx-background-color: #FFFFFF;");
    }

    private void initAvailabilityPage() {
        styleInputField(buildingField, "Building (Optional)");
        styleActionButton(checkAvailabilityButton, BRAND_PRIMARY);
        styleOutputTextArea(availabilityResultArea);

        // Incorporates full time choices plus an "Any" sentinel tracking block from Version 2
        ObservableList<String> availTimes = FXCollections.observableArrayList();
        availTimes.add("Any");
        for (int h = 8; h <= 22; h++) {
            for (int m = 0; m < 60; m += 30) {
                availTimes.add(String.format("%02d:%02d", h, m));
            }
        }
        availStartTime.setEditable(true);
        availEndTime.setEditable(true);
        availStartTime.setValueFactory(new SpinnerValueFactory.ListSpinnerValueFactory<>(availTimes));
        availEndTime.setValueFactory(new SpinnerValueFactory.ListSpinnerValueFactory<>(availTimes));

        HBox row1 = new HBox(10, new Label("Date"), availabilityDatePicker);
        HBox row2 = new HBox(10, new Label("Building"), buildingField);
        HBox row3 = new HBox(10,
                new Label("Start Time"), availStartTime,
                new Label("End Time"), availEndTime);

        availabilityPageView = new VBox(12,
                createSectionHeaderLabel("Backup Room Availability"),
                row1, row2, row3, checkAvailabilityButton, availabilityResultArea
        );
        availabilityPageView.setPadding(new Insets(24));
        VBox.setVgrow(availabilityResultArea, Priority.ALWAYS);
    }

    private void initBookingPage() {
        styleInputField(resourceIdField, "Resource ID");
        ObservableList<String> times = FXCollections.observableArrayList();

        for (int h = 8; h <= 22; h++) {
            for (int m = 0; m < 60; m += 30) {
                times.add(String.format("%02d:%02d", h, m));
            }
        }

        startTimeField.setEditable(true);
        endTimeField.setEditable(true);

        startTimeField.setValueFactory(new SpinnerValueFactory.ListSpinnerValueFactory<>(times));
        endTimeField.setValueFactory(new SpinnerValueFactory.ListSpinnerValueFactory<>(times));
        styleInputField(studentIdField, "Student ID");

        styleActionButton(bookButton, BRAND_PRIMARY);
        styleOutputTextArea(bookingResultArea);

        bookingPageView = new VBox(12,
                createSectionHeaderLabel("Book Resource"),
                new Label("Resource ID"), resourceIdField,
                new Label("Date"), bookingDatePicker,
                new Label("Start Time"), startTimeField,
                new Label("End Time"), endTimeField,
                new Label("Student ID"), studentIdField,
                bookButton, bookingResultArea
        );
        bookingPageView.setPadding(new Insets(24));
        VBox.setVgrow(bookingResultArea, Priority.ALWAYS);
    }

    private void showPage(Pane activePage, Button activeNavButton) {
        discoveryPageView.setVisible(false);
        chatPageView.setVisible(false);
        availabilityPageView.setVisible(false);
        lecturerAvailabilityView.getView().setVisible(false);
        bookingPageView.setVisible(false);
        if (profileTabView != null) {
            profileTabView.setVisible(false);
        }
        activePage.setVisible(true);

        String inactiveStyle = "-fx-background-color: #2D3748; -fx-text-fill: #A0AEC0; -fx-background-radius: 4px 4px 0px 0px;";
        navDiscoveryButton.setStyle(inactiveStyle);
        navChatButton.setStyle(inactiveStyle);
        navAvailabilityButton.setStyle(inactiveStyle);
        navBookingButton.setStyle(inactiveStyle);
        navProfileButton.setStyle(inactiveStyle);

        activeNavButton.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: " + BRAND_PRIMARY + "; -fx-background-radius: 4px 4px 0px 0px;");
    }

    // ---- Style Component Factories ------------------------------------------------------
    private void styleTabButton(Button btn) {
        btn.setFont(Font.font(FONT_FAMILY, FontWeight.BOLD, 13));
        btn.setPadding(new Insets(10, 20, 10, 20));
        btn.setCursor(javafx.scene.Cursor.HAND);
        btn.setPrefWidth(145);
    }

    private Label createSectionHeaderLabel(String text) {
        Label lbl = new Label(text.toUpperCase());
        lbl.setFont(Font.font(FONT_FAMILY, FontWeight.BOLD, 12));
        lbl.setTextFill(javafx.scene.paint.Color.web(BRAND_PRIMARY));
        return lbl;
    }

    private void styleInputField(TextField field, String placeholder) {
        field.setPromptText(placeholder);
        field.setFont(Font.font(FONT_FAMILY, 13)); 
        field.setPadding(new Insets(10));
        field.setPrefWidth(320);
        field.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #CBD5E0; -fx-border-radius: 4px; -fx-background-radius: 4px;");
    }

    private void styleOutputTextArea(TextArea area) {
        area.setEditable(false);
        area.setWrapText(true);
        area.setFont(Font.font(FONT_MONO, 12));
        area.setStyle("-fx-control-inner-background: #F7FAFC; -fx-text-fill: #1A202C; -fx-border-color: #E2E8F0;");
    }

    private void styleActionButton(Button btn, String baseHexColor) {
        btn.setFont(Font.font(FONT_FAMILY, FontWeight.BOLD, 13));
        btn.setTextFill(javafx.scene.paint.Color.WHITE);
        btn.setPadding(new Insets(12, 24, 12, 24));
        btn.setCursor(javafx.scene.Cursor.HAND);
        btn.setStyle("-fx-background-color: " + baseHexColor + "; -fx-background-radius: 6px;");
    }

    private void appendMessageBubble(String text, boolean isUser) {
        Platform.runLater(() -> {
            Label label = new Label(text);
            label.setWrapText(true);
            label.setFont(Font.font(FONT_FAMILY, 13));
            label.setMaxWidth(520);
            label.setPadding(new Insets(10, 14, 10, 14));

            HBox bubbleWrapper = new HBox();
            if (isUser) {
                bubbleWrapper.setAlignment(Pos.CENTER_RIGHT);
                label.setStyle("-fx-background-color: " + CHAT_BG_USER + "; -fx-text-fill: #2B6CB0; -fx-background-radius: 16px 16px 2px 16px;");
            } else {
                bubbleWrapper.setAlignment(Pos.CENTER_LEFT);
                label.setStyle("-fx-background-color: " + CHAT_BG_AI + "; -fx-text-fill: #2D3748; -fx-border-color: #E2E8F0; -fx-border-radius: 16px 16px 16px 2px; -fx-background-radius: 16px 16px 16px 2px;");
            }

            bubbleWrapper.getChildren().add(label);
            chatTimeline.getChildren().add(bubbleWrapper);
            chatScrollPane.setVvalue(1.0);
        });
    }

    // ---- Functional Asynchronous Event Wiring Actions ------------------------------------------------

    private void wire() {
        navDiscoveryButton.setOnAction(_ -> showPage(discoveryPageView, navDiscoveryButton));
        navChatButton.setOnAction(_ -> showPage(chatPageView, navChatButton));
        // Routes navigation directly to the decoupled encapsulated lecturer canvas pane
        navAvailabilityButton.setOnAction(_ -> showPage(lecturerAvailabilityView.getView(), navAvailabilityButton));
        navBookingButton.setOnAction(_ -> showPage(bookingPageView, navBookingButton));
        navProfileButton.setOnAction(_ -> showPage(profileTabView, navProfileButton));

        questionField.setOnAction(_ -> askButton.fire());

        askButton.setOnAction(_ -> {
            String q = questionField.getText().trim();
            if (q.isEmpty() || rag == null) return;

            appendMessageBubble(q, true);
            questionField.clear();

            setEnabled(false);
            setStatus("Query payload parsing inside structural fallback thread pool...");

            worker.submit(() -> {
                try {
                    RagService.RagResult r = rag.ask(q);
                    appendMessageBubble(r.answer(), false);
                    Platform.runLater(() -> {
                        setEnabled(true);
                        setStatus("Ready");
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        appendMessageBubble("RAG Engine Error: An error occurred processing your request.", false);
                        setEnabled(true);
                        setStatus("Ready");
                    });
                }
            });
        });

        // Retained for validation parity checks on full time slot specifications
        checkAvailabilityButton.setOnAction(_ -> {
            if (mcp == null) return;

            worker.submit(() -> {
                try {
                    LocalDate selectedDate = availabilityDatePicker.getValue();
                    if (selectedDate == null) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Invalid Date");
                            alert.setHeaderText("No date selected");
                            alert.setContentText("Please select a date to check availability.");
                            alert.showAndWait();
                        });
                        return;
                    }

                    if (selectedDate.isBefore(LocalDate.now())) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Invalid Date");
                            alert.setHeaderText("Past Date Selected");
                            alert.setContentText("You cannot check availability for a past date.");
                            alert.showAndWait();
                        });
                        return;
                    }

                    String start = availStartTime.getValue();
                    String end = availEndTime.getValue();
                    boolean useTime = start != null && end != null && !"Any".equals(start) && !"Any".equals(end);

                    if (useTime && !LocalTime.parse(end).isAfter(LocalTime.parse(start))) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Invalid Time");
                            alert.setHeaderText("End Time Error");
                            alert.setContentText("End time must be later than start time.");
                            alert.showAndWait();
                        });
                        return;
                    }

                    String result = mcp.checkRoomAvailability(
                            selectedDate.toString(),
                            buildingField.getText(),
                            useTime ? start : null,
                            useTime ? end : null
                    );
                    Platform.runLater(() -> availabilityResultArea.setText(result));
                } catch (Exception ex) {
                    Platform.runLater(() -> availabilityResultArea.setText("Could not check availability: " + ex.getMessage()));
                }
            });
        });

        bookButton.setOnAction(_ -> {
            if (mcp == null) return;

            worker.submit(() -> {
                try {
                    LocalDate selectedDate = bookingDatePicker.getValue();
                    if (selectedDate == null) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Invalid Date");
                            alert.setHeaderText("No date selected");
                            alert.setContentText("Please select a booking date.");
                            alert.showAndWait();
                        });
                        return;
                    }
                    LocalTime startTime = LocalTime.parse(startTimeField.getValue());
                    LocalTime endTime = LocalTime.parse(endTimeField.getValue());

                    if (selectedDate.isBefore(LocalDate.now())) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Invalid Date");
                            alert.setHeaderText("Past Date Selected");
                            alert.setContentText("You cannot book a date in the past.");
                            alert.showAndWait();
                        });
                        return;
                    }
                    if (!endTime.isAfter(startTime)) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Invalid Time");
                            alert.setHeaderText("End Time Error");
                            alert.setContentText("End time must be later than start time.");
                            alert.showAndWait();
                        });
                        return;
                    }
                    String result = mcp.bookResource(
                            resourceIdField.getText(),
                            selectedDate.toString(),
                            startTimeField.getValue(),
                            endTimeField.getValue(),
                            studentIdField.getText()
                    );

                    Platform.runLater(() -> {
                        bookingResultArea.setText(result);
                        if (result.startsWith("ERROR:")) {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Booking Failed");
                            alert.setHeaderText("Unable to create booking");
                            alert.setContentText(result);
                            alert.showAndWait();
                        } else {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("Booking Successful");
                            alert.setHeaderText("Booking Created");
                            alert.setContentText("Reference Number: " + result);
                            alert.showAndWait();
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> bookingResultArea.setText(ex.getMessage()));
                }
            });
        });
    }

    private void setEnabled(boolean enabled) {
        askButton.setDisable(!enabled || rag == null);
        questionField.setDisable(!enabled || rag == null);
    }
}
