package io.github.sykohkilla.notmyspawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

/** Persistent, server-side storage for independent Overworld and Nether respawn slots. */
final class RespawnSlotData implements INBTSerializable<CompoundTag> {
    @Nullable
    private RespawnPoint overworld;
    @Nullable
    private RespawnPoint nether;

    @Nullable
    RespawnPoint get(ResourceKey<Level> currentDimension) {
        return currentDimension.equals(Level.NETHER) ? nether : overworld;
    }

    @Nullable
    RespawnPoint overworld() {
        return overworld;
    }

    @Nullable
    RespawnPoint nether() {
        return nether;
    }

    void set(RespawnPoint point) {
        if (point.dimension().equals(Level.NETHER)) {
            nether = point;
        } else {
            overworld = point;
        }
    }

    void clear(ResourceKey<Level> dimension) {
        if (dimension.equals(Level.NETHER)) {
            nether = null;
        } else {
            overworld = null;
        }
    }

    boolean isEmpty() {
        return overworld == null && nether == null;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag root = new CompoundTag();
        writePoint(root, "overworld", overworld);
        writePoint(root, "nether", nether);
        return root;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag root) {
        overworld = readPoint(root, "overworld");
        nether = readPoint(root, "nether");
    }

    private static void writePoint(CompoundTag root, String key, @Nullable RespawnPoint point) {
        if (point == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", point.dimension().location().toString());
        tag.putInt("x", point.position().getX());
        tag.putInt("y", point.position().getY());
        tag.putInt("z", point.position().getZ());
        tag.putFloat("angle", point.angle());
        tag.putBoolean("forced", point.forced());
        root.put(key, tag);
    }

    @Nullable
    private static RespawnPoint readPoint(CompoundTag root, String key) {
        if (!root.contains(key, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = root.getCompound(key);
        ResourceLocation location = ResourceLocation.tryParse(tag.getString("dimension"));
        if (location == null) {
            return null;
        }
        return new RespawnPoint(
                ResourceKey.create(Registries.DIMENSION, location),
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                tag.getFloat("angle"),
                tag.getBoolean("forced")
        );
    }

    record RespawnPoint(ResourceKey<Level> dimension, BlockPos position, float angle, boolean forced) {
        RespawnPoint {
            position = position.immutable();
        }
    }
}
