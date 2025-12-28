package com.khazoda.basicstorage;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {

  public static Identifier ID(String name) {
    return Identifier.fromNamespaceAndPath(NAMESPACE, name);
  }

  public static final String NAMESPACE = "basicstorage";
  public static final String NAME = "Basic Storage";
  public static final Logger LOG = LoggerFactory.getLogger(NAME);
  public static final int CRATE_MAX_COUNT = 1000000000; // 1 Billion
}
