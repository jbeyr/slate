package slate.event.custom;

import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.eventhandler.Event;

@Getter
public class AutoclickerAttackEvent extends Event {

    private final Entity attacked;
    public AutoclickerAttackEvent(Entity attacked) {
        this.attacked = attacked;
    }
}
