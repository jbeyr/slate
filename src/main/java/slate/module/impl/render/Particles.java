package slate.module.impl.render;

import slate.module.Module;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.DescriptionSetting;
import slate.module.setting.impl.SliderSetting;

import static slate.module.ModuleManager.particles;

public class Particles extends Module {
    private final SliderSetting multiplier;
    private final ButtonSetting alwaysSharpness;
    private final ButtonSetting alwaysCriticals;

    public Particles() {
        super("Particles", category.render);
        this.registerSetting(new DescriptionSetting("modify the particles on attack."));
        this.registerSetting(multiplier = new SliderSetting("Multiplier", 2, 0, 5, 1));
        this.registerSetting(alwaysSharpness = new ButtonSetting("Always sharpness", false));
        this.registerSetting(alwaysCriticals = new ButtonSetting("Always criticals", false));
    }

    public static int criticalsParticleMultiplier(boolean should) {
        if (particles == null || !particles.isEnabled()) return should ? 1 : 0;
        if (particles.multiplier.getInput() == 0) return 0;
        if (!should && !particles.alwaysCriticals.isToggled()) return 0;

        return (int) particles.multiplier.getInput();
    }

    public static int sharpnessParticleMultiplier(boolean should) {
        if (particles == null || !particles.isEnabled()) return should ? 1 : 0;
        if (particles.multiplier.getInput() == 0) return 0;
        if (!should && !particles.alwaysSharpness.isToggled()) return 0;

        return (int) particles.multiplier.getInput();
    }
}
