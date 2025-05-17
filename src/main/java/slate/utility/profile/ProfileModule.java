package slate.utility.profile;

import slate.Main;
import slate.clickgui.ClickGui;
import slate.module.Module;
import slate.module.impl.client.Settings;
import slate.module.setting.impl.ButtonSetting;
import slate.utility.Utils;

public class ProfileModule extends Module {
    private final Profile profile;
    public boolean saved = true;

    public ProfileModule(Profile profile, String name, int bind) {
        super(name, category.profiles, bind);
        this.profile = profile;
        this.registerSetting(new ButtonSetting("Save profile", () -> {
            Utils.sendMessage("&7Saved profile: &b" + getName());
            Main.profileManager.saveProfile(this.profile);
            saved = true;
        }));
        this.registerSetting(new ButtonSetting("Remove profile", () -> {
            Utils.sendMessage("&7Removed profile: &b" + getName());
            Main.profileManager.deleteProfile(getName());
        }));
    }

    @Override
    public void toggle() {
        if (mc.currentScreen instanceof ClickGui || mc.currentScreen == null) {
            if (this.profile == Main.currentProfile) {
                return;
            }
            Main.profileManager.loadProfile(this.getName());

            Main.currentProfile = profile;

            if (Settings.sendMessage.isToggled()) {
                Utils.sendMessage("&7Enabled profile: &b" + this.getName());
            }
            saved = true;
        }
    }

    @Override
    public boolean isEnabled() {
        if (Main.currentProfile == null) {
            return false;
        }
        return Main.currentProfile.getModule() == this;
    }
}
