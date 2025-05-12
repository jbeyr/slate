package slate.module.setting;

import com.google.gson.JsonObject;
import slate.Main;
import slate.module.Module;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

public abstract class Setting {
    private final @Nullable String toolTip;
    public String n;
    public Supplier<Boolean> visibleCheck;
    public boolean viewOnly;
    @Setter
    protected @Nullable Module parent = null;

    public Setting(String n, @NotNull Supplier<Boolean> visibleCheck, @Nullable String toolTip) {
        this.n = n;
        this.visibleCheck = visibleCheck;
        this.viewOnly = false;
        this.toolTip = toolTip;
        Main.settingCounter++;
    }

    public Setting(String n, @NotNull Supplier<Boolean> visibleCheck) {
        this(n, visibleCheck, null);
    }

    public String getName() {
        return this.n;
    }

    public @Nullable String getToolTip() {
        return this.toolTip;
    }

    public @Nullable String getPrettyToolTip() {
        return getToolTip();
    }

    public boolean isVisible() {
        final Boolean b = visibleCheck.get();
        return b == null || b;
    }

    public abstract void loadProfile(JsonObject data);
}
