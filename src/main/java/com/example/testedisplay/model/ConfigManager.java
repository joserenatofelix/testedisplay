package com.example.testedisplay.model;

import java.io.*;
import java.util.Properties;

public class ConfigManager {
    private final Properties props = new Properties();
    private final File file = new File("config.properties");

    public ConfigManager() {
        load();
    }

    public void load() {
        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void save() {
        try (OutputStream out = new FileOutputStream(file)) {
            props.store(out, "Configurações do testedisplay");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public void set(String key, String value) {
        props.setProperty(key, value);
    }
}
