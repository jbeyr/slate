package slate.module.impl.combat;

import lombok.Getter;
import slate.module.Module;
import slate.module.impl.combat.aimassist.*;
import slate.module.setting.impl.ModeValue;

public class AimAssist extends Module {
    private final ModeValue mode;

    @Getter
    private final NormalAimAssist normalAimAssist;

    @Getter
    private final SubHitboxAimlock subHitboxAimAssist;

    public AimAssist() {
        super("AimAssist", category.combat);
        this.registerSetting(mode = new ModeValue("Mode", this)
                .add(normalAimAssist = new NormalAimAssist("Normal", this))
                .add(subHitboxAimAssist = new SubHitboxAimlock("Sub Hitbox", this))
                .build());
    }

    public void onEnable() {
        mode.enable();
    }

    public void onDisable() {
        mode.disable();
    }
}
