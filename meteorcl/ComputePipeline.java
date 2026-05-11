package com.github.cfmsm.meteorcl;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class ComputePipeline implements AutoCloseable {

    public final MeteorCL ctx;

    public final long descriptorLayout;
    public final long pipelineLayout;

    public final long pipeline;

    public final long descriptorPool;
    private static final java.util.concurrent.ConcurrentHashMap<ByteBuffer, Long> shaderCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Long, Long> layoutCache = new java.util.concurrent.ConcurrentHashMap<>();
    public ComputePipeline(
            MeteorCL ctx,
            ByteBuffer spirv,
            long descriptorPool,
            long descriptorLayout
    ){
        this.descriptorPool=descriptorPool;
        this.descriptorLayout=descriptorLayout;
        this.ctx = ctx;
        ByteBuffer key = spirv.asReadOnlyBuffer();
        long shaderModule = shaderCache.computeIfAbsent(key, this::createShaderModule);

        try (MemoryStack stack = stackPush()) {
            VkPipelineLayoutCreateInfo pipelineLayoutInfo =
                    VkPipelineLayoutCreateInfo
                            .calloc(stack)
                            .sType(
                                    VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO
                            )
                            .pSetLayouts(
                                    stack.longs(
                                            descriptorLayout
                                    )
                            );
            LongBuffer pPipelineLayout =
                    stack.mallocLong(1);
            check(vkCreatePipelineLayout(
                    ctx.device,
                    pipelineLayoutInfo,
                    null,
                    pPipelineLayout
            ));
            pipelineLayout = pPipelineLayout.get(0);

            VkPipelineShaderStageCreateInfo stage =
                    VkPipelineShaderStageCreateInfo
                            .calloc(stack)
                            .sType(
                                    VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO
                            )
                            .stage(
                                    VK_SHADER_STAGE_COMPUTE_BIT
                            )
                            .module(shaderModule)
                            .pName(
                                    stack.UTF8("main")
                            );

            VkComputePipelineCreateInfo.Buffer pipelineInfo =
                    VkComputePipelineCreateInfo
                            .calloc(1, stack)
                            .sType(
                                    VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO
                            )
                            .stage(stage)
                            .layout(pipelineLayout);

            LongBuffer pPipeline =
                    stack.mallocLong(1);

            check(vkCreateComputePipelines(
                    ctx.device,
                    VK_NULL_HANDLE,
                    pipelineInfo,
                    null,
                    pPipeline
            ));

            pipeline = pPipeline.get(0);
        }

        vkDestroyShaderModule(
                ctx.device,
                shaderModule,
                null
        );
        if (this.ctx.sweeper!=null) this.ctx.sweeper.add(this);
    }
    private long createShaderModule(
            ByteBuffer spirv
    ) {
        try (MemoryStack stack = stackPush()) {

            VkShaderModuleCreateInfo info =
                    VkShaderModuleCreateInfo
                            .calloc(stack)
                            .sType(
                                    VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO
                            )
                            .pCode(spirv);

            LongBuffer pModule =
                    stack.mallocLong(1);

            check(vkCreateShaderModule(
                    ctx.device,
                    info,
                    null,
                    pModule
            ));

            return pModule.get(0);
        }
    }

    public void updateDescriptorSet(
            long descriptorSet,
            DataBuffer buffer
    ) {
        try (MemoryStack stack = stackPush()) {

            VkDescriptorBufferInfo.Buffer info =
                    VkDescriptorBufferInfo
                            .calloc(1, stack)
                            .buffer(buffer.buffer)
                            .offset(0)
                            .range(buffer.size);

            VkWriteDescriptorSet.Buffer write =
                    VkWriteDescriptorSet
                            .calloc(1, stack)
                            .sType(
                                    VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET
                            )
                            .dstSet(descriptorSet)
                            .dstBinding(0)
                            .descriptorType(
                                    VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
                            )
                            .descriptorCount(1)
                            .pBufferInfo(info);

            vkUpdateDescriptorSets(
                    ctx.device,
                    write,
                    null
            );
        }
    }

    public void destroy() {
        vkDestroyDescriptorPool(
                ctx.device,
                descriptorPool,
                null
        );

        vkDestroyPipeline(
                ctx.device,
                pipeline,
                null
        );

        vkDestroyPipelineLayout(
                ctx.device,
                pipelineLayout,
                null
        );

        vkDestroyDescriptorSetLayout(
                ctx.device,
                descriptorLayout,
                null
        );
    }

    public static void check(int result) {
        if (result != VK_SUCCESS) {
            throw new RuntimeException(
                    "Vulkan error: " + result
            );
        }
    }

    @Override
    public void close() {
        destroy();
    }
}