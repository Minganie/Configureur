package com.hydrometeocharlevoix.configurer;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class Controller {
    private static final int NORMAL_FREQUENCY = 24*60;
    private static final int SURV_FREQUENCY = 6*60;
    private static final int ALERT_FREQUENCY = 30;

    @FXML
    private GridPane pane;
    @FXML
    private TextArea log;
    private final SimpleStringPropertyWithAppend logText = new SimpleStringPropertyWithAppend();

    private static void sendConfigEmail(Station station, int freq) {
        String body = "stop report\n" +
                "Location: \"\"\n" +
                "Sample Rate: 15 minutes\n" +
                "Report Rate: " + freq + " minutes\n" +
                "Mail Rate: " + freq + " minutes\n" +
                "Start Report: " + station.getNext();
        sendEmailUtil(station.getEmail(), body);
    }

    private static int getCurrentFrequency(int minutes) throws UnknownFrequencyException {
        switch (minutes) {
            case NORMAL_FREQUENCY:
                return NORMAL_FREQUENCY;
            case SURV_FREQUENCY:
                return SURV_FREQUENCY;
            case ALERT_FREQUENCY:
                return ALERT_FREQUENCY;
            default:
                throw new UnknownFrequencyException(String.valueOf(minutes));
        }
    }

    private static void sendEmailUtil(String to, String body) {
        try {
            Properties props = new Properties();
            props.setProperty("mail.smtp.ssl.enable", "true");
            props.setProperty("mail.smtp.host", "smtp.mail.yahoo.com");
            props.setProperty("mail.smtp.port", "465");
            Session session = Session.getInstance(props);

            Message message = new MimeMessage(session);
            message.addHeader("Content-type", "text/plain; charset=UTF-8");
            message.addHeader("format", "flowed");
            message.addHeader("Content-Transfer-Encoding", "8bit");
            message.setSentDate(new Date());

            message.setFrom(new InternetAddress(Private.getHomeStationEmail()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject("");
            message.setText(body);

            Transport.send(message, Private.getHomeStationEmail(), Private.getHomeStationPassword());
        }
        catch (Exception e) {
            Main.logger.error("Unexpected error while sending a configuration email: " + (e.getMessage() == null ? "NullPointerException?" : e.getMessage()));
        }
    }

    private static int newFrequency(ToggleGroup tg) {
        return (int) tg.getSelectedToggle().getUserData();
    }

    private static String formatFrequency(int minutes) {
        StringBuilder sb = new StringBuilder();
        if(minutes/(60*24)>1) {
            sb.append(minutes / (60 * 24)).append("j");
            minutes=minutes%(60*24);
        }
        if(minutes/60 > 0) {
            sb.append(minutes / 60).append("h");
            minutes=minutes%60;
        }
        if(minutes>0)
            sb.append(minutes).append("min");
        return sb.toString();
    }

    @FXML
    private void initialize() {
        logText.setValue("Choisissez la configuration désirée...");
        log.textProperty().bind(logText);
        try {
            Class.forName("org.postgresql.Driver");
            String connString = "jdbc:postgresql://" + Private.getDbHost() + "/" + Private.getDbName();
            try (Connection conn = DriverManager.getConnection(connString, Private.getDbUser(), Private.getDbPw())) {
                PreparedStatement stmt = conn.prepareStatement("with maxes as \n" +
                        "(\n" +
                        "    select station as stn, max(sent) as maxes \n" +
                        "    from emails\n" +
                        "    group by station\n" +
                        "),\n" +
                        "ems as \n" +
                        "(\n" +
                        "    select em.station, em.sent, em.report_rate\n" +
                        "    from emails as em\n" +
                        "        join maxes on em.station=maxes.stn and em.sent=maxes.maxes\n" +
                        ")\n" +
                        "select \n" +
                        "    gid, \n" +
                        "    name, \n" +
                        "    address, \n" +
                        "    sent at time zone 'America/Montreal' as last_report, \n" +
                        "    report_rate,\n" +
                        "    to_char((sent+interval '1 minute'*(report_rate+1)) at time zone 'America/Montreal', 'DD/MM/YYYY HH24:MI:SS') as next_avail_start\n" +
                        "from water_stations as ws\n" +
                        "    join ems on ws.gid=ems.station");
                ResultSet rs = stmt.executeQuery();
                int i = 2;

                // Add "header"
                pane.add(new Text("Toutes les stations"), 0, 1);
                ToggleGroup allToggleGroup = new ToggleGroup();
                RadioButton rbNormal = new RadioButton();
                RadioButton rbSurv = new RadioButton();
                RadioButton rbAlert = new RadioButton();
                rbNormal.setToggleGroup(allToggleGroup);
                rbSurv.setToggleGroup(allToggleGroup);
                rbAlert.setToggleGroup(allToggleGroup);
                pane.add(rbNormal, 1, 1);
                pane.add(rbSurv, 2, 1);
                pane.add(rbAlert, 3, 1);
                pane.add(new Text("Normal (" + formatFrequency(NORMAL_FREQUENCY) + ")"), 1, 0);
                pane.add(new Text("Surveillance (" + formatFrequency(SURV_FREQUENCY) + ")"), 2, 0);
                pane.add(new Text("Alerte (" + formatFrequency(ALERT_FREQUENCY) + ")"), 3, 0);

                // Add individual stations
                final List<RadioButton> stationNorm = new ArrayList<>();
                final List<RadioButton> stationSurv = new ArrayList<>();
                final List<RadioButton> stationAlert = new ArrayList<>();
                final List<Station> stations = new ArrayList<>();
                final List<ToggleGroup> toggleGroups = new ArrayList<>();
                while (rs.next()) {
                    Station station = new Station(rs);
                    stations.add(station);
                    ToggleGroup stnToggleGroup = new ToggleGroup();
                    toggleGroups.add(stnToggleGroup);
                    RadioButton normalBtn = new RadioButton();
                    RadioButton survBtn = new RadioButton();
                    RadioButton alertBtn = new RadioButton();
                    int curFreq = NORMAL_FREQUENCY;
                    try {
                        curFreq = getCurrentFrequency(station.getRate());
                    } catch (UnknownFrequencyException e) {
                        logText.append("La station #" + station.getSerial() + " a une fréquence d'envoi de données non standard (" + station.getRate() + " minutes); retour à la valeur par défaut de " + formatFrequency(NORMAL_FREQUENCY) + ".");
                    }
                    normalBtn.setToggleGroup(stnToggleGroup);
                    normalBtn.setUserData(NORMAL_FREQUENCY);
                    normalBtn.setSelected(curFreq==NORMAL_FREQUENCY);
                    survBtn.setToggleGroup(stnToggleGroup);
                    survBtn.setUserData(SURV_FREQUENCY);
                    survBtn.setSelected(curFreq==SURV_FREQUENCY);
                    alertBtn.setToggleGroup(stnToggleGroup);
                    alertBtn.setUserData(ALERT_FREQUENCY);
                    alertBtn.setSelected(curFreq==ALERT_FREQUENCY);
                    stationNorm.add(normalBtn);
                    stationSurv.add(survBtn);
                    stationAlert.add(alertBtn);
                    pane.add(new Text(station.shortToString()), 0, i);
                    pane.add(normalBtn, 1, i);
                    pane.add(survBtn, 2, i);
                    pane.add(alertBtn, 3, i);
                    ++i;
                }
                rbNormal.setOnAction(event -> {
                    for(RadioButton rb : stationNorm) {
                        rb.setSelected(true);
                    }
                });
                rbSurv.setOnAction(event -> {
                    for(RadioButton rb : stationSurv) {
                        rb.setSelected(true);
                    }
                });
                rbAlert.setOnAction(event -> {
                    for(RadioButton rb : stationAlert) {
                        rb.setSelected(true);
                    }
                });
                rbNormal.setSelected(true);
                Button done = new Button("Configurer");
                pane.add(done, 3, i);
                done.setOnAction(event -> new Thread(() -> {
                    boolean error = false;
                    for(int j = 0; j < stations.size(); j++) {
                        Station stn = stations.get(j);
                        ToggleGroup tg = toggleGroups.get(j);
                        String email = stn.getEmail();
                        int newFreq = newFrequency(tg);
                        if(newFreq == stn.getRate())
                            logText.append("Pas de changement pour " + stn.getName());
                        else {
                            logText.append("Changement pour " + stn.getName() + " ^ ");
                            sendConfigEmail(stn, newFreq);
                            logText.append("\tEnvoi d'un courriel de configuration à " + email + ": fréquence aux " + formatFrequency(newFreq) + ", applicable dès " + stn.getNext());
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e) {
                                error = true;
                                Main.logger.error("5s delay between emails interrupted? " + e.getMessage());
                                logText.setValue("Erreur étrange lors de l'envoi des courriels... \r\nEnvoyez votre journal (timakan.configurer.log) au programmeur!");
                            }
                        }
                    }
                    if(!error)
                        logText.append("Tous les courriels de configuration requis ont été envoyés.");
                    logText.append("Fermez et rouvrez l'application pour faire de nouveaux changements.");
                    pane.setDisable(true);
                }).start());
            } catch (Exception e) {
                Main.logger.error("Problem while trying to connect to database: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (ClassNotFoundException e) {
            Main.logger.error("Build error? Class Not Found :" + e.getMessage());
            e.printStackTrace();
        }
    }
}
