package personal.grocery_splitter;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReceiptSplitterController {

    @FXML private TableView<Item> itemTable;
    @FXML private TableColumn<Item, String> itemNameColumn;
    @FXML private TableColumn<Item, Double> itemPriceColumn;
    @FXML private ComboBox<String> purchaserDropdown;

    private final ObservableList<Item> items = FXCollections.observableArrayList();
    private final ObservableList<String> people = FXCollections.observableArrayList();
    private final Map<String, Double> totals = new HashMap<>();
    private final Map<Item, Set<String>> itemAssignments = new HashMap<>();

    @FXML
    public void initialize() {
        // Set up the table columns
        itemNameColumn.setCellValueFactory(data -> data.getValue().itemNameProperty());
        itemPriceColumn.setCellValueFactory(data -> data.getValue().itemPriceProperty().asObject());

        // Create the column for checkboxes
        TableColumn<Item, Void> assignColumn = new TableColumn<>("Assign");
        assignColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null) {
                    setGraphic(null);
                } else {
                    Item currentItem = getTableRow().getItem();
                    VBox checkboxContainer = new VBox();
                    for (String person : people) {
                        CheckBox checkBox = new CheckBox(person);
                        checkBox.setSelected(itemAssignments.getOrDefault(currentItem, new HashSet<>()).contains(person));
                        checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                            if (newValue) {
                                itemAssignments.computeIfAbsent(currentItem, k -> new HashSet<>()).add(person);
                            } else {
                                Set<String> owners = itemAssignments.getOrDefault(currentItem, new HashSet<>());
                                owners.remove(person);
                                if (owners.isEmpty()) {
                                    itemAssignments.remove(currentItem);
                                } else {
                                    itemAssignments.put(currentItem, owners);
                                }
                            }
                        });
                        checkboxContainer.getChildren().add(checkBox);
                    }
                    setGraphic(checkboxContainer);
                }
            }
        });

        itemTable.getColumns().add(assignColumn);

        // Set the items for the table and the dropdown for the purchaser
        itemTable.setItems(items);
        purchaserDropdown.setItems(people);
    }

    @FXML
    private void onUploadReceipt() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Walmart Receipt PDF");
        File file = fileChooser.showOpenDialog(itemTable.getScene().getWindow());
        if (file != null) {
            parseReceipt(file);
        }
    }

    @FXML
    private void onAddPerson() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Person");
        dialog.setHeaderText("Enter the name of the person to add:");
        dialog.setContentText("Name:");
        dialog.showAndWait().ifPresent(name -> {
            if (!people.contains(name)) {
                people.add(name);
                // Ensure the person is added to the ComboBox only once
                if (!purchaserDropdown.getItems().contains(name)) {
                    purchaserDropdown.getItems().add(name);
                }

                // Update item assignments for each item in the table
                itemTable.refresh(); // Refresh the table to reflect the changes
            }
        });
    }

    @FXML
    private void onFinish() {
        String purchaser = purchaserDropdown.getValue();
        if (purchaser == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "Please select a purchaser.");
            return;
        }

        Map<String, Double> personTotals = new HashMap<>();
        for (Map.Entry<Item, Set<String>> entry : itemAssignments.entrySet()) {
            Item item = entry.getKey();
            Set<String> assignedPeople = entry.getValue();
            if (!assignedPeople.isEmpty()) {
                double share = item.getItemPrice() / assignedPeople.size();
                for (String person : assignedPeople) {
                    personTotals.put(person, personTotals.getOrDefault(person, 0.0) + share);
                }
            }
        }

        StringBuilder summary = new StringBuilder("Summary:\n");
        for (Map.Entry<String, Double> entry : personTotals.entrySet()) {
            if (!entry.getKey().equals(purchaser)) {
                summary.append(entry.getKey()).append(" owes ").append(purchaser).append(": $")
                        .append(String.format("%.2f", entry.getValue())).append("\n");
            }
        }
        showAlert(Alert.AlertType.INFORMATION, "Summary", summary.toString());
    }

    public void parseReceipt(File receiptFile) {
        try {
            if (receiptFile.getName().endsWith(".pdf")) {
                PDDocument document = PDDocument.load(receiptFile);
                PDFRenderer pdfRenderer = new PDFRenderer(document);
                BufferedImage image = pdfRenderer.renderImageWithDPI(0, 300);
                String result = performOCR(image);
                processItemsFromOCR(result);
                document.close();
            } else if (receiptFile.getName().endsWith(".jpg") || receiptFile.getName().endsWith(".png")) {
                BufferedImage image = ImageIO.read(receiptFile);
                String result = performOCR(image);
                processItemsFromOCR(result);
            } else {
                showAlert(Alert.AlertType.ERROR, "Error", "Unsupported file type. Please upload a PDF or image file.");
            }
        } catch (Exception e) {
            System.err.println("Error processing receipt: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Error", "An error occurred while processing the receipt.");
        }
    }

    private String performOCR(BufferedImage image) throws TesseractException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
        tesseract.setLanguage("eng");
        return tesseract.doOCR(image);
    }

    private void processItemsFromOCR(String result) {
        String itemRegex = "([A-Za-z\\s-]+(?=\\s\\d{12}))";
        Pattern itemPattern = Pattern.compile(itemRegex);
        Matcher itemMatcher = itemPattern.matcher(result);
        items.clear();
        String[] lines = result.split("\\r?\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher matcher = itemPattern.matcher(line);

            if (matcher.find()) {
                String itemName = matcher.group(1).trim();
                if (itemName.equals("SUBTOTAL")) continue;

                double itemPrice = 0.0;
                if (i + 1 < lines.length && lines[i + 1].contains("@")) {
                    String weightLine = lines[i + 1];
                    String priceRegex = "\\d+\\.\\d{2}$";
                    Matcher priceMatcher = Pattern.compile(priceRegex).matcher(weightLine);
                    if (priceMatcher.find()) {
                        itemPrice = Double.parseDouble(priceMatcher.group());
                    }
                    i++;
                } else {
                    String priceRegex = "\\d+\\.\\d{2}$";
                    Matcher priceMatcher = Pattern.compile(priceRegex).matcher(line);
                    if (priceMatcher.find()) {
                        itemPrice = Double.parseDouble(priceMatcher.group());
                    }
                }
                items.add(new Item(itemName, itemPrice));
            }
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}