package com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.skillet;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.AbstractCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.NormalCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity;
import vectorwing.farmersdelight.common.registry.ModItems;
/** No 1.20 FD skillet task existed. Same task/Be/NormalRule/catalog boundary as upstream drying
 * rack. Keeps existing UID/settings and native device; deletes beta bespoke Make and fallback. */
@TaskClassAnalyzer(TaskInfo.FD_SKILLET)
public class TaskFdSkillet extends ICookTask<SkilletBlockEntity, CampfireCookingRecipe> {
    @Override protected AbstractCookRule<SkilletBlockEntity, CampfireCookingRecipe> createCookRule() { return NormalCookRule.getInstance(); }
    @Override protected RecSerializerManager<CampfireCookingRecipe> createRecSerializerManager() { return SkilletRecSerializerManager.getInstance(); }
    @Override protected CookBeBase<SkilletBlockEntity> createCookBe(EntityMaid maid) { return new SkilletBe(maid); }
    @Override public ResourceLocation getUid() { return com.github.wallev.maidsoulkitchen.task.TaskInfo.FD_SKILLET.uid; }
    @Override public ItemStack getIcon() { return ModItems.SKILLET.get().getDefaultInstance(); }
    @Override public com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey<com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData> getCookDataKey() { return com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister.FD_SKILLET; }
}
