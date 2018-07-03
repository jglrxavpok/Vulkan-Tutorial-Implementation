package org.jglrxavpok.vulkan

import org.lwjgl.PointerBuffer
import java.nio.LongBuffer

typealias VkPresentModeKHR = Int
typealias VkFormat = Int
typealias VkMemoryPropertiesFlags = Int

typealias VkPointer<T> = LongBuffer

typealias VkDescriptorSetLayout = Long
typealias VkDeviceMemory = Long
typealias VkBuffer = Long
typealias VkFence = Long
typealias VkSemaphore = Long
typealias VkCommandPool = Long
typealias VkFramebuffer = Long
typealias VkShaderModule = Long
typealias VkPipeline = Long
typealias VkRenderPass = Long
typealias VkPipelineLayout = Long
typealias VkSwapchainKHR = Long
typealias VkSurfaceKHR = Long
typealias VkImage = Long
typealias VkImageView = Long

inline operator fun LongBuffer.not(): Long {
    return this[0]
}

inline operator fun PointerBuffer.not(): Long {
    return this[0]
}
