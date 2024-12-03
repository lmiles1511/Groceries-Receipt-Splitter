module personal.grocery_splitter {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.apache.pdfbox;
    requires java.desktop;
    requires tess4j;


    opens personal.grocery_splitter to javafx.fxml;
    exports personal.grocery_splitter;
}