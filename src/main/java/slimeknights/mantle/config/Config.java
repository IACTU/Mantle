package slimeknights.mantle.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

/**
 * Base class for all Mantle specific config options
 */
public class Config {
	/** If true, enables the heart renderer */
	public static final ModConfigSpec.BooleanValue EXTRA_HEART_RENDERER;

	/** List of preferences for tag outputs */
	private static final List<String> DEFAULT_TAG_PREFERENCES = Arrays.asList("minecraft", "tconstruct", "tmechworks", "create", "immersiveengineering", "mekanism", "thermal");
	public static final ModConfigSpec.ConfigValue<List<? extends String>> TAG_PREFERENCES;

	public static final ModConfigSpec CLIENT_SPEC, SERVER_SPEC;

	static {
    ModConfigSpec.Builder client = new ModConfigSpec.Builder();
    ModConfigSpec.Builder server = new ModConfigSpec.Builder();

		// client options
		EXTRA_HEART_RENDERER = client
      .comment(
        "If true, enables the Mantle heart renderer, which stacks hearts by changing the color instead of vertically stacking them.",
        "Mod authors: this config is not meant for compatibility with your heart renderer, cancel the RenderGameOverlayEvent.Pre event and our logic won't run")
      .translation("config.mantle.extraHeartRenderer")
      .define("extraHeartRenderer", true);

		// server options
		TAG_PREFERENCES = server.comment("Preferences for outputs from tags used in automatic compat in recipes")
                            .translation("config.mantle.tagPreferences")
                            .defineList("tagPreferences", DEFAULT_TAG_PREFERENCES, str -> true);

		CLIENT_SPEC = client.build();
		SERVER_SPEC = server.build();
	}
}
