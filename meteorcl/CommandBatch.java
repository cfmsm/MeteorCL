package com.github.cfmsm.meteorcl;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public record CommandBatch(MeteorCL ctx, VkCommandBuffer cmd) implements AutoCloseable {
    public CommandBatch(
            MeteorCL ctx,
            VkCommandBuffer cmd
    ) {
        this.ctx = ctx;
        this.cmd = cmd;
        try (MemoryStack stack = stackPush()) {

            VkCommandBufferBeginInfo begin =
                    VkCommandBufferBeginInfo
                            .calloc(stack)
                            .sType(
                                    VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO
                            );

            check(vkBeginCommandBuffer(
                    cmd,
                    begin
            ));
        }
        if (this.ctx.sweeper!=null) this.ctx.sweeper.add(this);
    }

    public void bindPipeline(
            ComputePipeline pipeline
    ) {

        vkCmdBindPipeline(
                cmd,
                VK_PIPELINE_BIND_POINT_COMPUTE,
                pipeline.pipeline
        );
    }

    public void bindDescriptor(
            long layout,
            long set
    ) {
        try (MemoryStack stack = stackPush()) {

            vkCmdBindDescriptorSets(
                    cmd,
                    VK_PIPELINE_BIND_POINT_COMPUTE,
                    layout,
                    0,
                    stack.longs(set),
                    null
            );
        }
    }

    public void dispatch(
            int x,
            int y,
            int z
    ) {
        vkCmdDispatch(cmd, x, y, z);
    }

    public void end() {
        check(vkEndCommandBuffer(cmd));
    }

    public void queueSubmit(long fence) {
        try (MemoryStack stack = stackPush()) {

            VkSubmitInfo.Buffer submit =
                    VkSubmitInfo
                            .calloc(1, stack)
                            .sType(
                                    VK_STRUCTURE_TYPE_SUBMIT_INFO
                            )
                            .pCommandBuffers(
                                    stack.pointers(
                                            cmd.address()
                                    )
                            );

            check(vkQueueSubmit(
                    ctx.queue,
                    submit,
                    fence
            ));
        }
    }

    public void waitFence(
            long fence,
            long timeout
    ) {
        check(vkWaitForFences(
                ctx.device,
                fence,
                true,
                timeout
        ));
    }

    public void pipelineBarrier() {
        try (MemoryStack stack = stackPush()) {

            VkMemoryBarrier.Buffer barrier =
                    VkMemoryBarrier
                            .calloc(1, stack)
                            .sType(
                                    VK_STRUCTURE_TYPE_MEMORY_BARRIER
                            )
                            .srcAccessMask(
                                    VK_ACCESS_SHADER_WRITE_BIT
                            )
                            .dstAccessMask(
                                    VK_ACCESS_SHADER_READ_BIT |
                                            VK_ACCESS_SHADER_WRITE_BIT
                            );

            vkCmdPipelineBarrier(
                    cmd,
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    0,
                    barrier,
                    null,
                    null
            );
        }
    }

    private static void check(int result) {
        if (result != VK_SUCCESS) {
            throw new RuntimeException(
                    "Vulkan error: " + result
            );
        }
    }

    @Override
    public void close() {
        end();
    }
}