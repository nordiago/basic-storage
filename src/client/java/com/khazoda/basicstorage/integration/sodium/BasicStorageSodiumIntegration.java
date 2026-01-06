package com.khazoda.basicstorage.integration.sodium;

import com.khazoda.basicstorage.BasicStorageClientConfig;
import com.khazoda.basicstorage.Constants;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.network.chat.Component;

public class BasicStorageSodiumIntegration implements ConfigEntryPoint {
  @Override
  public void registerConfigLate(ConfigBuilder builder) {
    var modOptions = builder.registerOwnModOptions()
        .setName("Basic Storage");

    modOptions.addPage(builder.createOptionPage()
        .setName(Component.translatable("basicstorage.options.performance.title"))
        .addOption(builder.createBooleanOption(Constants.ID("show_crate_station_beams"))
            .setName(Component.translatable("basicstorage.options.performance.show_crate_station_beams"))
            .setTooltip(Component.translatable("basicstorage.options.performance.show_crate_station_beams.tooltip"))
            .setImpact(OptionImpact.MEDIUM)
            .setBinding(new OptionBinding<>() {
              @Override
              public void save(Boolean value) {
                BasicStorageClientConfig.INSTANCE.setShowCrateStationBeams(value);
                BasicStorageClientConfig.INSTANCE.save();
              }

              @Override
              public Boolean load() {
                return BasicStorageClientConfig.INSTANCE.showCrateStationBeams();
              }
            })
            .setDefaultValue(true)
            .setStorageHandler(BasicStorageClientConfig.INSTANCE::save)));
  }
}
