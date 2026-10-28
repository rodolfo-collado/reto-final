package com.practice.retofinal;

import com.practice.retofinal.domain.*;
import com.practice.retofinal.persistence.TextMapRepository;
import com.practice.retofinal.service.MapService;
import javafx.application.Application;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import javafx.stage.*;
import java.nio.file.Path;
import java.util.*;

public final class MapApplication extends Application {
    private MapService service;
    private Stage stage;
    private final ListView<String> campuses = new ListView<>();
    private final ListView<Connection> connections = new ListView<>();
    private final ComboBox<String> edgeFrom = new ComboBox<>(), edgeTo = new ComboBox<>();
    private final ComboBox<String> routeFrom = new ComboBox<>(), routeTo = new ComboBox<>();
    private final Pane graph = new Pane();
    private final Label status = new Label("Listo"), count = new Label(), result = new Label("Selecciona origen y destino"), cost = new Label("—");
    private List<String> highlighted = List.of();

    @Override public void start(Stage stage) {
        this.stage = stage;
        Path storage = Path.of(System.getProperty("sedes.data", "data/mapa.tsv"));
        try { service = new MapService(storage, new TextMapRepository()); }
        catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No se pudo cargar " + storage.toAbsolutePath()
                    + "\n" + e.getMessage() + "\nCorrige el archivo o usa -Dsedes.data con otra ruta. El archivo se conserva.", ButtonType.CLOSE);
            alert.setHeaderText("No se puede abrir el mapa"); alert.showAndWait();
            javafx.application.Platform.exit(); return;
        }
        var root = new BorderPane(); root.getStyleClass().add("root-shell");
        Label title = new Label("Trayectos entre sedes"); title.getStyleClass().add("title");
        Label subtitle = new Label("MAPA DIRIGIDO  /  DIJKSTRA"); subtitle.getStyleClass().add("eyebrow");
        var heading = new VBox(6, subtitle, title); HBox.setHgrow(heading, Priority.ALWAYS);
        var top = new HBox(12, heading, button("Historial", this::showHistory), button("Importar mapa", this::importMap), button("Exportar mapa", this::exportMap));
        top.setAlignment(Pos.CENTER_LEFT); top.setPadding(new Insets(26, 28, 24, 28)); root.setTop(top);
        VBox editor = editor(); editor.setPrefWidth(300); editor.setMinWidth(260);
        graph.setMinSize(300, 260); graph.setPrefHeight(420); graph.getStyleClass().add("graph");
        graph.widthProperty().addListener((o,a,b) -> draw()); graph.heightProperty().addListener((o,a,b) -> draw());
        var legend = new Label("Las flechas indican el sentido del trayecto. El número es su costo."); legend.getStyleClass().add("muted"); legend.setWrapText(true);
        var mapCard = new VBox(14, section("Vista del mapa"), count, graph, legend); mapCard.getStyleClass().add("card"); VBox.setVgrow(graph, Priority.ALWAYS);
        routeFrom.setPromptText("Origen"); routeTo.setPromptText("Destino");
        routeFrom.setMaxWidth(Double.MAX_VALUE); routeTo.setMaxWidth(Double.MAX_VALUE);
        var routeInputs = new HBox(10, routeFrom, new Label("→"), routeTo, button("Calcular ruta", this::calculate));
        HBox.setHgrow(routeFrom, Priority.ALWAYS); HBox.setHgrow(routeTo, Priority.ALWAYS);
        cost.getStyleClass().add("cost"); result.setWrapText(true);
        var routeCard = new VBox(14, section("Encuentra el trayecto de menor costo"), routeInputs, cost, result); routeCard.getStyleClass().add("card");
        var content = new VBox(18, mapCard, routeCard); VBox.setVgrow(mapCard, Priority.ALWAYS);
        var body = new HBox(18, editor, content); HBox.setHgrow(content, Priority.ALWAYS); body.setPadding(new Insets(0,28,20,28)); root.setCenter(body);
        status.setWrapText(true); status.setPadding(new Insets(12,28,16,28)); root.setBottom(status);
        var scene = new Scene(root, 1180, 850);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("app.css")).toExternalForm());
        stage.setTitle("Sedes · Planificador de trayectos"); stage.setMinWidth(980); stage.setMinHeight(780); stage.setScene(scene);
        refresh(); status.setText("Guardado automático · " + storage.toAbsolutePath()); stage.show();
    }
    private VBox editor() {
        var name = new TextField(); name.setPromptText("Nombre de la sede");
        var add = button("Añadir sede", () -> { service.change(m -> m.addCampus(name.getText().strip())); name.clear(); refresh(); });
        campuses.setPrefHeight(140); campuses.setPlaceholder(new Label("Añade tu primera sede"));
        var remove = button("Eliminar sede seleccionada", () -> {
            String selected = campuses.getSelectionModel().getSelectedItem();
            if (selected == null) throw new IllegalArgumentException("Selecciona una sede para eliminar.");
            var confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Se eliminará «" + selected + "» y sus conexiones.", ButtonType.OK, ButtonType.CANCEL);
            confirmation.initOwner(stage); confirmation.setHeaderText("Eliminar sede");
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) { service.change(m -> m.removeCampus(selected)); refresh(); }
        });
        edgeFrom.setPromptText("Desde"); edgeTo.setPromptText("Hasta"); edgeFrom.setMaxWidth(Double.MAX_VALUE); edgeTo.setMaxWidth(Double.MAX_VALUE);
        var weight = new TextField(); weight.setPromptText("Costo entero ≥ 0");
        var connect = button("Guardar conexión", () -> {
            int value;
            try { value = Integer.parseInt(weight.getText().strip()); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Introduce un costo entero entre 0 y 2147483647."); }
            service.change(m -> m.connect(edgeFrom.getValue(), edgeTo.getValue(), value)); weight.clear(); refresh();
        });
        connections.setPrefHeight(140); connections.setPlaceholder(new Label("Sin conexiones"));
        var disconnect = button("Eliminar conexión seleccionada", () -> {
            var c = connections.getSelectionModel().getSelectedItem();
            if (c == null) throw new IllegalArgumentException("Selecciona una conexión para eliminar.");
            service.change(m -> m.disconnect(c.from(), c.to())); refresh();
        });
        Label hint = new Label("Una conexión existente se actualiza. El sentido inverso se añade por separado."); hint.setWrapText(true); hint.setMinHeight(Region.USE_PREF_SIZE); hint.getStyleClass().add("muted");
        var box = new VBox(10, section("01  Sedes"), name, add, campuses, remove, new Separator(), section("02  Conexiones"), edgeFrom, edgeTo, weight, connect, hint, connections, disconnect);
        box.getStyleClass().add("card"); VBox.setVgrow(campuses, Priority.ALWAYS); VBox.setVgrow(connections, Priority.ALWAYS); return box;
    }
    private Label section(String text) { var label = new Label(text); label.getStyleClass().add("section"); return label; }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private Button button(String text, Action action) {
        var b = new Button(text); b.setOnAction(event -> {
            try { action.run(); }
            catch (Exception e) {
                status.setText("No se completó la operación: " + e.getMessage());
                var alert = new Alert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.OK); alert.initOwner(stage); alert.setHeaderText("Revisa la operación"); alert.showAndWait();
            }
        }); return b;
    }
    private void refresh() {
        CampusMap map = service.snapshot(); campuses.getItems().setAll(map.campuses()); connections.getItems().setAll(map.connections());
        for (var combo : List.of(edgeFrom, edgeTo, routeFrom, routeTo)) {
            String selected = combo.getValue(); combo.getItems().setAll(map.campuses());
            if (selected != null && map.campuses().contains(selected)) combo.setValue(selected);
            else combo.setValue(null);
        }
        highlighted = List.of(); cost.setText("—"); result.setText("Selecciona origen y destino");
        count.setText(map.campuses().size() + " sedes  ·  " + map.connections().size() + " conexiones");
        status.setText("Mapa listo · Los cambios se guardan automáticamente"); draw();
    }
    private void calculate() throws Exception {
        var route = service.calculate(routeFrom.getValue(), routeTo.getValue()); highlighted = route.route();
        if (route.status() == RouteResult.Status.SIN_RUTA) { cost.setText("SIN_RUTA"); result.setText("No existe un trayecto dirigido entre las sedes seleccionadas."); }
        else { cost.setText(route.totalCost().orElseThrow() + "  ·  costo total"); result.setText(String.join(" → ", route.route())); }
        status.setText("Cálculo completado · " + route.status()); draw();
    }
    private void showHistory() {
        var text = new StringBuilder();
        for (var item : service.history()) {
            text.append(item.timestamp()).append("  ·  ").append(item.from()).append(" → ").append(item.to())
                .append("  ·  ").append(item.result().status());
            item.result().totalCost().ifPresent(value -> text.append("  ·  costo ").append(value));
            text.append("\n").append(String.join(" → ", item.result().route())).append("\n\n");
        }
        var area = new TextArea(text.isEmpty() ? "Todavía no hay cálculos guardados." : text.toString());
        area.setEditable(false); area.setWrapText(true); area.setPrefSize(650, 380);
        var dialog = new Alert(Alert.AlertType.INFORMATION); dialog.initOwner(stage); dialog.setTitle("Historial de rutas");
        dialog.setHeaderText("Resultados recuperados del archivo local · " + service.history().size() + " cálculos");
        dialog.getDialogPane().setContent(area); dialog.setResizable(true); dialog.showAndWait();
    }
    private FileChooser chooser(String title) {
        var chooser = new FileChooser(); chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Mapa de sedes (*.tsv)", "*.tsv")); return chooser;
    }
    private void importMap() throws Exception {
        var file = chooser("Importar mapa").showOpenDialog(stage); if (file == null) return;
        var alert = new Alert(Alert.AlertType.CONFIRMATION, "El mapa importado reemplazará el actual. Puedes exportar el actual antes de continuar.", ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(stage); alert.setHeaderText("Reemplazar mapa");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        service.importMap(file.toPath()); refresh(); status.setText("Mapa importado y guardado: " + file.getName());
    }
    private void exportMap() throws Exception {
        var chooser = chooser("Exportar mapa"); chooser.setInitialFileName("mapa.tsv");
        var file = chooser.showSaveDialog(stage); if (file == null) return;
        service.exportMap(file.toPath()); status.setText("Mapa exportado: " + file.getAbsolutePath());
    }
    private void draw() {
        graph.getChildren().clear(); if (service == null) return;
        var map = service.snapshot(); var nodes = new ArrayList<>(map.campuses());
        if (nodes.isEmpty()) {
            var empty = new Text(30, 70, "Tu mapa empieza con una sede."); empty.setFill(Color.web("#67778e")); graph.getChildren().add(empty); return;
        }
        double w = graph.getWidth(), h = graph.getHeight();
        double radius = Math.max(20, Math.min(w, h) / 2 - 56);
        var positions = new HashMap<String, Point2D>();
        for (int i=0; i<nodes.size(); i++) {
            double angle = -Math.PI/2 + 2*Math.PI*i/nodes.size();
            positions.put(nodes.get(i), new Point2D(w/2 + (nodes.size()==1 ? 0 : radius*Math.cos(angle)), h/2 + (nodes.size()==1 ? 0 : radius*Math.sin(angle))));
        }
        for (var c : map.connections()) {
            boolean active = false;
            for (int i=1; i<highlighted.size(); i++) if (highlighted.get(i-1).equals(c.from()) && highlighted.get(i).equals(c.to())) active = true;
            Color color = Color.web(active ? "#087f72" : "#899ab1");
            var a = positions.get(c.from()); var b = positions.get(c.to());
            if (a.equals(b)) {
                var loop = new CubicCurve(a.getX()-15,a.getY()-20,a.getX()-65,a.getY()-85,a.getX()+65,a.getY()-85,a.getX()+15,a.getY()-20);
                loop.setFill(Color.TRANSPARENT); loop.setStroke(color); loop.setStrokeWidth(active ? 3 : 1.8);
                var arrow = new Polygon(a.getX()+15,a.getY()-20,a.getX()+14,a.getY()-32,a.getX()+25,a.getY()-27); arrow.setFill(color);
                graph.getChildren().addAll(loop,arrow,costLabel(c.cost(),a.getX(),a.getY()-63)); continue;
            }
            var unit = b.subtract(a).normalize(); var offset = new Point2D(-unit.getY()*7,unit.getX()*7);
            var start = a.add(unit.multiply(26)).add(offset); var end = b.subtract(unit.multiply(29)).add(offset);
            var line = new Line(start.getX(),start.getY(),end.getX(),end.getY()); line.setStroke(color); line.setStrokeWidth(active ? 3 : 1.8);
            var left = end.subtract(unit.multiply(11)).add(-unit.getY()*5,unit.getX()*5);
            var right = end.subtract(unit.multiply(11)).add(unit.getY()*5,-unit.getX()*5);
            var arrow = new Polygon(end.getX(),end.getY(),left.getX(),left.getY(),right.getX(),right.getY()); arrow.setFill(color);
            var mid = start.midpoint(end).add(offset.multiply(1.6));
            graph.getChildren().addAll(line,arrow,costLabel(c.cost(),mid.getX(),mid.getY()));
        }
        for (String node : nodes) {
            var p = positions.get(node); var circle = new Circle(p.getX(),p.getY(),24);
            circle.setFill(Color.web(highlighted.contains(node) ? "#087f72" : "#243b5b"));
            var text = new Text(node.length()>7 ? node.substring(0,6)+"…" : node); text.setFill(Color.WHITE);
            text.setX(p.getX()-text.getLayoutBounds().getWidth()/2); text.setY(p.getY()+4);
            Tooltip.install(circle,new Tooltip(node)); Tooltip.install(text,new Tooltip(node)); graph.getChildren().addAll(circle,text);
        }
    }
    private Label costLabel(int value, double x, double y) {
        var label = new Label(Integer.toString(value)); label.getStyleClass().add("edge-cost"); label.relocate(x-12,y-13); return label;
    }
}
