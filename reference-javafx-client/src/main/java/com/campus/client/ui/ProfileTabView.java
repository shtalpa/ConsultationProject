package com.campus.client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.concurrent.ExecutorService;

/**
 * A read-only, administrative locked profile view component panel.
 * Routes structural information adjustment requests directly to Campus Central.
 */
public final class ProfileTabView extends VBox {

    // Hardcoded baseline profile properties matching your assignment team details
    private final TextField idField = new TextField("0377129");
    private final TextField nameField = new TextField("Jane Doe");
    private final TextField programField = new TextField("Bachelor of Computer Science (Hons)");
    private final TextField deptField = new TextField("School of Computer Science (SCS)");
    private final Button requestChangeButton = new Button("Request Profile Modification");

    private static final String FONT_FAMILY = "Segoe UI";
    private static final String BRAND_PRIMARY = "#2B6CB0";

    /**
     * Constructor accepting ExecutorService to keep MainView's instantiating sequence fully compatible.
     */
    public ProfileTabView(ExecutorService executor) {
        // Configuration Layout View
        this.setSpacing(15);
        this.setPadding(new Insets(24));
        this.setStyle("-fx-background-color: #FFFFFF;");
        this.setAlignment(Pos.TOP_LEFT);

        setupLayout();
        setupEventHandlers();
    }

    private void setupLayout() {
        Label header = new Label("OFFICIAL UNIVERSITY PROFILE");
        header.setFont(Font.font(FONT_FAMILY, FontWeight.BOLD, 14));
        header.setTextFill(javafx.scene.paint.Color.web(BRAND_PRIMARY));

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(12);
        grid.setPadding(new Insets(10, 0, 15, 0));

        // Enforce explicit security lock down on fields
        styleReadOnlyField(idField);
        styleReadOnlyField(nameField);
        styleReadOnlyField(programField);
        styleReadOnlyField(deptField);

        // Map layout nodes
        grid.add(createFormLabel("Student ID:"), 0, 0);
        grid.add(idField, 1, 0);

        grid.add(createFormLabel("Full Name:"), 0, 1);
        grid.add(nameField, 1, 1);

        grid.add(createFormLabel("Academic Program:"), 0, 2);
        grid.add(programField, 1, 2);

        grid.add(createFormLabel("Faculty Department:"), 0, 3);
        grid.add(deptField, 1, 3);

        // Style the request button to explicitly prompt the user for corporate routing
        requestChangeButton.setFont(Font.font(FONT_FAMILY, FontWeight.BOLD, 13));
        requestChangeButton.setTextFill(javafx.scene.paint.Color.WHITE);
        requestChangeButton.setPadding(new Insets(10, 20, 10, 20));
        requestChangeButton.setCursor(javafx.scene.Cursor.HAND);
        requestChangeButton.setStyle("-fx-background-color: " + BRAND_PRIMARY + "; -fx-background-radius: 6px;");

        this.getChildren().addAll(header, grid, requestChangeButton);
    }

    private Label createFormLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font(FONT_FAMILY, FontWeight.MEDIUM, 13));
        label.setTextFill(javafx.scene.paint.Color.web("#4A5568"));
        return label;
    }

    private void styleReadOnlyField(TextField field) {
        field.setEditable(false);
        field.setFocusTraversable(false);
        field.setMouseTransparent(true); // Disables pointer carets to visually emphasize locked data records
        field.setFont(Font.font(FONT_FAMILY, 13));
        field.setPadding(new Insets(8));
        field.setPrefWidth(340);
        field.setStyle("-fx-background-color: #EDF2F7; -fx-text-fill: #2D3748; -fx-border-color: #CBD5E0; -fx-border-radius: 4px; -fx-background-radius: 4px;");
    }

    private void setupEventHandlers() {
        requestChangeButton.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Information Management Alert");
            alert.setHeaderText("Secure Profile Lock Restricted");
            alert.setContentText(
                    "Core preference properties and identity criteria are handled explicitly by campus central.\n\n" +
                            "To modify your program track, department registry, or credentials details, please log an official support request via the Campus Central Portal or visit a Campus Central service counter."
            );

            // Refine font layouts inside the dialog pane
            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.setStyle("-fx-font-family: '" + FONT_FAMILY + "';");

            alert.showAndWait();
        });
    }

    /**
     * Optional programmatic update hook to update details dynamically from a login response token if necessary,
     * maintaining clean object encapsulation properties.
     */
    public void setProfileInformation(String studentId, String fullName, String program, String faculty) {
        if (studentId != null) idField.setText(studentId);
        if (fullName != null) nameField.setText(fullName);
        if (program != null) programField.setText(program);
        if (faculty != null) deptField.setText(faculty);
    }
}
