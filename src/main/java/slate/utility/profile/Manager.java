package slate.utility.profile;

import slate.Main;
import slate.module.Module;
import slate.module.setting.impl.ButtonSetting;
import slate.utility.Utils;

import java.awt.*;
import java.io.IOException;

public class Manager extends Module {
    private ButtonSetting loadProfiles, openFolder, createProfile;

    public Manager() {
        super("Manager", category.profiles);
        this.registerSetting(createProfile = new ButtonSetting("Create profile", () -> {
            if (Utils.nullCheckPasses() && Main.profileManager != null) {
                String name = "profile-";
                for (int i = 1; i <= 100; i++) {
                    if (Main.profileManager.getProfile(name + i) != null) {
                        continue;
                    }
                    name += i;
                    Main.profileManager.saveProfile(new Profile(name, 0));
                    Utils.sendMessage("&7Created profile: &b" + name);
                    Main.profileManager.loadProfiles();
                    break;
                }
            }
        }));
        this.registerSetting(loadProfiles = new ButtonSetting("Load profiles", () -> {
            if (Utils.nullCheckPasses() && Main.profileManager != null) {
                Main.profileManager.loadProfiles();
            }
        }));
        this.registerSetting(openFolder = new ButtonSetting("Open folder", () -> {
            try {
                Desktop.getDesktop().open(Main.profileManager.directory);
            }
            catch (IOException ex) {
                Main.profileManager.directory.mkdirs();
                Utils.sendMessage("&cError locating folder, recreated.");
            }
        }));
        ignoreOnSave = true;
        canBeEnabled = false;
    }
}