package com.khazoda.basicstorage;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static com.khazoda.basicstorage.Constants.LOG;

public class BasicStorageConfig {

  private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("basicstorage.properties");
  private static BasicStorageConfig INSTANCE;
  private final Properties properties;

  private BasicStorageConfig() {
    this.properties = new Properties();
  }

  public static BasicStorageConfig getInstance() {
    if (INSTANCE == null) {
      INSTANCE = new BasicStorageConfig();
    }
    return INSTANCE;
  }

  public void load() {
    if (Files.exists(CONFIG_PATH)) {
      try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
        properties.load(reader);
      } catch (IOException e) {
        LOG.error("[Basic Storage] Failed to load config: {}", e.getMessage());
      }
    }

    boolean modified = false;

    if (!properties.containsKey("break_with_axe_only")) {
      properties.setProperty("break_with_axe_only", "false");
      modified = true;
    }

    if (!properties.containsKey("can_break_if_full")) {
      properties.setProperty("can_break_if_full", "true");
      modified = true;
    }

    if (modified || !Files.exists(CONFIG_PATH)) {
      save();
    }
  }

  public void save() {
    try {
      Files.createDirectories(CONFIG_PATH.getParent());
      try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
        writer.write("# Basic Storage Configuration\n\n");

        writer.write("# If true, crates can only be broken using an axe.\n");
        writer.write("# If false, crates can be broken with anything.\n");
        writer.write("break_with_axe_only=" + properties.getProperty("break_with_axe_only") + "\n\n");

        writer.write("# If true, crates containing items can be broken, picked up and moved\n");
        writer.write("# If false, crates containing items can not be broken \n");
        writer.write("can_break_if_full=" + properties.getProperty("can_break_if_full") + "\n");
      }
    } catch (IOException e) {
      LOG.error("[Basic Storage] Failed to save config: {}", e.getMessage());
    }
  }

  public boolean breakWithAxeOnly() {
    return Boolean.parseBoolean(properties.getProperty("break_with_axe_only", "false"));
  }

  public boolean canBreakIfFull() {
    return Boolean.parseBoolean(properties.getProperty("can_break_if_full", "true"));
  }

  public void setBreakWithAxeOnly(boolean value) {
    properties.setProperty("break_with_axe_only", String.valueOf(value));
  }

  public void setCanBreakIfFull(boolean value) {
    properties.setProperty("can_break_if_full", String.valueOf(value));
  }
}
