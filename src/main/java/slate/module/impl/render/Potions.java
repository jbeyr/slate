package slate.module.impl.render;

import slate.module.Module;
import slate.module.setting.impl.ButtonSetting;

public class Potions extends Module {
    public ButtonSetting removeBlindness;
    public ButtonSetting removeNausea;

    public Potions() {
        super("Potions", category.render);
        this.registerSetting(removeBlindness = new ButtonSetting("Remove blindness", true));
        this.registerSetting(removeNausea = new ButtonSetting("Remove nausea", true));
    }

}
