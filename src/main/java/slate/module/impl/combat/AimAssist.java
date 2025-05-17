package slate.module.impl.combat;

import lombok.Getter;
import slate.module.Module;
import slate.module.impl.combat.aimassist.*;
import slate.module.setting.impl.ModeValue;

public class AimAssist extends Module {
    private final ModeValue mode;

    @Getter
    private final NormalAimAssist normalAimAssist;

    public AimAssist() {
        super("AimAssist", category.combat);
        this.registerSetting(mode = new ModeValue("Mode", this)
                .add(normalAimAssist = new NormalAimAssist("Normal", this))
                .build());
    }

    public void onEnable() {
        mode.enable();
    }

    public void onDisable() {
        mode.disable();
    }
}
