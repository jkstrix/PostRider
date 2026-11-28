package com.comedymonkey.postrider.neoforge;

import java.util.HashMap;
import java.util.Map;

import com.comedymonkey.postrider.PostRiderConfig;
import com.comedymonkey.postrider.PostRiderConfig.Setting;
import com.comedymonkey.postrider.parity.ParityManifest;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The server config file on NeoForge, built from the settings PostRiderConfig describes. */
final class NeoForgeConfig {
	static final ModConfigSpec SPEC;
	private static final Map<Setting, ModConfigSpec.ConfigValue<? extends Number>> VALUES = new HashMap<>();

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		String section = null;
		for (Setting setting : PostRiderConfig.ALL) {
			if (!setting.section().equals(section)) {
				if (section != null)
					builder.pop();
				builder.push(setting.section());
				section = setting.section();
			}
			builder.comment(setting.comment().toArray(String[]::new));
			ParityManifest.recordConfig(setting.section() + "." + setting.key(), setting.defaultValue(), setting.min(), setting.max());
			VALUES.put(setting, setting.isWhole()
				? builder.defineInRange(setting.key(), setting.defaultValue().intValue(), setting.min().intValue(), setting.max().intValue())
				: builder.defineInRange(setting.key(), setting.defaultValue().doubleValue(), setting.min().doubleValue(), setting.max().doubleValue()));
		}
		builder.pop();
		SPEC = builder.build();
		PostRiderConfig.setValues(new PostRiderConfig.Values() {
			@Override
			public boolean isLoaded() {
				return SPEC.isLoaded();
			}

			@Override
			public Number get(Setting setting) {
				return VALUES.get(setting).get();
			}
		});
	}

	private NeoForgeConfig() {}
}
