package slate.module.impl.render;


import lombok.Getter;
import slate.event.WorldChangeEvent;
import slate.module.Module;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.SliderSetting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.client.event.FOVUpdateEvent;


public class CustomFOV extends Module {

    private float previousFov;
    @Getter private float activeFov;
    @Getter private boolean useStaticFov;

    private final ButtonSetting useAlternateFovButton = new ButtonSetting("Use alternate FOV", false);
    private final SliderSetting setFOV = new SliderSetting("Alternate FOV", 70, 1, 179, 1, useAlternateFovButton::isToggled);
    private final ButtonSetting useStaticFovButton = new ButtonSetting("Static FOV", false);

    public CustomFOV() {
        super("CustomFOV", category.render);
        this.registerSetting(useAlternateFovButton, setFOV, useStaticFovButton);
    }

    @Override
    public void onEnable() {
        previousFov = mc.gameSettings.fovSetting;
        if(useAlternateFovButton.isToggled()) mc.gameSettings.fovSetting = (float) setFOV.getInput();
    }

    @Override
    public void guiUpdate() throws Throwable {
        super.guiUpdate();
        useStaticFov = isEnabled() && useStaticFovButton.isToggled();
        activeFov = isEnabled() && useAlternateFovButton.isToggled() ? (float) setFOV.getInput() : previousFov;
    }

    @Override
    public void onDisable() {
        mc.gameSettings.fovSetting = previousFov;
    }

    @SubscribeEvent
    public void onWorldChangeEvent(WorldChangeEvent event) {
        if(isEnabled() && useAlternateFovButton.isToggled()) mc.gameSettings.fovSetting = (float) setFOV.getInput();
    }

    @SubscribeEvent
    public void onFovUpdateEvent(FOVUpdateEvent event) {
        if(isEnabled() && useAlternateFovButton.isToggled()) mc.gameSettings.fovSetting = (float) setFOV.getInput();
    }
}
