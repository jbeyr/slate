package slate.utility.profile;

import lombok.Getter;
import slate.module.Module;

public class Profile {
    @Getter
    private final Module module;
    @Getter
    private final int bind;
    private final String profileName;

    public Profile(String profileName, int bind) {
        this.profileName = profileName;
        this.bind = bind;
        this.module = new ProfileModule(this, profileName, bind);
        this.module.ignoreOnSave = true;
    }

    public String getName() {
        return profileName;
    }
}
