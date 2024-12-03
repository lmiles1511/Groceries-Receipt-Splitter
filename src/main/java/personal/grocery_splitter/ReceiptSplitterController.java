package personal.grocery_splitter;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReceiptSplitterController {

    @FXML private TableView<Item> itemTable;
    @FXML private TableColumn<Item, String> itemNameColumn;
    @FXML private TableColumn<Item, Double> itemPriceColumn;
    @FXML private TableColumn<Item, CheckBox> personColumn;
    @FXML private ComboBox<String> purchaserDropdown;

    private final ObservableList<Item> items = FXCollections.observableArrayList();
    private final ObservableList<String> people = FXCollections.observableArrayList();
    private final Map<String, Double> totals = new HashMap<>();

    @FXML
    public void initialize() {
        itemNameColumn.setCellValueFactory(data -> data.getValue().itemNameProperty());
        itemPriceColumn.setCellValueFactory(data -> data.getValue().itemPriceProperty().asObject());
        personColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(CheckBox checkBox, boolean empty) {
                super.updateItem(checkBox, empty);
                if (empty || getTableRow() == null) {
                    setGraphic(null);
                } else {
                    Item item = getTableRow().getItem();
                    if (item != null && !people.isEmpty()) {
                        CheckBox cb = new CheckBox();
                        cb.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                            if (isSelected) {
                                totals.put(people.get(0), totals.getOrDefault(people.get(0), 0.0) + item.getItemPrice());
                            }
                        });
                        setGraphic(cb);
                    }
                }
            }
        });

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
                purchaserDropdown.getItems().add(name);
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

        StringBuilder summary = new StringBuilder("Summary:\n");
        for (Map.Entry<String, Double> entry : totals.entrySet()) {
            if (!entry.getKey().equals(purchaser)) {
                summary.append(entry.getKey()).append(" owes ").append(purchaser).append(": $")
                        .append(entry.getValue()).append("\n");
            }
        }
        showAlert(Alert.AlertType.INFORMATION, "Summary", summary.toString());
    }

    public void parseReceipt(File receiptFile) {
        try {
            if (receiptFile.getName().endsWith(".pdf")) {
                // Handle PDF files using PDFBox
                PDDocument document = PDDocument.load(receiptFile);
                PDFRenderer pdfRenderer = new PDFRenderer(document);

                // Extract text from the first page
                BufferedImage image = pdfRenderer.renderImageWithDPI(0, 300);  // Convert PDF to image at 300 DPI

                // Perform OCR to extract text from the image
                String result = performOCR(image);

                // Process the OCR result to extract item names and prices
                processItemsFromOCR(result);

                document.close();
            } else if (receiptFile.getName().endsWith(".jpg") || receiptFile.getName().endsWith(".png")) {
                // Handle image files directly with Tesseract
                BufferedImage image = ImageIO.read(receiptFile);

                // Perform OCR to extract text from the image
                String result = performOCR(image);

                // Process the OCR result to extract item names and prices
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
        // Set up Tesseract instance
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata"); // Replace with the correct path

        // Set the language for OCR (e.g., English)
        tesseract.setLanguage("eng");

        // Perform OCR on the image
        return tesseract.doOCR(image);
    }

    private void processItemsFromOCR(String result) {
        // Regex to match item name before the 12-digit product code
        String itemRegex = "([A-Za-z\\s-]+(?=\\s\\d{12}))";
        Pattern itemPattern = Pattern.compile(itemRegex);
        Matcher itemMatcher = itemPattern.matcher(result);

        // Clear existing items before adding new ones
        items.clear();

        // Split the input into lines for processing
        String[] lines = result.split("\\r?\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher matcher = itemPattern.matcher(line);

            if (matcher.find()) {
                String itemName = matcher.group(1).trim();
                if (itemName.equals("SUBTOTAL")) continue;

                double itemPrice = 0.0;

                // Check if the next line contains "@" for weighed items
                if (i + 1 < lines.length && lines[i + 1].contains("@")) {
                    String weightLine = lines[i + 1];
                    String priceRegex = "\\d+\\.\\d{2}$"; // Match price at the end of the line
                    Matcher priceMatcher = Pattern.compile(priceRegex).matcher(weightLine);

                    if (priceMatcher.find()) {
                        itemPrice = Double.parseDouble(priceMatcher.group());
                    }
                    i++; // Skip the weight line after processing
                } else {
                    // Handle regular item price
                    String priceRegex = "\\d+\\.\\d{2}$";
                    Matcher priceMatcher = Pattern.compile(priceRegex).matcher(line);

                    if (priceMatcher.find()) {
                        itemPrice = Double.parseDouble(priceMatcher.group());
                    }
                }

                // Create a new Item and add it to the list
                Item item = new Item(itemName, itemPrice);
                items.add(item);
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
