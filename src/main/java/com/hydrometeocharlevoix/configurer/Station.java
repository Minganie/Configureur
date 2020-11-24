package com.hydrometeocharlevoix.configurer;

import java.sql.ResultSet;
import java.sql.SQLException;

public class Station {
    private int serial;
    private String name;
    private String email;
    private int rate;
    private String next;
    /*serial, name, address, last_report, report_rate, next_avail_start*/

    public Station(ResultSet rs) throws SQLException {
        serial = rs.getInt("gid");
        name = rs.getString("name");
        email = rs.getString("address");
        rate = rs.getInt("report_rate");
        next = rs.getString("next_avail_start");
    }

    public int getSerial() {
        return serial;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public int getRate() {
        return rate;
    }

    public String getNext() {
        return next;
    }

    public String shortToString() {
        return "Station #" + serial + " " + name;
    }

    @Override
    public String toString() {
        return "Station #" + serial + " " + name + " @ " + email;
    }
}
