package com.github.wallev.maidsoulkitchen.foundation.utility;

import com.github.wallev.maidsoulkitchen.util.ModUtil;

public enum Mods {
    TLM("touhou_little_maid"),
    MSK("maidsoulkitchen"),

    PATCHOULI("patchouli"),
    CLOTH_CONFIG("cloth_config"),

    JADE("jade"),
    TOP("theoneprobe"),

    SOPHISTICATED_STORAGE("sophisticatedstorage"),

    /*
        Farmer's Delight && Addons
     */
    FD("farmersdelight"),
    MD("miners_delight"),
    MND("mynethersdelight"),
    CD("cuisinedelight"),
    BD("barbequesdelight"),
    YHCD("youkaishomecoming", "[2.2.3,)"),
    YHCD_NEW("youkaishomecoming", "[2.3.13,)"),
    BNCD("brewinandchewin", "[3.0.0,)"),
    FRD("farmersrespite"),

    /*
        Let's DO
     */
    DAPI("doapi"),
    DHB("herbalbrews"),
    DV("vinery"),
    DBP("beachparty"),
    DCL("candlelight"),
    DBK("bakery"),
    DFC("farm_and_charm"),


    SF("simplefarming"),
    FS("fruitstack"),

    /*
        Other
     */
    MS("supplementaries"),
    CP("crockpot"),
    DB("drinkbeer", "[1.4.1,2)"),
    KK("kitchenkarrot"),
    KC("kaleidoscope_cookery", "[1.4.1,2)"),

    TWT("thirst"),

    SS("sereneseasons"),
    ES("eclipticseasons"),

    MC("minecraft") {
        @Override
        public boolean isLoaded() {
            return true;
        }

        @Override
        public boolean versionLoad() {
            return true;
        }
    };

    public final String modId;
    private final String versionRange;

    Mods(String modId) {
        this(modId, "");
    }

    Mods(String modId, String versionRange) {
        this.modId = modId;
        this.versionRange = versionRange;
    }

    public boolean isLoaded() {
        return ModUtil.isInstalled(modId);
    }

    public boolean isInstalled() {
        return isLoaded();
    }

    public boolean versionLoad() {
        return versionRange.isEmpty()
                ? isLoaded()
                : ModUtil.isInstalled(modId, versionRange);
    }

    public static Mods by(String key) {
        return valueOf(key);
    }

    public String getModId() {
        return modId;
    }

    public String getModName() {
        return ModUtil.getModName(modId);
    }

    public String getModActualVersion() {
        return ModUtil.getModVersion(modId);
    }

    public String versionRange() {
        return versionRange;
    }

    public static boolean allLoaded(String... modIds) {
        for (String modId : modIds)
            if (!ModUtil.isInstalled(modId))
                return false;
        return true;
    }

    public static boolean hasLoaded(String... modIds) {
        for (String modId : modIds)
            if (ModUtil.isInstalled(modId))
                return true;
        return false;
    }

    public static boolean hasLoaded(Mods... mods) {
        for (Mods mod : mods)
            if (mod.isLoaded())
                return true;
        return false;
    }

}
