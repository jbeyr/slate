package slate.module.impl.world;

import lombok.Getter;
import slate.module.Module;
import slate.module.setting.impl.ButtonSetting;

public class DelayRemover extends Module {

    @Getter private boolean noHitDelayEnabled;
    @Getter private boolean noMiningDelayEnabled;
    @Getter private boolean noJumpDelayEnabled;

    @Override
    public void guiUpdate() throws Throwable {
        super.guiUpdate();
        noHitDelayEnabled = isEnabled() && noHitDelay.isToggled();
        noMiningDelayEnabled = isEnabled() && noMiningDelay.isToggled();
        noJumpDelayEnabled = isEnabled() && noJumpDelay.isToggled();
    }

    private final ButtonSetting noHitDelay = new ButtonSetting("No Hit Delay", true);
    private final ButtonSetting noMiningDelay = new ButtonSetting("No Mining Delay", true);
    private final ButtonSetting noJumpDelay = new ButtonSetting("No Jump Delay", true);

    public DelayRemover() {
        super("Delay Remover", category.player);
        this.registerSetting(noHitDelay, noMiningDelay, noJumpDelay);
    }
}
