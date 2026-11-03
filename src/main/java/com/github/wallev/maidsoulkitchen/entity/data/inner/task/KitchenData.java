package com.github.wallev.maidsoulkitchen.entity.data.inner.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.task.TaskInfo;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Source: 58ec08ec entity/data/inner/task/cook/v1/KitchenData.java (MIT).
 * Keeps the upstream selected UID + type_data map. CookDataV1 becomes the current component-era CookData.
 * Known neo settings move once during entity loading; runtime readers use only this canonical source.
 * KC's immutable filters are derived views, not independently persisted work settings.
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
    public Map<ResourceLocation, CookData> getCookDatas() {
        Map<ResourceLocation, CookData> snapshot = new HashMap<>();
        cookData.forEach((id, data) -> snapshot.put(id, copy(data)));
        return Map.copyOf(snapshot);
    }
    public CookData getCookData(ResourceLocation id) {
        CookData data = cookData.get(id);
        return data == null ? new CookData() : data;
    }
    public CookData getCookData() { return getCookData(cookName); }
    public void setCookData(ResourceLocation id, CookData data) { cookData.put(id, copy(data)); }

    private static CookData copy(CookData data) {
        return new CookData(data.mode(), data.whitelistRecs(), data.blacklistRecs());
    }

    public static KitchenData get(EntityMaid maid) {
        KitchenData data = maid.getData(DataRegister.KITCHEN);
        return data == null ? new KitchenData() : data;
    }

    public static CookData get(EntityMaid maid, ICookTask<?, ?> task) {
        return get(maid).getCookData(task.getUid());
    }

    public static RecipeFilterData getSteamerFilter(EntityMaid maid) {
        return filter(get(maid).getCookData(TaskInfo.KC_STEAMER.uid));
    }

    public static StockpotTaskData getStockpotSettings(EntityMaid maid) {
        KitchenData kitchen = get(maid);
        boolean allowFlex = maid.level().isClientSide ? kitchen.allowFlexRecipes
                : com.github.wallev.maidsoulkitchen.config.subconfig.TaskConfig.EXPERIMENTAL_FEATURES.get();
        return new StockpotTaskData(filter(kitchen.getCookData(TaskInfo.KC_STOCKPOT.uid)), allowFlex);
    }

    public static void setFilter(EntityMaid maid, ResourceLocation id, RecipeFilterData filter) {
        edit(maid, id, data -> {
            data.setMode(filter.mode() == RecipeFilterData.Mode.WHITELIST ? "whitelist" : "blacklist");
            data.setWhitelistRecs(filter.whitelist().stream().map(ResourceLocation::toString).toList());
            data.setBlacklistRecs(filter.blacklist().stream().map(ResourceLocation::toString).toList());
        });
    }

    public static void edit(EntityMaid maid, ResourceLocation id, Consumer<CookData> change) {
        KitchenData kitchen = get(maid);
        CookData data = copy(kitchen.getCookData(id));
        change.accept(data);
        kitchen.setCookData(id, data);
        sync(maid, kitchen);
    }

    public static void sync(EntityMaid maid) {
        sync(maid, get(maid));
    }

    public static void sync(EntityMaid maid, KitchenData kitchen) {
        if (!maid.level().isClientSide) {
            kitchen.allowFlexRecipes = com.github.wallev.maidsoulkitchen.config.subconfig.TaskConfig.EXPERIMENTAL_FEATURES.get();
            maid.setAndSyncData(DataRegister.KITCHEN, kitchen);
        }
    }

    /** Before TLM decodes its task map: keep bad MSK work data from rejecting the entire maid.
     * Only work settings are touched. Inventory, components, ownership and other mods stay native. */
    public static boolean prepareSavedData(CompoundTag entityTag) {
        CompoundTag tasks = entityTag.getCompound("MaidTaskDataMaps");
        String kitchenKey = TaskInfo.COOK.uid.toString();
        CompoundTag raw = tasks.getCompound(kitchenKey);
        CompoundTag entries = raw.getCompound("type_data");
        KitchenData kitchen = new KitchenData();
        java.util.Set<ResourceLocation> validEntries = new java.util.HashSet<>();
        for (String key : entries.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                CookData parsed = CookData.CODEC.parse(NbtOps.INSTANCE, entries.get(key)).result().orElse(null);
                if (parsed != null) validEntries.add(id);
                kitchen.setCookData(id, parsed == null ? blockedSettings() : parsed);
            }
        }
        ResourceLocation savedTask = ResourceLocation.tryParse(entityTag.getString("MaidTask"));
        boolean legacyWork = java.util.EnumSet.range(TaskInfo.FURNACE, TaskInfo.FERMENTATION_BARREL)
                .stream().anyMatch(identity -> identity.uid.equals(savedTask));
        String selectedName = raw.getString("kitchen_name");
        ResourceLocation selection = selectedName.isBlank() ? null : ResourceLocation.tryParse(selectedName);
        kitchen.setCookName(selection != null ? selection : legacyWork && !raw.contains("kitchen_name") ? savedTask : IDLE);
        boolean stopLegacyWork = false;
        for (TaskInfo identity : java.util.EnumSet.range(TaskInfo.FURNACE, TaskInfo.FERMENTATION_BARREL)) {
            String key = identity.uid.toString();
            if (!tasks.contains(key)) continue;
            // Existing canonical entries, including a deliberate empty whitelist, always win.
            if (!validEntries.contains(identity.uid)) {
                CookData old = null;
                if (isCookUid(identity.uid)) {
                    if (identity == TaskInfo.KC_STEAMER || identity == TaskInfo.KC_STOCKPOT) {
                        Tag filterTag = identity == TaskInfo.KC_STOCKPOT ? tasks.getCompound(key).get("filter") : tasks.get(key);
                        old = RecipeFilterData.CODEC.parse(NbtOps.INSTANCE, filterTag == null ? new CompoundTag() : filterTag)
                                .result().map(KitchenData::fromFilter).orElse(null);
                    } else old = CookData.CODEC.parse(NbtOps.INSTANCE, tasks.get(key)).result().orElse(null);
                }
                kitchen.setCookData(identity.uid, old == null ? blockedSettings() : old);
                if (old != null) validEntries.add(identity.uid);
                if (old == null && legacyWork && identity.uid.equals(savedTask)
                        && identity.uid.equals(kitchen.cookName)) stopLegacyWork = true;
            }
            tasks.remove(key);
        }
        if (raw.contains("type_data") && !raw.contains("type_data", Tag.TAG_COMPOUND)
                && !validEntries.contains(kitchen.cookName)
                && !kitchen.cookName.equals(IDLE)) kitchen.setCookData(kitchen.cookName, blockedSettings());
        if (tasks.contains(kitchenKey) || legacyWork || !kitchen.cookData.isEmpty()) {
            tasks.put(kitchenKey, CODEC.encodeStart(NbtOps.INSTANCE, kitchen).getOrThrow());
            if (legacyWork && !stopLegacyWork) entityTag.putString("MaidTask", kitchenKey);
        }
        stopLegacyWork |= protectFarmData(tasks, entityTag, DataRegister.BERRY_FARM, BerryData.CODEC, new BerryData(List.of()));
        stopLegacyWork |= protectFarmData(tasks, entityTag, DataRegister.FRUIT_FARM, FruitData.CODEC, new FruitData(3, List.of()));
        entityTag.put("MaidTaskDataMaps", tasks);
        if (stopLegacyWork) entityTag.putString("MaidTask", com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager.getIdleTask().getUid().toString());
        return stopLegacyWork;
    }

    private static <T> boolean protectFarmData(CompoundTag tasks, CompoundTag entityTag,
            com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey<T> key, Codec<T> codec, T empty) {
        String id = key.getKey().toString();
        if (!tasks.contains(id) || codec.parse(NbtOps.INSTANCE, tasks.get(id)).result().isPresent()) return false;
        boolean oldMap = tasks.getCompound(id).contains("Rules", Tag.TAG_COMPOUND);
        tasks.put(id, codec.encodeStart(NbtOps.INSTANCE, empty).getOrThrow());
        return oldMap && entityTag.getString("MaidTask").equals(id);
    }

    private static CookData blockedSettings() { return new CookData("whitelist", List.of(), List.of()); }

    private static boolean isCookUid(ResourceLocation uid) {
        if (uid == null) return false;
        return java.util.EnumSet.range(TaskInfo.FURNACE, TaskInfo.FERMENTATION_BARREL).stream()
                .anyMatch(identity -> identity.uid.equals(uid)
                        && java.util.Arrays.stream(com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.VALUES)
                        .anyMatch(info -> info.name().equals(identity.name())));
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
