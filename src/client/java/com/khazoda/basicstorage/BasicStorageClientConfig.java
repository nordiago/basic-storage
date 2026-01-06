package com.khazoda.basicstorage;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class BasicStorageClientConfig {
  private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("basicstorage-client.properties");
  public static final BasicStorageClientConfig INSTANCE = new BasicStorageClientConfig();
  private final Properties properties;

  private BasicStorageClientConfig() {
    this.properties = new Properties();
  }

  public void load() {
    if (Files.exists(CONFIG_PATH)) {
      try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
        properties.load(reader);
      } catch (IOException e) {
        // use defaults
      }
    }

    if (!properties.containsKey("show_crate_station_beams")) {
      properties.setProperty("show_crate_station_beams", "true");
      save();
    }
  }

  public void save() {
    try {
      Files.createDirectories(CONFIG_PATH.getParent());
      try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
        writer.write("# Basic Storage Client Configuration\n\n");
        writer.write("# If true, crate station beams will be rendered\n");
        writer.write("show_crate_station_beams=" + properties.getProperty("show_crate_station_beams", "true") + "\n");
      }
    } catch (IOException e) {
      // ignore save errors
    }
  }

  public boolean showCrateStationBeams() {
    return Boolean.parseBoolean(properties.getProperty("show_crate_station_beams", "true"));
  }

  public void setShowCrateStationBeams(boolean value) {
    properties.setProperty("show_crate_station_beams", String.valueOf(value));
  }
}
