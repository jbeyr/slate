package slate.module;

import slate.module.impl.client.*;
import slate.module.impl.combat.AimAssist;
import slate.module.impl.player.AutoWeapon;
import slate.module.impl.render.*;
import org.jetbrains.annotations.NotNull;
import slate.module.impl.world.targeting.AntiBot;
import slate.module.impl.world.targeting.TargetManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ModuleManager {
    public static List<Module> organizedModules = new ArrayList<>();
    public static AntiBot antiBot;
    public static HUD hud;
    public static Notifications notifications;
    public static ClientTheme clientTheme;
    public static Watermark watermark;
    public static AimAssist aimAssist;
    public static AutoWeapon autoWeapon;

    public static TargetManager targetManager;

    static List<Module> modules = new ArrayList<>();

    private static double getWidth(@NotNull Module module) {
        String text = module.getPrettyName()
                + ((HUD.showInfo.isToggled() && !module.getPrettyInfo().isEmpty()) ? " " + module.getPrettyInfo() : "");
        return HUD.getFontRenderer().width(HUD.lowercase.isToggled() ? text.toLowerCase() : text);
    }

    public static void sort() {
        if (HUD.alphabeticalSort.isToggled()) {
            organizedModules.sort(Comparator.comparing(Module::getPrettyName));
        } else {
            organizedModules.sort((c1, c2) -> Double.compare(getWidth(c2), getWidth(c1)));
        }
    }

    public void register() {

        // client
        this.addModule(new Gui());
        this.addModule(new Settings());
        this.addModule(notifications = new Notifications());
        this.addModule(hud = new HUD());

        // render
        this.addModule(watermark = new Watermark());
        this.addModule(clientTheme = new ClientTheme());

        // world
        this.addModule(antiBot = new AntiBot());
        this.addModule(targetManager = new TargetManager());

        // player
        this.addModule(autoWeapon = new AutoWeapon());

        // combat
        this.addModule(aimAssist = new AimAssist());

        // enable
        antiBot.enable();
        targetManager.enable();
        notifications.enable();
        clientTheme.enable();
        modules.sort(Comparator.comparing(Module::getPrettyName));
    }

    public void addModule(Module m) {
        modules.add(m);
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> inCategory(Module.category category) {
        ArrayList<Module> categoryML = new ArrayList<>();

        for (Module mod : this.getModules()) {
            if (mod.moduleCategory().equals(category)) {
                categoryML.add(mod);
            }
        }

        return categoryML;
    }

    public Module getModule(String moduleName) {
        for (Module module : modules) {
            if (module.getName().equals(moduleName)) {
                return module;
            }
        }
        return null;
    }
}