package slate;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.jetbrains.annotations.NotNull;
import slate.clickgui.ClickGui;
import slate.module.Module;
import slate.module.ModuleManager;
import slate.utility.Reflection;
import slate.utility.Utils;
import slate.utility.slate.PacketManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Mod(modid = Main.MODID, version = Main.VERSION)
public class Main
{
    public static final String MODID = "slate";
    public static final String VERSION = "0.1";

    public static Minecraft mc = Minecraft.getMinecraft();
    private static final ScheduledExecutorService ex = Executors.newScheduledThreadPool(4);

    @Getter
    public static ModuleManager moduleManager;
    public static ClickGui clickGui;

    public static int moduleCounter;

    public static int settingCounter;
    public Main() {
        moduleManager = new ModuleManager();
    }

    @EventHandler
    public void init(FMLInitializationEvent ignored) {
        Runtime.getRuntime().addShutdownHook(new Thread(ex::shutdown));
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new PacketManager());
        // MinecraftForge.EVENT_BUS.register(new DebugInfoRenderer());
        // MinecraftForge.EVENT_BUS.register(new CPSCalculator());
        // MinecraftForge.EVENT_BUS.register(new Ping());
        // MinecraftForge.EVENT_BUS.register(badPacketsHandler = new BadPacketsHandler());
        // MinecraftForge.EVENT_BUS.register(progressManager = new ProgressManager());
        Reflection.getFields();
        Reflection.getMethods();
        moduleManager.register();
        // scriptManager = new ScriptManager();
        clickGui = new ClickGui();
        // profileManager = new ProfileManager();
        // profileManager.loadProfiles();
        // profileManager.loadProfile();
        Reflection.setKeyBindings();
        // MinecraftForge.EVENT_BUS.register(ModuleManager.rotationHandler);
        // MinecraftForge.EVENT_BUS.register(ModuleManager.slotHandler);
        // MinecraftForge.EVENT_BUS.register(ModuleManager.dynamicManager);
        // MinecraftForge.EVENT_BUS.register(new MoveableManager());
        // MinecraftForge.EVENT_BUS.register(profileManager);
    }

    @SubscribeEvent
    public void onTick(@NotNull TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END) {
            try {
                if (Utils.nullCheckPasses()) {
                    if (Reflection.sendMessage) {
                        Utils.sendMessage("&cThere was an error, relaunch the game.");
                        Reflection.sendMessage = false;
                    }
                    for (Module module : getModuleManager().getModules()) {
                        if (mc.currentScreen instanceof ClickGui) {
                            module.guiUpdate();
                        }

                        if (module.isEnabled()) {
                            module.onUpdate();
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.@NotNull RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            try {
                if (Utils.nullCheckPasses()) {
                    for (Module module : getModuleManager().getModules()) {
                        if (mc.currentScreen == null && module.canBeEnabled()) {
                            module.keybind();
                        }
                    }
                    // synchronized (Main.profileManager.profiles) {
                    //    for (Profile profile : Raven.profileManager.profiles) {
                    //        if (mc.currentScreen == null) {
                    //            profile.getModule().keybind();
                    //        }
                    //    }
                    // }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static ScheduledExecutorService getExecutor() {
        return ex;
    }
}
