package rules.ui;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Shadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import rules.RuleTypes;
import schema.ui.SchemaNode;
import source.ui.items.SourceTreeDirectory;
import source.ui.items.SourceTreeFile;
import source.ui.items.SourceTreeItem;
import utils.Utils;

/**
 * Created by adrapereira on 28-09-2015.
 */
public class RuleModalPane extends BorderPane {
    private SchemaNode schema;
    private Set<SourceTreeItem> sourceSet;

    private GridPane gridAssociation;
    private ToggleGroup groupAssoc;
    private ComboBox<Integer> level;

    private GridPane gridMetadata;
    private ToggleGroup groupMetadata;

    private Button btContinue, btCancel;

    public RuleModalPane(Set<SourceTreeItem> sourceSet, SchemaNode schemaNode){
        super();
        schema = schemaNode;
        this.sourceSet = sourceSet;
        setStyle("-fx-border-color: lightgray; -fx-border-width: 2px; -fx-background-color: white;");

        createTop();
        createCenter();
        createBottom();
    }

    private void createTop(){
        StackPane pane = new StackPane();
        pane.setPadding(new Insets(0, 0, 10, 0));

        HBox hbox = new HBox();
        HBox.setHgrow(hbox, Priority.ALWAYS);
        hbox.setStyle("-fx-background-color: lightgray;");
        hbox.setPadding(new Insets(5, 5, 5, 5));
        pane.getChildren().add(hbox);

        Label source = new Label();
        StringBuilder sb = new StringBuilder();
        for(SourceTreeItem it: sourceSet) {
            if(it instanceof SourceTreeDirectory)
                sb.append(((SourceTreeDirectory) it).getValue()).append(",");
            else if(it instanceof SourceTreeFile)
                sb.append(((SourceTreeFile)it).getValue()).append(",");
        }
        //remove the last comma
        int lastComma = sb.lastIndexOf(",");
        sb.replace(lastComma, lastComma + 1,"");
        source.setText(sb.toString());

        source.setMinHeight(24);
        source.setFont(new Font("System", 14));
        source.setGraphic(new ImageView(SourceTreeDirectory.folderCollapseImage));
        source.setStyle(" -fx-text-fill: black");

        Label descObj = new Label(schema.getDob().getTitle());
        descObj.setMinHeight(24);
        descObj.setFont(new Font("System", 14));
        descObj.setGraphic(new ImageView(schema.getImage()));
        descObj.setTextAlignment(TextAlignment.LEFT);
        descObj.setStyle(" -fx-text-fill: black");

        HBox space = new HBox();
        HBox.setHgrow(space, Priority.ALWAYS);

        hbox.getChildren().addAll(source, space, descObj);

        setTop(pane);
    }

    private void createCenter(){
        gridAssociation = createCenterAssociation();
        gridMetadata = createCenterMetadata();

        setCenter(gridAssociation);
    }

    private GridPane createCenterAssociation(){
        GridPane gridCenter = new GridPane();
        gridCenter.setPadding(new Insets(0, 10, 0, 10));
        gridCenter.setVgap(5);
        gridCenter.setHgap(10);

        groupAssoc = new ToggleGroup();

        RadioButton singleSip = new RadioButton("Create a single SIP");
        singleSip.setToggleGroup(groupAssoc);
        singleSip.setUserData(RuleTypes.SINGLESIP);
        singleSip.setSelected(true);
        singleSip.setStyle(" -fx-text-fill: black");

        RadioButton perFile = new RadioButton("Create one SIP per file");
        perFile.setToggleGroup(groupAssoc);
        perFile.setUserData(RuleTypes.SIPPERFILE);
        perFile.setStyle(" -fx-text-fill: black");

        RadioButton byFolder = new RadioButton("Create one SIP per folder until level");
        byFolder.setUserData(RuleTypes.SIPPERFOLDER);
        byFolder.setToggleGroup(groupAssoc);
        byFolder.setStyle(" -fx-text-fill: black");

        int depth = 0;
        for(SourceTreeItem std: sourceSet) {
            Path startPath = Paths.get(std.getPath());
            int depthAux = Utils.getRelativeMaxDepth(startPath);
            if(depthAux > depth) depth = depthAux;
        }

        ArrayList<Integer> levels = new ArrayList<>();
        for (int i = 1; i <= depth; i++)
            levels.add(i);

        ObservableList<Integer> options = FXCollections.observableArrayList(levels);
        level = new ComboBox<>(options);
        level.setValue((int)Math.ceil(depth/2.0));

        gridCenter.add(singleSip, 0, 1);
        gridCenter.add(perFile, 0, 2);
        gridCenter.add(byFolder, 0, 3);
        gridCenter.add(level, 1, 3);

        return gridCenter;
    }

    private GridPane createCenterMetadata(){
        GridPane gridCenter = new GridPane();
        gridCenter.setPadding(new Insets(0, 10, 0, 10));
        gridCenter.setVgap(5);
        gridCenter.setHgap(10);

        groupMetadata = new ToggleGroup();

        RadioButton singleFile = new RadioButton("A single file");
        singleFile.setToggleGroup(groupMetadata);
        singleFile.setUserData(RuleTypes.SINGLESIP);
        singleFile.setSelected(true);
        singleFile.setStyle(" -fx-text-fill: black");

        //fill list with the directories in the source set
        List<String> directories = new ArrayList<>();
        for(SourceTreeItem sti: sourceSet){
            if(sti instanceof SourceTreeDirectory)
                directories.add(sti.getPath());
            else{ //if the item isn't a directory, get its parent
                Path path = Paths.get(sti.getPath());
                directories.add(path.getParent().toString());
            }
        }
        //get the common prefix of the directories
        String commonPrefix = Utils.longestCommonPrefix(directories);

        RadioButton sameFolder = new RadioButton("From the directory \"" + commonPrefix + "\"");
        sameFolder.setToggleGroup(groupMetadata);
        sameFolder.setUserData(RuleTypes.SIPPERFILE);
        sameFolder.setStyle(" -fx-text-fill: black");

        RadioButton diffFolder = new RadioButton("From another directory");
        diffFolder.setUserData(RuleTypes.SIPPERFOLDER);
        diffFolder.setToggleGroup(groupMetadata);
        diffFolder.setStyle(" -fx-text-fill: black");

        Label metaTitle = new Label("Apply metadata from:");
        metaTitle.setFont(Font.font("System", FontWeight.BOLD, 13));

        gridCenter.add(metaTitle, 0, 1);
        gridCenter.add(singleFile, 0, 2);
        gridCenter.add(sameFolder, 0, 3);
        gridCenter.add(diffFolder, 0, 4);

        return gridCenter;
    }

    private void createBottom(){
        btCancel = new Button("Cancel");
        btContinue = new Button("Continue");
        Label lState = new Label("");
        VBox.setVgrow(lState, Priority.ALWAYS);
        lState.setAlignment(Pos.BOTTOM_CENTER);
        lState.setStyle(" -fx-text-fill: darkgrey");

        HBox space = new HBox();
        HBox.setHgrow(space, Priority.ALWAYS);

        HBox buttons = new HBox();
        buttons.setPadding(new Insets(10, 10, 10, 10));
        buttons.setSpacing(10);
        buttons.setAlignment(Pos.CENTER);
        buttons.getChildren().addAll(btCancel, space, lState, btContinue);


        btContinue.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                //RuleModalController.associationContinue();
                setCenter(gridMetadata);
            }
        });

        btCancel.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                RuleModalController.cancel();
            }
        });

        setBottom(buttons);
    }
}
