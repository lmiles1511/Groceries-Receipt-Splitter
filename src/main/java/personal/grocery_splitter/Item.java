package personal.grocery_splitter;

import javafx.beans.property.*;

public class Item {
    private final StringProperty itemName;
    private final DoubleProperty itemPrice;

    public Item(String itemName, double itemPrice) {
        this.itemName = new SimpleStringProperty(itemName);
        this.itemPrice = new SimpleDoubleProperty(itemPrice);
    }

    public StringProperty itemNameProperty() {
        return itemName;
    }

    public DoubleProperty itemPriceProperty() {
        return itemPrice;
    }

    public String getItemName() {
        return itemName.get();
    }

    public void setItemName(String itemName) {
        this.itemName.set(itemName);
    }

    public double getItemPrice() {
        return itemPrice.get();
    }

    public void setItemPrice(double itemPrice) {
        this.itemPrice.set(itemPrice);
    }
}
