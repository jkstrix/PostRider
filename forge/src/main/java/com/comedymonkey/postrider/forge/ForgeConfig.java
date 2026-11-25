package com.comedymonkey.postrider.forge;

import java.util.HashMap;
import java.util.Map;

import com.comedymonkey.postrider.PostRiderConfig;
import com.comedymonkey.postrider.PostRiderConfig.Setting;
import com.comedymonkey.postrider.parity.ParityManifest;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The server config file on Forge, built from the settings PostRiderConfig describes exactly as NeoForgeConfig builds
 * it, with Forge's ForgeConfigSpec: the same file name, sections, keys, comments, defaults and ranges.
 */
final class ForgeConfig {
	static final ForgeConfigSpec SPEC;
	private static final Map<Setting, ForgeConfigSpec.ConfigValue<? extends Number>> VALUES = new HashMap<>();

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
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

	private ForgeConfig() {}
}
