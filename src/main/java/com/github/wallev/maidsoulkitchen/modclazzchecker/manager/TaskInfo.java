package com.github.wallev.maidsoulkitchen.modclazzchecker.manager;

import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import com.github.wallev.maidsoulkitchen.config.subconfig.RegisterConfig;
import com.github.wallev.maidsoulkitchen.foundation.utility.Mods;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Compatibility identities for classes described by {@code mod_task_clazz.json}.
 *
 * <p>This is intentionally separate from the public maid-task catalogue in
 * {@code task.TaskInfo}: it only contains entries that participate in runtime
 * compatibility validation.</p>
 */
public enum TaskInfo {
    NONE("none", Mods.MC, () -> true),

    COMPAT_MELON_FARM("compat_melon", Mods.MC,
            () -> RegisterConfig.COMPAT_MELON_FARM_TASK_ENABLED.get()),
    BERRY_FARM("berries_farm", Mods.MC,
            () -> RegisterConfig.BERRY_FARM_TASK_ENABLED.get()),
    FRUIT_FARM("fruit_farm", Mods.MC,
            () -> RegisterConfig.FRUIT_FARM_TASK_ENABLED.get()),
    FEED_ANIMAL_T("feed_animal_t", Mods.MC,
            () -> RegisterConfig.FEED_ANIMAL_T_TASK_ENABLED.get()),
    SERENESEASONS_FARM("sereneseasons_farm", Mods.SS,
            () -> RegisterConfig.SERENESEASONS_FARM_TASK_ENABLED.get()),
    ECLIPTICSSEASONS_FARM("eclipticseasons_farm", Mods.ES,
            () -> RegisterConfig.ECLIPTICSEASONS_FARM_TASK_ENABLED.get()),

    FURNACE("minecraft_furnace_smelting", Mods.MC,
            () -> RegisterConfig.FURNACE_TASK_ENABLED.get()),
    KC_STEAMER("kaleidoscope_cookery_steamer_steaming", Mods.KC,
            () -> RegisterConfig.KC_STEAMER_TASK_ENABLED.get()),
    FD_COOK_POT("farmersdelight_cooking_pot_cooking", Mods.FD,
            () -> RegisterConfig.FD_COOK_POT_TASK_ENABLED.get()),
    FD_CUTTING_BOARD("farmersdelight_cutting_board_cutting", Mods.FD,
            () -> RegisterConfig.FD_CUTTING_BOARD_TASK_ENABLED.get()),
    FD_SKILLET("fd_skillet", Mods.FD,
            () -> RegisterConfig.FD_SKILLET_TASK_ENABLED.get()),
    CD_CUISINE_SKILLET("cuisinedelight_cuisine_skillet_cuisine", Mods.CD,
            () -> RegisterConfig.CD_CUISINE_SKILLET_TASK_ENABLED.get()),
    BNC_KEY("brewinandchewin_keg_fermenting", Mods.BNCD,
            () -> RegisterConfig.BNC_KEY_TASK_ENABLED.get()),
    BD_BASIN("barbequesdelight_basin_skewering", Mods.BD,
            () -> RegisterConfig.BD_BASIN_TASK_ENABLED.get()),
    BD_GRILL("barbequesdelight_grill_grilling", Mods.BD,
            () -> RegisterConfig.BD_GRILL_TASK_ENABLED.get()),
    YHC_MOKA("youkaishomecoming_moka_pot_moka_pot", Mods.YHCD,
            () -> RegisterConfig.YHC_MOKA_TASK_ENABLED.get()),
    YHC_TEA_KETTLE("youkaishomecoming_kettle_kettle", Mods.YHCD,
            () -> RegisterConfig.YHC_TEA_KETTLE_TASK_ENABLED.get()),
    YHC_DRYING_RACK("youkaishomecoming_drying_rack_drying_rack", Mods.YHCD,
            () -> RegisterConfig.YHC_DRYING_RACK_TASK_ENABLED.get()),
    YHC_FERMENTATION_TANK("youkaishomecoming_fermentation_tank_fermentation", Mods.YHCD_NEW,
            () -> RegisterConfig.YHC_FERMENTATION_TANK_TASK_ENABLED.get()),
    DB_BEER("drinkbeer_beer_barrel_brewing", Mods.DB,
            () -> RegisterConfig.DB_BEER_TASK_ENABLED.get()),

    BERRY_MINECRAFT("berry_minecraft", Mods.MC,
            () -> RegisterConfig.BERRY_FARM_TASK_ENABLED.get()),
    BERRY_COMPAT("berry_compat", Mods.MC,
            () -> RegisterConfig.BERRY_FARM_TASK_ENABLED.get()),
    FRUIT_COMPAT("fruit_compat", Mods.MC,
            () -> RegisterConfig.FRUIT_FARM_TASK_ENABLED.get());

    public static final TaskInfo[] VALUES = values();

    private final String uid;
    private final Mods bindMod;
    private final BooleanSupplier configEnabled;

    TaskInfo(String path, Mods bindMod, BooleanSupplier configEnabled) {
        this.uid = MaidsoulKitchen.MOD_ID + ":" + path;
        this.bindMod = bindMod;
        this.configEnabled = configEnabled;
    }

    public ResourceLocation getUid() {
        return ResourceLocation.parse(uid);
    }

    public String getUidStr() {
        return uid;
    }

    public Mods getBindMod() {
        return bindMod;
    }

    public boolean modVersionLoaded() {
        return bindMod.versionLoad();
    }

    public boolean configEnabled() {
        return configEnabled.getAsBoolean();
    }

    public boolean canLoadWithoutCheckClazz() {
        return this == NONE || modVersionLoaded() && configEnabled();
    }

    public boolean canLoad() {
        return canLoadWithoutCheckClazz()
                && (this == NONE || CompatibilityRegistry.isTaskCompatible(getUidStr()));
    }

    public static TaskInfo by(String key) {
        return valueOf(key);
    }

    @Nullable
    public static TaskInfo by(ResourceLocation uid) {
        for (TaskInfo task : VALUES) {
            if (task.uid.equals(uid.toString())) {
                return task;
            }
        }
        return null;
    }

    @Nullable
    public static TaskInfo byUid(String uid) {
        for (TaskInfo task : VALUES) {
            if (task.getUidStr().equals(uid)) {
                return task;
            }
        }
        return null;
    }
}
