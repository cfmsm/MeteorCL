package com.github.cfmsm.meteorcl;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.*;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;

public class DataBuffer implements AutoCloseable {

    public final MeteorCL ctx;

    public final long size;
    public final int usage;
    public final int memoryFlags;

    public final long buffer;
    public final long allocation;

    private ByteBuffer mapped;

    public DataBuffer(
            MeteorCL ctx,
            long size,
            int usage,
            int memoryFlags
    ) {
        this.ctx = ctx;
        this.size = size;
        this.usage = usage;
        this.memoryFlags = memoryFlags;

        try (MemoryStack stack = stackPush()) {

            VkBufferCreateInfo bufferInfo =
                    VkBufferCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                            .size(size)
                            .usage(usage)
                            .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocInfo =
                    VmaAllocationCreateInfo.calloc(stack)
                            .usage(VMA_MEMORY_USAGE_AUTO_PREFER_HOST)
                            .requiredFlags(
                                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                                            VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                            )
                            .flags(VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT);

            LongBuffer pBuffer = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);

            int err = vmaCreateBuffer(
                    ctx.vmaAllocator,
                    bufferInfo,
                    allocInfo,
                    pBuffer,
                    pAllocation,
                    null
            );

            if (err != VK_SUCCESS) {
                throw new RuntimeException("VMA buffer creation failed: " + err);
            }

            this.buffer = pBuffer.get(0);
            this.allocation = pAllocation.get(0);
        }

        this.mapped = map();

        if (ctx.sweeper != null) {
            ctx.sweeper.add(this);
        }
    }

    // ----------------------------
    // Mapping
    // ----------------------------

    public ByteBuffer map() {
        if (mapped != null) return mapped;

        try (MemoryStack stack = stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);

            int err = vmaMapMemory(ctx.vmaAllocator, allocation, pData);
            if (err != VK_SUCCESS) {
                throw new RuntimeException("VMA map failed: " + err);
            }

            mapped = pData.getByteBuffer(0, (int) size);
            return mapped;
        }
    }

    public void unmap() {
        if (mapped == null) return;

        vmaUnmapMemory(ctx.vmaAllocator, allocation);
        mapped = null;
    }

    // ----------------------------
    // Upload / Read
    // ----------------------------

    public void upload(ByteBuffer src, int offset) {
        if (ctx.sweeper != null) {
            ctx.sweeper.touch(this);
        }

        if (offset + src.remaining() > size) {
            throw new RuntimeException("Upload exceeds buffer size");
        }

        ByteBuffer dst = map();

        ByteBuffer dstSlice = dst.duplicate();
        dstSlice.position(offset);
        dstSlice.limit(offset + src.remaining());

        dstSlice.put(src.duplicate());
    }

    public ByteBuffer read(int size, int offset) {

        ByteBuffer src = map();

        ByteBuffer view = src.duplicate();
        view.position(offset);
        view.limit(offset + size);
        return src.slice().order(ByteOrder.nativeOrder());
    }
    public ByteBuffer read() {
        ByteBuffer out = map();
        return out.order(ByteOrder.nativeOrder());
    }
    public ByteBuffer read(int offset) {
        ByteBuffer src = map();

        ByteBuffer view = src.duplicate();
        view.position(offset);
        view.limit(offset + src.remaining());

        return src.slice().order(ByteOrder.nativeOrder());
    }

    // ----------------------------
    // Cleanup
    // ----------------------------

    public void destroy() {
        unmap();
        vmaDestroyBuffer(ctx.vmaAllocator, buffer, allocation);
    }

    @Override
    public void close() {
        destroy();
    }
}