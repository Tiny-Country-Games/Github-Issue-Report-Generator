package com.tcg.githubreport;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.format.CellFormatType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;

public class Main extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Github Report Generator");

        TextField orgTextField = new TextField();

        TextField apiKeyTextField = new TextField();

        Button button = new Button("Generate Report");
        button.setOnAction(event -> {
            button.setDisable(true);
            if(orgTextField.getText().length() > 0) {
                if(apiKeyTextField.getText().length() > 0) {
                    final Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.getDialogPane().lookupButton(ButtonType.OK).setDisable(true);
                    alert.setHeaderText(null);
                    alert.setTitle("Generating Report");
                    alert.setContentText("Please Wait Until Report Is Complete");
                    Thread thread = new Thread(() -> {
                        try {
                            Workbook workbook = new XSSFWorkbook();
                            HttpClient client = HttpClients.createDefault();
                            HttpGet getRepos = new HttpGet(String.format("https://api.github.com/orgs/%s/repos?access_token=%s", orgTextField.getText(), apiKeyTextField.getText()));
                            HttpResponse repoResponse = client.execute(getRepos);
                            JSONArray reposArray = new JSONArray(httpEntityBody(repoResponse.getEntity()));
                            for (int i = 0; i < reposArray.length(); i++) {
                                JSONObject repoObject = reposArray.getJSONObject(i);
                                HttpGet getIssues = new HttpGet(String.format("%s?access_token=%s", repoObject.getString("issues_url").replace("{/number}", ""), apiKeyTextField.getText()));
                                HttpResponse issuesResponse = client.execute(getIssues);
                                JSONArray issuesArray = new JSONArray(httpEntityBody(issuesResponse.getEntity()));
                                if(issuesArray.length() > 0) {
                                    Sheet sheet = workbook.createSheet(repoObject.getString("name"));
                                    Row header = sheet.createRow(0);
                                    header.createCell(0).setCellValue("Issue Number");
                                    header.createCell(1).setCellValue("Issue Name");
                                    header.createCell(2).setCellValue("Assignees");
                                    header.createCell(3).setCellValue("Milestone");

                                    for (int j = 0; j < issuesArray.length(); j++) {
                                        JSONObject issueObject = issuesArray.getJSONObject(j);
                                        Row issueRow = sheet.createRow(1 + j);
                                        issueRow.createCell(0).setCellValue("#" + issueObject.getInt("number"));
                                        issueRow.createCell(1).setCellValue(issueObject.getString("title"));
                                        JSONArray assignees = issueObject.getJSONArray("assignees");
                                        if(assignees.length() > 0) {
                                            StringBuilder stringBuilder = new StringBuilder();
                                            for (int k = 0; k < assignees.length(); k++) {
                                                JSONObject assignee = assignees.getJSONObject(k);
                                                if(k != 0) stringBuilder.append(", \t");
                                                stringBuilder.append(assignee.getString("login"));
                                            }
                                            issueRow.createCell(2).setCellValue(stringBuilder.toString());
                                        } else {
                                            issueRow.createCell(2).setCellValue("Unassigned");
                                        }
                                        if(!issueObject.get("milestone").equals(JSONObject.NULL)) {
                                            JSONObject milestone = issueObject.getJSONObject("milestone");
                                            issueRow.createCell(3).setCellValue(milestone.getString("title"));
                                        } else {
                                            issueRow.createCell(3).setCellValue("None");
                                        }
                                    }
                                    for (int j = 0; j < sheet.getRow(0).getPhysicalNumberOfCells(); j++) {
                                        sheet.autoSizeColumn(j);
                                    }
                                }
                            }
                            Platform.runLater(() -> {
                                alert.close();
                                FileChooser fileChooser = new FileChooser();
                                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
                                try(FileOutputStream out = new FileOutputStream(fileChooser.showSaveDialog(primaryStage))) {
                                    workbook.write(out);
                                    out.flush();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    alert.close();
                                    button.setDisable(false);
                                    new ExceptionDialog(e, primaryStage).showAndWait();
                                }
                            });
                        } catch (Exception e) {
                            Platform.runLater(() -> {
                                e.printStackTrace();
                                alert.close();
                                button.setDisable(false);
                                new ExceptionDialog(e, primaryStage).showAndWait();
                            });
                        }
                        Platform.runLater(() -> {
                            alert.close();
                            button.setDisable(false);
                        });
                    });
                    thread.start();
                    alert.showAndWait();
                } else {
                    new ExceptionDialog(new Exception("Enter an API key"), primaryStage).showAndWait();
                }
            } else {
                new ExceptionDialog(new Exception("Enter a Github organization"), primaryStage).showAndWait();
            }
        });

        VBox vBox = new VBox(5, new Label("Organization"), orgTextField, new Label("API Key"), apiKeyTextField, button);
        vBox.setPadding(new Insets(10));

        Scene scene = new Scene(vBox);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static String httpEntityBody(HttpEntity entity) throws IOException {
        return Main.httpEntityBody(entity, "UTF-8");
    }

    public static String httpEntityBody(HttpEntity entity, String encoding) throws IOException {
        String body = "";
        if(entity != null) {
            InputStream inputStream = entity.getContent();
            try {
                StringWriter writer = new StringWriter();
                IOUtils.copy(inputStream, writer, encoding);
                body = writer.toString();
            } finally {
                inputStream.close();
            }
        }
        return body;
    }

}
