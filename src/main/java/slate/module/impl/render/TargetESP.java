package slate.module.impl.render;

import slate.module.Module;
// import slate.module.impl.combat.KillAura;
import slate.module.impl.render.targetvisual.ITargetVisual;
import slate.module.impl.render.targetvisual.targetesp.JelloTargetESP;
import slate.module.impl.render.targetvisual.targetesp.RavenTargetESP;
import slate.module.impl.render.targetvisual.targetesp.VapeTargetESP;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.ModeValue;
import slate.utility.Utils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

public class TargetESP extends Module {
    private static @Nullable EntityLivingBase target = null;
    private final ModeValue mode;
    private final ButtonSetting onlyKillAura;
    private long lastTargetTime = -1;

    public TargetESP() {
        super("TargetESP", category.render);
        this.registerSetting(mode = new ModeValue("Mode", this)
                .add(new RavenTargetESP("Raven", this))
                .add(new JelloTargetESP("Jello", this))
                .add(new VapeTargetESP("Vape", this))
        );
        this.registerSetting(onlyKillAura = new ButtonSetting("Only killAura", true));
    }

    @Override
    public void onEnable() {
        mode.enable();
    }

    @Override
    public void onDisable() {
        mode.disable();

        target = null;
        lastTargetTime = -1;
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) {
            target = null;
            return;
        }


        // if (KillAura.target != null) {
        //     target = KillAura.target;
        //     lastTargetTime = System.currentTimeMillis();
        // }

        if (target != null && lastTargetTime != -1 && (target.isDead || System.currentTimeMillis() - lastTargetTime > 5000 || target.getDistanceSqToEntity(mc.thePlayer) > 20)) {
            target = null;
            lastTargetTime = -1;
        }

        if (onlyKillAura.isToggled()) return;

        // manual target
        if (target != null) {
            if (!Utils.inFov(180, target) || target.getDistanceSqToEntity(mc.thePlayer) > 36) {
                target = null;
            }
        }
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (onlyKillAura.isToggled()) return;

        if (event.target instanceof EntityLivingBase) {
            target = (EntityLivingBase) event.target;
        }
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        if (target != null)
            ((ITargetVisual) mode.getSubModeValues().get((int) mode.getInput())).render(target);
    }
}
