/*
 * Historical steamer-only search wrapper retained for regression reference.
 * It is intentionally comment-only: runtime steamer movement now calls the
 * shared CookPathSearch directly, while its multi-layer candidate predicate
 * and heat-source floor anchoring remain in MaidSteamerMoveTask.
 *
 * package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;
 *
 * import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
 * import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookPathSearch;
 * import net.minecraft.core.BlockPos;
 * import net.minecraft.server.level.ServerLevel;
 *
 * import java.util.Optional;
 * import java.util.function.Predicate;
 *
 * final class SteamerApproachSearch {
 *     private SteamerApproachSearch() {
 *     }
 *
 *     static Optional<BlockPos> find(
 *             ServerLevel level,
 *             EntityMaid maid,
 *             Predicate<BlockPos> isWantedWalkPosition
 *     ) {
 *         BlockPos center = maid.hasRestriction()
 *                 ? maid.getRestrictCenter() : maid.blockPosition();
 *         return CookPathSearch.find(
 *                 level, maid, center, maid.searchRadius(), isWantedWalkPosition);
 *     }
 * }
 */
