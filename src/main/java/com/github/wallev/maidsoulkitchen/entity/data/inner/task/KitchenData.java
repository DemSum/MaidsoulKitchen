package com.github.wallev.maidsoulkitchen.entity.data.inner.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.task.TaskInfo;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Source: 58ec08ec entity/data/inner/task/cook/v1/KitchenData.java (MIT).
 * Keeps the upstream selected UID + type_data map. CookDataV1 becomes the current component-era CookData.
 * TLM legacy per-appliance keys require a one-way migration boundary; KC's immutable filters are derived views.
 * Replaces all runtime settings reads/writes on beta data keys; those keys remain only for old saves.
 */
public final class KitchenData {
    public static final ResourceLocation IDLE = ResourceLocation.fromNamespaceAndPath("maidsoulkitchen", "idle");
    private static final Codec<Map<ResourceLocation, CookData>> MAP_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, CookData.CODEC);
    public static final Codec<KitchenData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MAP_CODEC.optionalFieldOf("type_data", Map.of()).forGetter(data -> data.cookData),
            ResourceLocation.CODEC.optionalFieldOf("kitchen_name", IDLE).forGetter(KitchenData::getCookName),
            Codec.BOOL.optionalFieldOf("allow_flex_recipes", false).forGetter(data -> data.allowFlexRecipes)
    ).apply(instance, KitchenData::new));

    private final Map<ResourceLocation, CookData> cookData;
    private ResourceLocation cookName;
    // Server-owned configuration snapshot for client menus, never permission supplied by a client.
    private boolean allowFlexRecipes;

    public KitchenData() { this(Map.of(), IDLE); }

    public KitchenData(Map<ResourceLocation, CookData> data, ResourceLocation name) {
        this(data, name, false);
    }

    public KitchenData(Map<ResourceLocation, CookData> data, ResourceLocation name, boolean allowFlexRecipes) {
        this.cookData = new HashMap<>();
        data.forEach((id, settings) -> this.cookData.put(id, copy(settings)));
        this.cookName = name;
        this.allowFlexRecipes = allowFlexRecipes;
    }

    public ResourceLocation getCookName() { return cookName; }
    public void setCookName(ResourceLocation name) { cookName = java.util.Objects.requireNonNull(name); }
    public Map<ResourceLocation, CookData> getCookDatas() { return Map.copyOf(cookData); }
    public CookData getCookData(ResourceLocation id) { return cookData.computeIfAbsent(id, key -> new CookData()); }
    public CookData getCookData() { return getCookData(cookName); }
    public void setCookData(ResourceLocation id, CookData data) { cookData.put(id, copy(data)); }

    /** Copy only once. A legacy key can never overwrite settings already migrated or received from the server. */
    public CookData migrate(ResourceLocation id, Supplier<CookData> legacy) {
        return cookData.computeIfAbsent(id, key -> copy(legacy.get()));
    }

    private static CookData copy(CookData data) {
        return new CookData(data.mode(), data.whitelistRecs(), data.blacklistRecs());
    }

    public static KitchenData get(EntityMaid maid) {
        return maid.getOrCreateData(DataRegister.KITCHEN, new KitchenData());
    }

    public static CookData get(EntityMaid maid, ICookTask<?, ?> task) {
        // KC legacy keys contain filter records rather than CookData. Migrate those once through
        // their existing boundary, then return the same canonical type_data entry to the manager.
        if (task.getUid().equals(TaskInfo.KC_STEAMER.uid)) {
            getSteamerFilter(maid); return get(maid).getCookData(task.getUid());
        }
        if (task.getUid().equals(TaskInfo.KC_STOCKPOT.uid)) {
            getStockpotSettings(maid); return get(maid).getCookData(task.getUid());
        }
        KitchenData kitchen = get(maid);
        boolean migrated = !kitchen.cookData.containsKey(task.getUid());
        CookData data = kitchen.migrate(task.getUid(), () -> maid.getOrCreateData(task.getCookDataKey(), new CookData()));
        if (migrated) sync(maid);
        return data;
    }

    public static RecipeFilterData getSteamerFilter(EntityMaid maid) {
        KitchenData kitchen = get(maid);
        boolean migrated = !kitchen.cookData.containsKey(TaskInfo.KC_STEAMER.uid);
        CookData data = kitchen.migrate(TaskInfo.KC_STEAMER.uid,
                () -> fromFilter(maid.getOrCreateData(DataRegister.KC_STEAMER, RecipeFilterData.DEFAULT)));
        if (migrated) sync(maid);
        return filter(data);
    }

    public static StockpotTaskData getStockpotSettings(EntityMaid maid) {
        KitchenData kitchen = get(maid);
        boolean migrated = !kitchen.cookData.containsKey(TaskInfo.KC_STOCKPOT.uid);
        CookData data = kitchen.migrate(TaskInfo.KC_STOCKPOT.uid,
                () -> fromFilter(maid.getOrCreateData(DataRegister.KC_STOCKPOT, StockpotTaskData.DEFAULT).filter()));
        if (migrated) sync(maid);
        if (!maid.level().isClientSide) kitchen.allowFlexRecipes =
                com.github.wallev.maidsoulkitchen.config.subconfig.TaskConfig.EXPERIMENTAL_FEATURES.get();
        return new StockpotTaskData(filter(data), kitchen.allowFlexRecipes);
    }

    public static void setFilter(EntityMaid maid, ResourceLocation id, RecipeFilterData filter) {
        get(maid).setCookData(id, fromFilter(filter));
        sync(maid);
    }

    public static void sync(EntityMaid maid) {
        if (!maid.level().isClientSide) {
            KitchenData kitchen = get(maid);
            kitchen.allowFlexRecipes = com.github.wallev.maidsoulkitchen.config.subconfig.TaskConfig.EXPERIMENTAL_FEATURES.get();
            maid.setAndSyncData(DataRegister.KITCHEN, kitchen);
        }
    }

    private static CookData fromFilter(RecipeFilterData data) {
        return new CookData(data.mode() == RecipeFilterData.Mode.WHITELIST ? "whitelist" : "blacklist",
                data.whitelist().stream().map(ResourceLocation::toString).toList(),
                data.blacklist().stream().map(ResourceLocation::toString).toList());
    }

    private static RecipeFilterData filter(CookData data) {
        return new RecipeFilterData("whitelist".equals(data.mode())
                ? RecipeFilterData.Mode.WHITELIST : RecipeFilterData.Mode.BLACKLIST,
                ids(data.whitelistRecs()), ids(data.blacklistRecs()));
    }

    private static List<ResourceLocation> ids(List<String> entries) {
        return entries.stream().map(ResourceLocation::tryParse).filter(java.util.Objects::nonNull).toList();
    }
}
