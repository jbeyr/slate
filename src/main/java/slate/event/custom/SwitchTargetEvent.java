package slate.event.custom;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.Optional;


/**
 * Occurs when TargetManager (slant impl that i wont add) switches target.
 * Can be {@code null} to indicate target was reset, or an actual living entity to indicate a switch.
 */
@EqualsAndHashCode(callSuper = true)
@Cancelable
@Data
@AllArgsConstructor
public class SwitchTargetEvent extends Event {
    private Optional<EntityLivingBase> target;
}

