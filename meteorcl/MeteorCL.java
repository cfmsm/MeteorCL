package com.github.cfmsm.meteorcl;

import com.github.cfmsm.meteorcl.highlevel.Sweeper;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.*;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;
import org.lwjgl.util.vma.*;
public class MeteorCL {

    public final VkInstance instance;
    public final VkPhysicalDevice physicalDevice;
    public final VkDevice device;
    public final int computeQueueIndex;
    public final VkQueue queue;
    public final long vmaAllocator;
    public Sweeper sweeper = null;
    public static VkBufferCreateInfo bufferInfo(MemoryStack stack, long size, int usage) {
        return VkBufferCreateInfo
                .calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(size)
                .usage(usage)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
    }
    public MeteorCL(String appName) {
        this.instance = createInstance(appName);
        this.physicalDevice = pickPhysicalDevice();
        DeviceResult result = createDevice();

        this.device = result.device;
        try (MemoryStack stack = MemoryStack.stackPush()) {

            VmaVulkanFunctions functions =
                    VmaVulkanFunctions.calloc(stack)
                            .set(instance, device);

            VmaAllocatorCreateInfo allocatorInfo =
                    VmaAllocatorCreateInfo.calloc(stack)
                            .physicalDevice(physicalDevice)
                            .device(device)
                            .instance(instance)
                            .pVulkanFunctions(functions);

            PointerBuffer pAllocator = stack.mallocPointer(1);

            int err = Vma.vmaCreateAllocator(allocatorInfo, pAllocator);

            if (err != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VMA allocator: " + err);
            }

            this.vmaAllocator = pAllocator.get(0);

            this.computeQueueIndex = result.queueIndex;

            PointerBuffer pQueue = stack.mallocPointer(1);

            vkGetDeviceQueue(
                    device,
                    computeQueueIndex,
                    0,
                    pQueue
            );

            this.queue = new VkQueue(pQueue.get(0), device);
        }
    }
    public void bindSweeper(Sweeper s) {
        this.sweeper=s;
    }
   public DataBuffer createStorageBuffer(long size) {
        return new DataBuffer(
                this,
                size,
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT |
                        VK_BUFFER_USAGE_TRANSFER_DST_BIT |
                        VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                        VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        );
    }

