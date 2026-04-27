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
        this.capacity = newCapacity;
    }

    /**
     * 直接设置能量值（用于容量减少时裁剪）
     */
    public void setEnergy(int newEnergy) {
        this.energy = newEnergy;
    }
}
