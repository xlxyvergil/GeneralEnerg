package com.xlxyvergil.generalenergy.energy;

import com.refinedmods.refinedstorage.energy.BaseEnergyStorage;

/**
 * 自定义的 BaseEnergyStorage，支持动态修改容量
 */
public class CustomBaseEnergyStorage extends BaseEnergyStorage {

    public CustomBaseEnergyStorage(int capacity, int maxReceive, int maxExtract) {
        super(capacity, maxReceive, maxExtract);
    }

    /**
     * 动态设置容量上限
     */
    public void setCapacity(int newCapacity) {
        if (newCapacity < 0) {
            throw new IllegalArgumentException("Capacity cannot be negative: " + newCapacity);
        }
        this.capacity = newCapacity;
        // 如果当前能量超过新容量，裁剪到新容量
        if (this.energy > this.capacity) {
            this.energy = this.capacity;
        }
    }

    /**
     * 直接设置能量值（用于容量减少时裁剪）
     */
    public void setEnergy(int newEnergy) {
        // 验证并裁剪到 0..capacity 范围
        this.energy = Math.max(0, Math.min(newEnergy, this.capacity));
    }
}
