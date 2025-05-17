package slate.module;

import slate.module.impl.client.*;
import slate.module.impl.combat.AimAssist;
import slate.module.impl.combat.AutoClicker;
import slate.module.impl.combat.Backtrack;
import slate.module.impl.combat.BlockHit;
import slate.module.impl.movement.Sprint;
import slate.module.impl.other.ModSpoofer;
import slate.module.impl.other.PingSpoofer;
import slate.module.impl.player.AutoTool;
import slate.module.impl.player.AutoWeapon;
import slate.module.impl.render.*;
import org.jetbrains.annotations.NotNull;
import slate.module.impl.world.DelayRemover;
import slate.module.impl.world.targeting.AntiBot;
import slate.module.impl.world.targeting.TargetManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ModuleManager {
    public static List<Module> organizedModules = new ArrayList<>();

    public static Notifications notifications;
    public static HUD hud;

    public static ClientTheme clientTheme;
    public static Watermark watermark;
    public static Tracers tracers;
    public static Fullbright fullbright;
    public static BedESP bedEsp;
    public static SharkESP sharkESP;
    public static InvisESP invisESP;

    public static AntiBot antiBot;
    public static TargetManager targetManager;
    public static DelayRemover delayRemover;
    public static AutoWeapon autoWeapon;
    public static AutoTool autoTool;

    public static AutoClicker autoClicker;
    public static AimAssist aimAssist;
    public static BlockHit blockHit;
    public static Backtrack backtrack;

    public static Sprint sprint;

    public static ModSpoofer modSpoofer;
    public static PingSpoofer pingSpoofer;


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
        this.addModule(tracers = new Tracers());
        this.addModule(fullbright = new Fullbright());
        this.addModule(bedEsp = new BedESP());
        this.addModule(sharkESP = new SharkESP());
        this.addModule(invisESP = new InvisESP());

        // world
        this.addModule(antiBot = new AntiBot());
        this.addModule(targetManager = new TargetManager());

        // player
        this.addModule(delayRemover = new DelayRemover());
        this.addModule(autoWeapon = new AutoWeapon());
        this.addModule(autoTool = new AutoTool());

        // combat
        this.addModule(aimAssist = new AimAssist());
        this.addModule(autoClicker = new AutoClicker());
        this.addModule(blockHit = new BlockHit());
        this.addModule(backtrack = new Backtrack());

        // movement
        this.addModule(sprint = new Sprint());

        // other
        this.addModule(modSpoofer = new ModSpoofer());
        this.addModule(pingSpoofer = new PingSpoofer());

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