package com.github.cfmsm.meteorcl;

import static org.lwjgl.vulkan.VK10.vkAllocateMemory;

class MemoryBlock {
    final long memory;
    final long size;
    long offset;

    MemoryBlock(long memory, long size) {
        this.memory = memory;
        this.size = size;
        this.offset = 0;
    }

    long allocate(long size, long alignment) {
        long aligned = (offset + alignment - 1) & ~(alignment - 1);

        if (aligned + size > this.size) {
            return -1;
        }

        offset = aligned + size;
        return aligned;
    }
}