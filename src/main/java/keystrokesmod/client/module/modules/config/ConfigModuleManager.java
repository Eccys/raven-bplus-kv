package keystrokesmod.client.module.modules.config;

import java.util.ArrayList;
import java.util.List;

import keystrokesmod.client.config.Config;
import keystrokesmod.client.main.Raven;
import keystrokesmod.client.module.Module.ModuleCategory;

public class ConfigModuleManager {

    private List<ConfigModule> modules = new ArrayList<ConfigModule>();

    public ConfigModuleManager() {

    }

    public void updater(ArrayList<Config> cfgs) {
        try {
            if (cfgs == null) {
                return;
            }
            modules.removeIf(m -> !hasConfig(cfgs, m.getName()));
            for (Config cfg : cfgs) {
                if (cfg == null || cfg.getName() == null) {
                    continue;
                }
                if (!hasModule(cfg.getName())) {
                    ConfigModule m = new ConfigModule(cfg.getName());
                    modules.add(m);
                }
            }
            for (ConfigModule m : modules) {
                boolean isCurrent = Raven.configManager != null
                        && Raven.configManager.getConfig() != null
                        && m.getName().equals(Raven.configManager.getConfig().getName());
                m.setToggled(isCurrent);
            }
        } catch (NullPointerException omgwoawsocoolandedgy) {
            omgwoawsocoolandedgy.printStackTrace();
        }
        if (Raven.clickGui != null && Raven.clickGui.getCategoryComponent(ModuleCategory.config) != null) {
            Raven.clickGui.getCategoryComponent(ModuleCategory.config).updateModules();
        }
    }

    public List<ConfigModule> getConfigModules() {
        return modules;
    }

    public boolean hasModule(String name) {
        if (name == null) return false;
        return modules.stream().map(ConfigModule::getName).filter(name::equals).findFirst().isPresent();
    }

    public boolean hasConfig(ArrayList<Config> cfgs, String name) {
        if (cfgs == null || name == null) return false;
        return cfgs.stream().filter(c -> c != null && c.getName() != null).map(Config::getName).filter(name::equals).findFirst().isPresent();
    }
}
