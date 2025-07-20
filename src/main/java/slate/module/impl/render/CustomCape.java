package slate.module.impl.render;

import slate.Main;
import slate.module.Module;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.ModeSetting;
import slate.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.mojang.realmsclient.gui.ChatFormatting.*;

public final class CustomCape extends Module {
    public static final List<ResourceLocation> LOADED_CAPES = new ArrayList<>();
    public static String[] CAPES_NAME = new String[]{
            "n/a", "die", "ravenanime"
    };
    public static final ModeSetting cape = new ModeSetting("Cape", CAPES_NAME, 0);
    private static File directory;

    public CustomCape() {
        super("CustomCape", category.render);
        this.registerSetting(new ButtonSetting("Load capes", CustomCape::loadCapes));
        this.registerSetting(cape);

        directory = new File(mc.mcDataDir + File.separator + Main.MODID, "customCapes");
        if (!directory.exists()) {
            boolean success = directory.mkdirs();
            if (!success) {
                System.out.println("There was an issue creating customCapes directory.");
            }
        }

        this.registerSetting(new ButtonSetting("Open folder", () -> {
            try {
                Desktop.getDesktop().open(directory);
            } catch (IOException ex) {
                Main.profileManager.directory.mkdirs();
                Utils.sendMessage("&cError locating folder, recreated.");
            }
        }));

        loadCapes();
    }

    public static void loadCapes() {
        final File[] files;
        try { // collect custom PNGs
            files = Objects.requireNonNull(directory.listFiles());
        } catch (NullPointerException e) {
            Utils.sendMessage(RED + "Fail to load.");
            return;
        }

        /* 1) Built-in capes supplied inside the mod jar */
        final String[] builtinCapes = {
                "die", "ravenanime"
        };

        // +1 slot for the “n/a” option that means “no custom cape”
        CAPES_NAME = new String[builtinCapes.length + files.length + 1];
        CAPES_NAME[0] = "n/a"; // keep placeholder
        LOADED_CAPES.clear();
        LOADED_CAPES.add(null); // matching dummy entry

        // load built-ins
        for (int i = 0; i < builtinCapes.length; i++) {
            String id   = builtinCapes[i];
            String path = id.toLowerCase();

            try (InputStream stream = // try lower-case
                         Main.class.getResourceAsStream(
                                 "/assets/slate/textures/capes/" + path + ".png"
                         )
            ) {

                InputStream s = stream != null ? stream // fall-back to case
                        : Main.class.getResourceAsStream(
                        "/assets/slate/textures/capes/" + id + ".png");

                if (s == null) {
                    Utils.sendMessage(RED + "Failed to load cape '" + id + "'");
                    continue;
                }

                BufferedImage img = ImageIO.read(s);
                ResourceLocation rl =
                        Minecraft.getMinecraft().renderEngine
                                .getDynamicTextureLocation(id,
                                        new DynamicTexture(img));

                CAPES_NAME[i + 1] = id; // +1 because of “n/a”
                LOADED_CAPES.add(rl);

            } catch (Exception ex) {
                Utils.sendMessage(RED + "Failed to load cape '" + id + "'");
            }
        }

        // load user files
        int customOffset = builtinCapes.length + 1; // jump past “n/a” + built-ins
        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".png")) continue;

            String name = file.getName().substring(0, file.getName().length() - 4);

            try {
                BufferedImage img = ImageIO.read(file);
                ResourceLocation rl =
                        Minecraft.getMinecraft().renderEngine
                                .getDynamicTextureLocation(name,
                                        new DynamicTexture(img));

                CAPES_NAME[customOffset] = name;
                LOADED_CAPES.add(rl);
                customOffset++;

            } catch (IOException ex) {
                Utils.sendMessage(RED + "Failed to load cape '" + name + "'");
            }
        }

        cape.setOptions(CAPES_NAME); // refresh the ModeSetting
        Utils.sendMessage(GREEN + "Loaded "
                + RESET + (CAPES_NAME.length - 1) + GREEN + " capes.");
    }
}
