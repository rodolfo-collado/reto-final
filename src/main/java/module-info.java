module com.practice.retofinal {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.practice.retofinal to javafx.fxml;
    exports com.practice.retofinal;
}