    public long createFence() {
        try (MemoryStack stack = stackPush()) {
            VkFenceCreateInfo info = VkFenceCreateInfo
                    .calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);

            LongBuffer pFence = stack.mallocLong(1);

            check(vkCreateFence(device, info, null, pFence));

            return pFence.get(0);
        }
    }

    public void destroyFence(long fence) {
        vkDestroyFence(device, fence, null);
    }

    private VkInstance createInstance(String name) {
        try (MemoryStack stack = stackPush()) {

            VkApplicationInfo appInfo = VkApplicationInfo
                    .calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(stack.UTF8(name))
                    .applicationVersion(1)
                    .pEngineName(stack.UTF8("Meteor Engine"))
                    .engineVersion(1)
                    .apiVersion(VK_API_VERSION_1_0);

            PointerBuffer extensions = stack.mallocPointer(1);
            extensions.flip();

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo
                    .calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(appInfo)
                    .ppEnabledExtensionNames(extensions)
                    .flags(VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR);

            PointerBuffer pInstance = stack.mallocPointer(1);

            check(vkCreateInstance(createInfo, null, pInstance));

            return new VkInstance(
                    pInstance.get(0),
                    createInfo
            );
        }
    }

    private VkPhysicalDevice pickPhysicalDevice() {
        try (MemoryStack stack = stackPush()) {

            IntBuffer count = stack.mallocInt(1);

            check(vkEnumeratePhysicalDevices(
                    instance,
                    count,
                    null
            ));

            if (count.get(0) == 0) {
                throw new RuntimeException(
                        "No Vulkan physical devices found"
                );
            }

            PointerBuffer devices = stack.mallocPointer(count.get(0));

            check(vkEnumeratePhysicalDevices(
                    instance,
                    count,
                    devices
            ));

            return new VkPhysicalDevice(
                    devices.get(0),
                    instance
            );
        }
    }

    private DeviceResult createDevice() {
        try (MemoryStack stack = stackPush()) {

            IntBuffer count = stack.mallocInt(1);

            vkGetPhysicalDeviceQueueFamilyProperties(
                    physicalDevice,
                    count,
                    null
            );

            VkQueueFamilyProperties.Buffer families =
                    VkQueueFamilyProperties.calloc(
                            count.get(0),
                            stack
                    );

            vkGetPhysicalDeviceQueueFamilyProperties(
                    physicalDevice,
                    count,
                    families
            );

            Integer computeIndex = null;

            for (int i = 0; i < families.capacity(); i++) {
                if ((families.get(i).queueFlags() &
                        VK_QUEUE_COMPUTE_BIT) != 0) {

                    computeIndex = i;
                    break;
                }
            }

            if (computeIndex == null) {
                throw new RuntimeException(
                        "No compute queue family found"
                );
            }

            FloatBuffer priorities = stack.floats(1.0f);

            VkDeviceQueueCreateInfo.Buffer queueInfo =
                    VkDeviceQueueCreateInfo
                            .calloc(1, stack)
                            .sType(
                                    VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO
                            )
                            .queueFamilyIndex(computeIndex)
                            .pQueuePriorities(priorities);

            VkDeviceCreateInfo deviceInfo =
                    VkDeviceCreateInfo
                            .calloc(stack)
                            .sType(
                                    VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO
                            )
                            .pQueueCreateInfos(queueInfo);

            PointerBuffer pDevice = stack.mallocPointer(1);

            check(vkCreateDevice(
                    physicalDevice,
                    deviceInfo,
                    null,
                    pDevice
            ));

            VkDevice device = new VkDevice(
                    pDevice.get(0),
                    physicalDevice,
                    deviceInfo
            );

            return new DeviceResult(device, computeIndex);
        }
    }

    public long createCommandPool() {
        try (MemoryStack stack = stackPush()) {

            VkCommandPoolCreateInfo info =
                    VkCommandPoolCreateInfo
                            .calloc(stack)
                            .sType(
                                    VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO
                            )
                            .queueFamilyIndex(computeQueueIndex)
                            .flags(
                                    VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT
                            );

            LongBuffer pPool = stack.mallocLong(1);

            check(vkCreateCommandPool(
                    device,
                    info,
                    null,
                    pPool
            ));

            return pPool.get(0);
        }
    }

    public VkCommandBuffer createCommandBuffer(long pool) {
        try (MemoryStack stack = stackPush()) {

            VkCommandBufferAllocateInfo alloc =
                    VkCommandBufferAllocateInfo
                            .calloc(stack)
                            .sType(
                                    VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO
                            )
                            .commandPool(pool)
                            .level(
                                    VK_COMMAND_BUFFER_LEVEL_PRIMARY
                            )
                            .commandBufferCount(1);

            PointerBuffer pCmd = stack.mallocPointer(1);

            check(vkAllocateCommandBuffers(
                    device,
                    alloc,
                    pCmd
            ));

            return new VkCommandBuffer(
                    pCmd.get(0),
                    device
            );
        }
    }
    public long createDescriptorPool(int sets) {
        try (MemoryStack stack = stackPush()) {

            VkDescriptorPoolSize.Buffer size =
                    VkDescriptorPoolSize
                            .calloc(1, stack)
                            .type(
                                    VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
                            )
                            .descriptorCount(16);

            VkDescriptorPoolCreateInfo info =
                    VkDescriptorPoolCreateInfo
                            .calloc(stack)
                            .sType(
                                    VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO
                            )
                            .maxSets(sets)
                            .pPoolSizes(size);

            LongBuffer pPool =
                    stack.mallocLong(1);

            check(vkCreateDescriptorPool(
                    device,
                    info,
                    null,
                    pPool
            ));

            return pPool.get(0);
        }
    }
    public void resetCommandPool(long pool) {
        vkResetCommandPool(device, pool, 0);
    }
    public long createDescriptorLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer binding =
                    VkDescriptorSetLayoutBinding
                            .calloc(1, stack)
                            .binding(0)
                            .descriptorType(
                                    VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
                            )
                            .descriptorCount(1)
                            .stageFlags(
                                    VK_SHADER_STAGE_COMPUTE_BIT
                            );
            VkDescriptorSetLayoutCreateInfo layoutInfo =
                    VkDescriptorSetLayoutCreateInfo
                            .calloc(stack)
                            .sType(
                                    VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO
                            )
                            .pBindings(binding);
            LongBuffer pLayout =
                    stack.mallocLong(1);

            ComputePipeline.check(vkCreateDescriptorSetLayout(
                    device,
                    layoutInfo,
                    null,
                    pLayout
            ));
            return pLayout.get(0);
        }
    }
    public static void resetCommandBuffer(
            VkCommandBuffer cmd
    ) {
        vkResetCommandBuffer(cmd, 0);
    }

    public void destroy() {
        vkDestroyDevice(device, null);
        vkDestroyInstance(instance, null);
    }

    private static void check(int result) {
        if (result != VK_SUCCESS) {
            throw new RuntimeException(
                    "Vulkan error: " + result
            );
        }
    }
    public long allocateDescriptorSet(long descriptorLayout, long descriptorPool) {
        try (MemoryStack stack = stackPush()) {

            VkDescriptorSetAllocateInfo alloc =
                    VkDescriptorSetAllocateInfo
                            .calloc(stack)
                            .sType(
                                    VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO
                            )
                            .descriptorPool(
                                    descriptorPool
                            )
                            .pSetLayouts(
                                    stack.longs(
                                            descriptorLayout
                                    )
                            );

            LongBuffer pSet =
                    stack.mallocLong(alloc.descriptorSetCount());
            check(vkAllocateDescriptorSets(
                    device,
                    alloc,
                    pSet
            ));

            return pSet.get(0);
        }
    }
    private record DeviceResult(
            VkDevice device,
            int queueIndex
    ) {}
}