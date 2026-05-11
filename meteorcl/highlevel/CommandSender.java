package com.github.cfmsm.meteorcl.highlevel;

import com.github.cfmsm.meteorcl.*;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.util.xxhash.*;
import java.nio.ByteBuffer;

public class CommandSender {

    private final MeteorCL ctx;
    private final long descriptorPool;
    private final long descriptorLayout;
    private long descriptorSet;
    private long fence;
    private VkCommandBuffer cmd;
    private CommandBatch batch;
    private long commandPool;
    private final java.util.concurrent.ConcurrentHashMap<Long, ComputePipeline> pipelineCache = new java.util.concurrent.ConcurrentHashMap<>();
    public CommandSender(MeteorCL ctx, int sets) {
        this.ctx = ctx;
        descriptorPool = ctx.createDescriptorPool(sets);
        descriptorLayout = ctx.createDescriptorLayout();
        fence = ctx.createFence();
        commandPool = ctx.createCommandPool();
        cmd = ctx.createCommandBuffer(commandPool);
        batch = new CommandBatch(ctx, cmd);
        descriptorSet = ctx.allocateDescriptorSet(descriptorLayout, descriptorPool);
    }
    public void renew() {
        fence = ctx.createFence();
        commandPool = ctx.createCommandPool();
        cmd = ctx.createCommandBuffer(commandPool);
        batch = new CommandBatch(ctx, cmd);
    }
    public Result run(ByteBuffer spirv, DataBuffer input, int x, int y, int z) {
        long key = XXHash.XXH64(spirv.asReadOnlyBuffer().slice(), 0);

        ComputePipeline pipeline = pipelineCache.computeIfAbsent(key,
                _ -> new ComputePipeline(ctx, spirv, descriptorPool, descriptorLayout)
        );
        pipeline.updateDescriptorSet(descriptorSet, input);
        VkCommandBuffer vkCmd = ctx.createCommandBuffer(commandPool);

        CommandBatch batch =
                new CommandBatch(ctx, vkCmd);

        batch.bindPipeline(pipeline);

        batch.bindDescriptor(
                pipeline.pipelineLayout,
                descriptorSet
        );

        batch.dispatch(x, y, z);

        batch.queueSubmit(fence);

        return new ResultAsync(
                ctx,
                input,
                pipeline,
                batch,
                fence
        );
    }
}