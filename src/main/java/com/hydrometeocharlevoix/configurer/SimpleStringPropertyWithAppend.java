package com.hydrometeocharlevoix.configurer;

import javafx.beans.property.SimpleStringProperty;

public class SimpleStringPropertyWithAppend extends SimpleStringProperty {
    public void append(String newText) {
        setValue(newText + "\r\n" + getValue());
    }
}
